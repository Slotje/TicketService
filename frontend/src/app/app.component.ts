import { Component, OnInit, OnDestroy } from '@angular/core';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MenuItem } from 'primeng/api';
import { Menubar } from 'primeng/menubar';
import { Button } from 'primeng/button';
import { AdminAuthService } from './services/admin-auth.service';
import { AuthService } from './services/auth.service';
import { UserAuthService } from './services/user-auth.service';
import { CustomerAuthService } from './services/customer-auth.service';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, CommonModule, Menubar, Button],
  template: `
    <div class="layout-wrapper">
      <p-menubar [model]="menuItems">
        <ng-template #start>
          <a routerLink="/" class="app-logo">
            <i class="pi pi-ticket" style="font-size: 1.5rem; margin-right: 0.5rem"></i>
            <span class="app-name">TicketService</span>
          </a>
        </ng-template>
        <ng-template #end>
          <div class="auth-buttons">
            @if (userAuth.isLoggedIn) {
              <span class="user-greeting">{{ userAuth.name }}</span>
              <p-button icon="pi pi-sign-out" label="Uitloggen" [text]="true" size="small" (onClick)="userAuth.logout()" />
            } @else {
              <p-button icon="pi pi-sign-in" label="Inloggen" [text]="true" size="small" (onClick)="goToLogin()" />
            }
          </div>
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
    .auth-buttons {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .user-greeting {
      font-size: 0.9rem;
      color: var(--p-text-muted-color);
    }
  `]
})
export class AppComponent implements OnInit, OnDestroy {
  menuItems: MenuItem[] = [];
  private subs: Subscription[] = [];

  constructor(
    private adminAuth: AdminAuthService,
    private scannerAuth: AuthService,
    public userAuth: UserAuthService,
    private customerAuth: CustomerAuthService,
    private router: Router
  ) {}

  ngOnInit() {
    this.subs.push(
      this.adminAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.scannerAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.userAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.customerAuth.isLoggedIn$.subscribe(() => this.buildMenu())
    );
  }

  ngOnDestroy() {
    this.subs.forEach(s => s.unsubscribe());
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }

  private buildMenu() {
    const isAdmin = this.adminAuth.isLoggedIn;
    const isScanner = this.scannerAuth.isLoggedIn;
    const isCustomer = this.customerAuth.isLoggedIn;

    this.menuItems = [
      { label: 'Evenementen', icon: 'pi pi-calendar', routerLink: '/' },
      { label: 'Mijn Tickets', icon: 'pi pi-ticket', routerLink: '/my-tickets' },
      ...(isCustomer ? [{
        label: 'Mijn Evenementen',
        icon: 'pi pi-building',
        routerLink: '/klant/dashboard'
      }] : []),
      ...(isAdmin || isScanner ? [{
        label: 'Scanner',
        icon: 'pi pi-qrcode',
        routerLink: isScanner ? '/scan' : '/scan/login'
      }] : []),
      ...(isAdmin ? [{
        label: 'Beheer',
        icon: 'pi pi-cog',
        items: [
          { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/admin' },
          { label: 'Klanten', icon: 'pi pi-users', routerLink: '/admin/customers' },
          { label: 'Evenementen', icon: 'pi pi-calendar', routerLink: '/admin/events' },
          { label: 'Scanner Accounts', icon: 'pi pi-id-card', routerLink: '/admin/scanners' },
          { separator: true },
          { label: 'Uitloggen', icon: 'pi pi-sign-out', command: () => this.adminAuth.logout() }
        ]
      }] : [])
    ];
  }
}
