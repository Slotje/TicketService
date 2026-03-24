#!/usr/bin/env node
/**
 * Static Security Audit for TicketService Frontend
 *
 * Scans all .html and .ts source files for dangerous patterns:
 *   - [innerHTML] bindings
 *   - bypassSecurityTrust* calls
 *   - eval() / new Function()
 *   - document.cookie access
 *   - window.location assignments
 *   - Dangerous DOM manipulation (insertAdjacentHTML, document.write, outerHTML)
 *   - Inline event handlers in templates
 *   - External form actions
 *
 * Run: node security-audit.js
 * Exit code: 0 = all clear, 1 = findings detected
 */

const fs = require('fs');
const path = require('path');

const APP_ROOT = path.join(__dirname, 'src', 'app');

function getAllFiles(dirPath, ext, results = []) {
  if (!fs.existsSync(dirPath)) return results;
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(dirPath, entry.name);
    if (entry.isDirectory() && entry.name !== 'node_modules' && entry.name !== 'dist') {
      getAllFiles(full, ext, results);
    } else if (entry.isFile() && entry.name.endsWith(ext)) {
      results.push(full);
    }
  }
  return results;
}

const htmlFiles = getAllFiles(APP_ROOT, '.html');
const tsFiles = getAllFiles(APP_ROOT, '.ts').filter(f => !f.endsWith('.spec.ts'));

let totalFindings = 0;
let passed = 0;

function audit(name, files, check) {
  const findings = [];
  for (const file of files) {
    const content = fs.readFileSync(file, 'utf-8');
    const result = check(content, file);
    if (result) findings.push(...(Array.isArray(result) ? result : [result]));
  }
  if (findings.length > 0) {
    console.log(`\x1b[31m FAIL \x1b[0m ${name} (${findings.length} finding(s))`);
    findings.forEach(f => console.log(`       ${f}`));
    totalFindings += findings.length;
  } else {
    console.log(`\x1b[32m PASS \x1b[0m ${name}`);
    passed++;
  }
}

console.log('\n=== Frontend Security Audit ===\n');
console.log(`Scanning ${htmlFiles.length} HTML files and ${tsFiles.length} TS files...\n`);

// 1. innerHTML bindings
audit('[innerHTML] bindings', htmlFiles, (content, file) => {
  return /\[innerHTML\]/i.test(content) ? path.relative(APP_ROOT, file) : null;
});

// 2. bypassSecurityTrust* calls
const trustMethods = ['bypassSecurityTrustHtml', 'bypassSecurityTrustUrl',
  'bypassSecurityTrustResourceUrl', 'bypassSecurityTrustStyle', 'bypassSecurityTrustScript'];
audit('bypassSecurityTrust* calls', tsFiles, (content, file) => {
  const found = trustMethods.filter(m => content.includes(m));
  return found.length > 0 ? `${path.relative(APP_ROOT, file)}: ${found.join(', ')}` : null;
});

// 3. eval() / new Function()
audit('eval() or new Function()', tsFiles, (content, file) => {
  return (/\beval\s*\(/.test(content) || /new\s+Function\s*\(/.test(content))
    ? path.relative(APP_ROOT, file) : null;
});

// 4. document.cookie
audit('document.cookie access', tsFiles, (content, file) => {
  return /document\.cookie/.test(content) ? path.relative(APP_ROOT, file) : null;
});

// 5. window.location assignment
audit('window.location assignment', tsFiles, (content, file) => {
  return /window\.location\s*(\.\s*href\s*)?\=/.test(content)
    ? path.relative(APP_ROOT, file) : null;
});

// 6. Dangerous DOM manipulation
const domPatterns = [
  { re: /\.insertAdjacentHTML\s*\(/, name: 'insertAdjacentHTML' },
  { re: /\.outerHTML\s*=/, name: 'outerHTML assignment' },
  { re: /document\.write\s*\(/, name: 'document.write' },
  { re: /document\.writeln\s*\(/, name: 'document.writeln' }
];
audit('Dangerous DOM manipulation', tsFiles, (content, file) => {
  const found = domPatterns.filter(p => p.re.test(content)).map(p => p.name);
  return found.length > 0 ? `${path.relative(APP_ROOT, file)}: ${found.join(', ')}` : null;
});

// 7. Inline event handlers in HTML
const inlineHandlers = /\s(onclick|onsubmit|onchange|onkeyup|onkeydown|onfocus|onblur|onmouseover|onerror|onload)\s*=/i;
audit('Inline event handlers in templates', htmlFiles, (content, file) => {
  return inlineHandlers.test(content) ? path.relative(APP_ROOT, file) : null;
});

// 8. External form actions
audit('External form action URLs', htmlFiles, (content, file) => {
  const match = content.match(/<form[^>]*action\s*=\s*["'](https?:\/\/[^"']+)["'][^>]*>/i);
  return match ? `${path.relative(APP_ROOT, file)}: action="${match[1]}"` : null;
});

// 9. HttpClient usage (should use HttpClient, not raw fetch/XHR)
const servicesDir = path.join(APP_ROOT, 'services');
const serviceFiles = getAllFiles(servicesDir, '.ts').filter(f => !f.endsWith('.spec.ts'));
audit('Raw fetch()/XMLHttpRequest in services', serviceFiles, (content, file) => {
  const issues = [];
  if (/\bfetch\s*\(/.test(content)) issues.push('fetch()');
  if (/XMLHttpRequest/.test(content)) issues.push('XMLHttpRequest');
  return issues.length > 0 ? `${path.relative(APP_ROOT, file)}: ${issues.join(', ')}` : null;
});

// Summary
console.log(`\n=== Results: ${passed} passed, ${totalFindings} finding(s) ===\n`);
process.exit(totalFindings > 0 ? 1 : 0);
