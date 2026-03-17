import { Component } from '@angular/core';
import { RouterOutlet, RouterLink } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { Menubar } from 'primeng/menubar';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, Menubar],
  template: `
    <div class="layout-wrapper">
      <p-menubar [model]="menuItems">
        <ng-template #start>
          <a routerLink="/" class="app-logo">
            <i class="pi pi-ticket" style="font-size: 1.5rem; margin-right: 0.5rem"></i>
            <span class="app-name">TicketService</span>
          </a>
        </ng-template>
      </p-menubar>
      <div class="layout-content">
        <router-outlet />
      </div>
      <footer class="layout-footer">
        <span>TicketService &copy; 2026 - Alle rechten voorbehouden</span>
      </footer>
    </div>
  `,
  styles: [`
    .layout-wrapper {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .app-logo {
      display: flex;
      align-items: center;
      text-decoration: none;
      color: var(--p-primary-color);
      font-weight: 700;
      font-size: 1.25rem;
      margin-right: 1rem;
    }
    .layout-content {
      flex: 1;
      padding: 1.5rem;
      max-width: 1400px;
      margin: 0 auto;
      width: 100%;
      box-sizing: border-box;
    }
    @media (max-width: 768px) {
      .layout-content {
        padding: 0.75rem;
      }
      .app-name {
        font-size: 1rem;
      }
    }
    .layout-footer {
      text-align: center;
      padding: 1rem;
      background: var(--p-surface-100);
      color: var(--p-text-muted-color);
      font-size: 0.875rem;
    }
  `]
})
export class AppComponent {
  menuItems: MenuItem[] = [
    { label: 'Evenementen', icon: 'pi pi-calendar', routerLink: '/' },
    { label: 'Scanner', icon: 'pi pi-qrcode', routerLink: '/scan/login' },
    {
      label: 'Beheer',
      icon: 'pi pi-cog',
      items: [
        { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/admin' },
        { label: 'Klanten', icon: 'pi pi-users', routerLink: '/admin/customers' },
        { label: 'Evenementen', icon: 'pi pi-calendar', routerLink: '/admin/events' },
        { label: 'Scanner Accounts', icon: 'pi pi-id-card', routerLink: '/admin/scanners' }
      ]
    }
  ];
}
