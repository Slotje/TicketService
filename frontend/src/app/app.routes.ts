import { Routes } from '@angular/router';
import { scannerAuthGuard } from './guards/scanner-auth.guard';
import { adminAuthGuard } from './guards/admin-auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent)
  },
  {
    path: 'event/:id',
    loadComponent: () => import('./pages/home/event-detail.component').then(m => m.EventDetailComponent)
  },
  {
    path: 'order/:orderNumber',
    loadComponent: () => import('./pages/home/order-confirmation.component').then(m => m.OrderConfirmationComponent)
  },
  {
    path: 'my-tickets',
    loadComponent: () => import('./pages/home/my-tickets.component').then(m => m.MyTicketsComponent)
  },
  {
    path: 'scan/login',
    loadComponent: () => import('./pages/scanner/scanner-login.component').then(m => m.ScannerLoginComponent)
  },
  {
    path: 'scan',
    loadComponent: () => import('./pages/scanner/ticket-scanner-camera.component').then(m => m.TicketScannerCameraComponent),
    canActivate: [scannerAuthGuard]
  },
  {
    path: 'admin/login',
    loadComponent: () => import('./pages/admin/admin-login.component').then(m => m.AdminLoginComponent)
  },
  {
    path: 'admin',
    loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/customers',
    loadComponent: () => import('./pages/admin/customer-management.component').then(m => m.CustomerManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/events',
    loadComponent: () => import('./pages/admin/event-management.component').then(m => m.EventManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/orders/:eventId',
    loadComponent: () => import('./pages/admin/order-management.component').then(m => m.OrderManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/scanners',
    loadComponent: () => import('./pages/admin/scanner-management.component').then(m => m.ScannerManagementComponent),
    canActivate: [adminAuthGuard]
  },
  {
    path: 'admin/scan',
    redirectTo: '/scan/login',
    pathMatch: 'full'
  },
  { path: '**', redirectTo: '' }
];
