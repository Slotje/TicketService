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
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  menuItems: MenuItem[] = [];
  private subs: Subscription[] = [];

  constructor(
    public adminAuth: AdminAuthService,
    private scannerAuth: AuthService,
    public userAuth: UserAuthService,
    public customerAuth: CustomerAuthService,
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
      {
        label: 'Scanner',
        icon: 'pi pi-qrcode',
        routerLink: isScanner ? '/scan' : '/scan/login'
      },
      ...(isAdmin ? [{
        label: 'Beheer',
        icon: 'pi pi-cog',
        items: [
          { label: 'Dashboard', icon: 'pi pi-home', routerLink: '/admin' },
          { label: 'Klanten', icon: 'pi pi-users', routerLink: '/admin/customers' },
          { label: 'Evenementen', icon: 'pi pi-calendar', routerLink: '/admin/events' },
          { label: 'Scanner Accounts', icon: 'pi pi-id-card', routerLink: '/admin/scanners' }
        ]
      }] : [])
    ];
  }
}
