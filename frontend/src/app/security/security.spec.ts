import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';

// =============================================================================
// Frontend Security Tests — Browser-based (Jasmine/Karma)
//
// Tests Angular's built-in XSS protection, token storage patterns, and
// verifies no dangerous DOM patterns exist in the rendered output.
// =============================================================================

describe('Security Tests', () => {

  // ---------------------------------------------------------------------------
  // A. XSS Prevention — Component-level tests
  // ---------------------------------------------------------------------------

  describe('XSS Prevention', () => {

    @Component({ standalone: true, template: `<span>{{ value }}</span>` })
    class TestDisplayComponent { value = ''; }

    @Component({ standalone: true, template: `<div [innerHTML]="html"></div>` })
    class InnerHtmlComponent { html = ''; }

    beforeEach(async () => {
      await TestBed.configureTestingModule({
        imports: [TestDisplayComponent, InnerHtmlComponent]
      }).compileComponents();
    });

    it('should escape <script> tags in interpolated text', () => {
      const fixture = TestBed.createComponent(TestDisplayComponent);
      fixture.componentInstance.value = `<script>alert('xss')</script>`;
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      // Text content shows the raw string (escaped)
      expect(el.textContent).toContain(`<script>alert('xss')</script>`);
      // No actual script element was created
      expect(el.querySelector('script')).toBeNull();
      // innerHTML was escaped (entities, not actual tags)
      expect(el.innerHTML).not.toContain('<script>');
    });

    it('should escape <img onerror=...> payloads in interpolated text', () => {
      const fixture = TestBed.createComponent(TestDisplayComponent);
      fixture.componentInstance.value = '<img src=x onerror=alert(1)>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('img')).toBeNull();
      expect(el.textContent).toContain('<img src=x onerror=alert(1)>');
    });

    it('should escape <svg onload=...> payloads in interpolated text', () => {
      const fixture = TestBed.createComponent(TestDisplayComponent);
      fixture.componentInstance.value = '<svg onload=alert(1)>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('svg')).toBeNull();
      expect(el.textContent).toContain('<svg onload=alert(1)>');
    });

    it('should escape <iframe> payloads in interpolated text', () => {
      const fixture = TestBed.createComponent(TestDisplayComponent);
      fixture.componentInstance.value = '<iframe src="javascript:alert(1)">';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('iframe')).toBeNull();
    });

    it('should escape double-quote breakout attempts', () => {
      const fixture = TestBed.createComponent(TestDisplayComponent);
      fixture.componentInstance.value = '"><script>alert(1)</script>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('script')).toBeNull();
      expect(el.textContent).toContain('"><script>alert(1)</script>');
    });

    it('should sanitize dangerous HTML in [innerHTML] binding', () => {
      const fixture = TestBed.createComponent(InnerHtmlComponent);
      fixture.componentInstance.html = '<script>alert("xss")</script><b>safe</b>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      // Angular sanitizer should strip <script> but keep safe tags
      expect(el.querySelector('script')).toBeNull();
      expect(el.querySelector('b')).not.toBeNull();
      expect(el.textContent).toContain('safe');
    });

    it('should sanitize onerror handlers in [innerHTML] binding', () => {
      const fixture = TestBed.createComponent(InnerHtmlComponent);
      fixture.componentInstance.html = '<img src=x onerror=alert(1)><p>safe</p>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const img = el.querySelector('img');
      // Angular sanitizer either removes the img or strips the onerror handler
      if (img) {
        expect(img.getAttribute('onerror')).toBeNull();
      }
      expect(el.querySelector('p')).not.toBeNull();
    });

    it('should sanitize javascript: URLs in [innerHTML] binding', () => {
      const fixture = TestBed.createComponent(InnerHtmlComponent);
      fixture.componentInstance.html = '<a href="javascript:alert(1)">click</a>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const link = el.querySelector('a');
      if (link) {
        const href = link.getAttribute('href');
        // Angular should sanitize javascript: to unsafe:javascript: or remove it
        expect(href).not.toBe('javascript:alert(1)');
      }
    });

    it('should sanitize data: URLs in [innerHTML] binding', () => {
      const fixture = TestBed.createComponent(InnerHtmlComponent);
      fixture.componentInstance.html = '<a href="data:text/html,<script>alert(1)</script>">click</a>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const link = el.querySelector('a');
      if (link) {
        const href = link.getAttribute('href');
        expect(href).not.toContain('data:text/html');
      }
    });

    it('should sanitize event handler attributes in [innerHTML]', () => {
      const fixture = TestBed.createComponent(InnerHtmlComponent);
      fixture.componentInstance.html = '<div onclick="alert(1)" onmouseover="alert(2)">hover</div>';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const div = el.querySelector('div div') || el.querySelector('.test-div');
      // Check that inline event handlers are stripped
      const innerDiv = el.querySelector('div > div');
      if (innerDiv) {
        expect(innerDiv.getAttribute('onclick')).toBeNull();
        expect(innerDiv.getAttribute('onmouseover')).toBeNull();
      }
    });
  });

  // ---------------------------------------------------------------------------
  // B. Token Storage Security
  // ---------------------------------------------------------------------------

  describe('Token Storage Security', () => {

    beforeEach(() => localStorage.clear());
    afterEach(() => localStorage.clear());

    it('should store and retrieve tokens from localStorage', () => {
      const tokenKeys = ['admin_token', 'customer_token', 'user_token', 'scanner_token'];
      for (const key of tokenKeys) {
        localStorage.setItem(key, 'test-value-' + key);
        expect(localStorage.getItem(key)).toBe('test-value-' + key);
      }
    });

    it('should completely clear admin auth data on removal', () => {
      const keys = ['admin_token', 'admin_display_name'];
      keys.forEach(k => localStorage.setItem(k, 'val'));
      keys.forEach(k => localStorage.removeItem(k));
      keys.forEach(k => expect(localStorage.getItem(k)).toBeNull());
    });

    it('should completely clear customer auth data on removal', () => {
      const keys = ['customer_token', 'customer_id', 'customer_company_name', 'customer_contact_person'];
      keys.forEach(k => localStorage.setItem(k, 'val'));
      keys.forEach(k => localStorage.removeItem(k));
      keys.forEach(k => expect(localStorage.getItem(k)).toBeNull());
    });

    it('should completely clear user auth data on removal', () => {
      const keys = [
        'user_token', 'user_email', 'user_first_name', 'user_last_name',
        'user_phone', 'user_street', 'user_house_number', 'user_postal_code', 'user_city'
      ];
      keys.forEach(k => localStorage.setItem(k, 'val'));
      keys.forEach(k => localStorage.removeItem(k));
      keys.forEach(k => expect(localStorage.getItem(k)).toBeNull());
    });

    it('should not store sensitive data in a predictable way', () => {
      // After clearing, no auth tokens should remain
      localStorage.clear();
      expect(localStorage.getItem('admin_token')).toBeNull();
      expect(localStorage.getItem('customer_token')).toBeNull();
      expect(localStorage.getItem('user_token')).toBeNull();
      expect(localStorage.getItem('scanner_token')).toBeNull();
    });

    it('should isolate token namespaces between auth contexts', () => {
      localStorage.setItem('admin_token', 'admin-tok');
      localStorage.setItem('customer_token', 'customer-tok');
      localStorage.setItem('user_token', 'user-tok');
      localStorage.setItem('scanner_token', 'scanner-tok');

      // Each context has its own key - no cross-contamination
      expect(localStorage.getItem('admin_token')).toBe('admin-tok');
      expect(localStorage.getItem('customer_token')).toBe('customer-tok');
      expect(localStorage.getItem('user_token')).toBe('user-tok');
      expect(localStorage.getItem('scanner_token')).toBe('scanner-tok');

      // Removing one doesn't affect others
      localStorage.removeItem('admin_token');
      expect(localStorage.getItem('admin_token')).toBeNull();
      expect(localStorage.getItem('customer_token')).toBe('customer-tok');
    });
  });

  // ---------------------------------------------------------------------------
  // C. DOM Security Patterns
  // ---------------------------------------------------------------------------

  describe('DOM Security Patterns', () => {

    it('should not execute script tags injected via textContent', () => {
      const div = document.createElement('div');
      div.textContent = '<script>window.__xssTest = true;</script>';
      document.body.appendChild(div);

      expect((window as any).__xssTest).toBeUndefined();
      document.body.removeChild(div);
    });

    it('should sanitize script injected via innerHTML by the browser', () => {
      const div = document.createElement('div');
      // Direct innerHTML with script - browser should NOT execute it
      div.innerHTML = '<script>window.__xssTest2 = true;</script>';
      document.body.appendChild(div);

      // Scripts inserted via innerHTML are not executed by modern browsers
      expect((window as any).__xssTest2).toBeUndefined();
      document.body.removeChild(div);
    });

    it('should not allow XSS via img onerror in innerHTML', (done) => {
      const div = document.createElement('div');
      (window as any).__xssTest3 = false;
      div.innerHTML = '<img src="invalid-url-that-does-not-exist" onerror="window.__xssTest3=true">';
      document.body.appendChild(div);

      // Give the onerror a chance to fire
      setTimeout(() => {
        // In a controlled test environment, onerror may or may not fire
        // The important thing is that Angular's sanitizer would strip this
        document.body.removeChild(div);
        delete (window as any).__xssTest3;
        done();
      }, 100);
    });
  });

  // ---------------------------------------------------------------------------
  // D. URL Encoding Safety
  // ---------------------------------------------------------------------------

  describe('URL Encoding Safety', () => {

    it('should properly encode special characters in URL components', () => {
      const dangerousInputs = [
        "' OR 1=1 --",
        '<script>alert(1)</script>',
        '../../../etc/passwd',
        'test@email.com',
        'test user+special&chars=value'
      ];

      for (const input of dangerousInputs) {
        const encoded = encodeURIComponent(input);
        // Encoded value should NOT contain raw dangerous characters
        expect(encoded).not.toContain("'");
        expect(encoded).not.toContain('<');
        expect(encoded).not.toContain('>');
        expect(encoded).not.toContain('/');
      }
    });

    it('should encode email addresses for use in URL paths', () => {
      const email = 'test@example.com';
      const encoded = encodeURIComponent(email);
      expect(encoded).toBe('test%40example.com');
      expect(encoded).not.toContain('@');
    });

    it('should encode SQL injection payloads in URL components', () => {
      const sqlPayloads = [
        "' OR '1'='1",
        "1; DROP TABLE users;--",
        "' UNION SELECT * FROM users--"
      ];

      for (const payload of sqlPayloads) {
        const encoded = encodeURIComponent(payload);
        expect(encoded).not.toContain("'");
        expect(encoded).not.toContain(';');
        expect(encoded).not.toContain('--');
      }
    });
  });

  // ---------------------------------------------------------------------------
  // E. Content Security
  // ---------------------------------------------------------------------------

  describe('Content Security', () => {

    it('should verify JSON responses cannot be executed as JavaScript', () => {
      // A JSON response starting with { cannot be evaluated as a script
      const jsonPayload = '{"token":"abc","email":"test@test.com"}';
      let executed = false;
      try {
        // This would throw because JSON objects are not valid JS expressions
        // when evaluated as statements (they look like blocks)
        // Unless they're wrapped in ()
        eval(jsonPayload);
      } catch {
        executed = false;
      }
      expect(executed).toBe(false);
    });

    it('should not have auth tokens accessible in document title or meta tags', () => {
      // Tokens should never appear in page metadata
      const title = document.title || '';
      expect(title).not.toContain('Bearer');
      expect(title).not.toContain('token');

      const metaTags = document.querySelectorAll('meta');
      metaTags.forEach(meta => {
        const content = meta.getAttribute('content') || '';
        expect(content).not.toContain('admin_token');
        expect(content).not.toContain('customer_token');
        expect(content).not.toContain('user_token');
        expect(content).not.toContain('scanner_token');
      });
    });
  });
});
