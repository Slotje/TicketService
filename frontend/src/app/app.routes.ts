import { Routes } from '@angular/router';
import { scannerAuthGuard } from './guards/scanner-auth.guard';
import { adminAuthGuard } from './guards/admin-auth.guard';
import { userAuthGuard } from './guards/user-auth.guard';
import { customerAuthGuard } from './guards/customer-auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'event/:id',
    loadComponent: () => import('./pages/home/event-detail/event-detail.component').then(m => m.EventDetailComponent)
  },
  {
    path: 'order/:orderNumber',
    loadComponent: () => import('./pages/home/order-comfirmation/order-confirmation.component').then(m => m.OrderConfirmationComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/home/user-login/user-login.component').then(m => m.UserLoginComponent)
  },
  {
    path: 'forgot-password',
    loadComponent: () => import('./pages/home/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./pages/home/reset-password/reset-password.component').then(m => m.ResetPasswordComponent)
  },
  {
    path: 'my-tickets',
    loadComponent: () => import('./pages/home/my-tickets/my-tickets.component').then(m => m.MyTicketsComponent),
    canActivate: [userAuthGuard]
  },
  // Customer (klant) routes
  {
    path: 'klant/login',
    loadComponent: () => import('./pages/customer/customer-login/customer-login.component').then(m => m.CustomerLoginComponent)
  },
  {
    path: 'klant/activeren/:token',
    loadComponent: () => import('./pages/customer/customer-activate/customer-activate.component').then(m => m.CustomerActivateComponent)
  },
  {
    path: 'klant/forgot-password',
    loadComponent: () => import('./pages/customer/customer-forgot-password/customer-forgot-password.component').then(m => m.CustomerForgotPasswordComponent)
  },
  {
    path: 'klant/reset-password',
    loadComponent: () => import('./pages/home/reset-password/reset-password.component').then(m => m.ResetPasswordComponent)
  },
  {
    path: 'klant/dashboard',
    loadComponent: () => import('./pages/customer/customer-dashboard/customer-dashboard.component').then(m => m.CustomerDashboardComponent),
    canActivate: [customerAuthGuard]
  },
  {
    path: 'klant/:slug',
    loadComponent: () => import('./pages/customer/customer-landing/customer-landing.component').then(m => m.CustomerLandingComponent)
  },
  // Scanner routes
  {
    path: 'scan/login',
    loadComponent: () => import('./pages/scanner/scanner-login/scanner-login.component').then(m => m.ScannerLoginComponent)
  },
  {
    path: 'scan',
    loadComponent: () => import('./pages/scanner/ticket-scanner-camera/ticket-scanner-camera.component').then(m => m.TicketScannerCameraComponent),
    canActivate: [scannerAuthGuard]
  },
  // Admin routes
  {
    path: 'admin/login',
    loadComponent: () => import('./pages/admin/admin-login/admin-login.component').then(m => m.AdminLoginComponent)
  },
  {
    path: 'admin',
    loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/customers',
    loadComponent: () => import('./pages/admin/customer-management/customer-management.component').then(m => m.CustomerManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/events',
    loadComponent: () => import('./pages/admin/event-management/event-management.component').then(m => m.EventManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/orders/:eventId',
    loadComponent: () => import('./pages/admin/order-management/order-management.component').then(m => m.OrderManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/scanners',
    loadComponent: () => import('./pages/admin/scanner-management/scanner-management.component').then(m => m.ScannerManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/scan',
    redirectTo: '/scan/login',
    pathMatch: 'full'
  },
  { path: '**', redirectTo: '' }
];
