import { Routes } from '@angular/router';

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
    path: 'admin',
    loadComponent: () => import('./pages/admin/admin.component').then(m => m.AdminComponent)
  },
  {
    path: 'admin/customers',
    loadComponent: () => import('./pages/admin/customer-management.component').then(m => m.CustomerManagementComponent)
  },
  {
    path: 'admin/events',
    loadComponent: () => import('./pages/admin/event-management.component').then(m => m.EventManagementComponent)
  },
  {
    path: 'admin/orders/:eventId',
    loadComponent: () => import('./pages/admin/order-management.component').then(m => m.OrderManagementComponent)
  },
  {
    path: 'admin/scan',
    loadComponent: () => import('./pages/admin/ticket-scanner.component').then(m => m.TicketScannerComponent)
  },
  { path: '**', redirectTo: '' }
];
