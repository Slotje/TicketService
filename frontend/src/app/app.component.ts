import { Component, OnInit, OnDestroy, HostListener, Inject, PLATFORM_ID } from '@angular/core';
import { RouterOutlet, RouterLink, Router, NavigationEnd } from '@angular/router';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { MenuItem } from 'primeng/api';
import { Drawer } from 'primeng/drawer';
import { AdminAuthService } from './services/admin-auth.service';
import { AuthService } from './services/auth.service';
import { UserAuthService } from './services/user-auth.service';
import { CustomerAuthService } from './services/customer-auth.service';
import { CartService } from './services/cart.service';
import { Subscription, filter } from 'rxjs';
import * as AOS from 'aos';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, CommonModule, Drawer],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit, OnDestroy {
  menuItems: MenuItem[] = [];
  mobileMenuVisible = false;
  adminMenuVisible = false;
  navbarScrolled = false;
  navbarSolid = false;
  private subs: Subscription[] = [];
  private isBrowser: boolean;

  constructor(
    public adminAuth: AdminAuthService,
    private scannerAuth: AuthService,
    public userAuth: UserAuthService,
    public customerAuth: CustomerAuthService,
    public cart: CartService,
    private router: Router,
    @Inject(PLATFORM_ID) platformId: Object
  ) {
    this.isBrowser = isPlatformBrowser(platformId);
  }

  @HostListener('window:scroll')
  onWindowScroll() {
    this.navbarScrolled = window.scrollY > 60;
  }

  ngOnInit() {
    if (this.isBrowser) {
      AOS.init({
        duration: 800,
        easing: 'ease-out-cubic',
        once: true,
        offset: 60,
        delay: 0
      });
    }

    this.subs.push(
      this.adminAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.scannerAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.userAuth.isLoggedIn$.subscribe(() => this.buildMenu()),
      this.customerAuth.isLoggedIn$.subscribe(() => this.buildMenu())
    );

    // Refresh AOS on route change & detect pages without dark hero
    this.subs.push(
      this.router.events.pipe(
        filter(e => e instanceof NavigationEnd)
      ).subscribe((e) => {
        if (this.isBrowser) {
          setTimeout(() => AOS.refresh(), 100);
        }
        const url = (e as NavigationEnd).urlAfterRedirects || (e as NavigationEnd).url;
        // Only the homepage (/) has a dark hero — all other pages need a solid navbar
        this.navbarSolid = url !== '/' && url !== '';
      })
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
