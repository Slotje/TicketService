import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { UserAuthService } from '../../services/user-auth.service';
import { Order } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { Message } from 'primeng/message';
import { Divider } from 'primeng/divider';
import { ProgressBar } from 'primeng/progressbar';

@Component({
  selector: 'app-my-tickets',
  imports: [CommonModule, Card, Button, Tag, Message, Divider, ProgressBar],
  templateUrl: './my-tickets.component.html',
  styleUrl: './my-tickets.component.scss'
})
export class MyTicketsComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  errorMessage = '';

  constructor(
    private api: ApiService,
    public userAuth: UserAuthService,
    private router: Router
  ) {}

  ngOnInit() {
    const email = this.userAuth.email;
    if (!email) {
      this.router.navigate(['/login'], { state: { returnUrl: '/my-tickets' } });
      return;
    }

    this.api.getOrdersByEmail(email).subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Fout bij ophalen van bestellingen.';
        this.loading = false;
      }
    });
  }

  openOrder(orderNumber: string) {
    this.router.navigate(['/order', orderNumber]);
  }

  getScannedCount(order: Order): number {
    return order.tickets?.filter(t => t.scanned).length ?? 0;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      RESERVED: 'Gereserveerd', CONFIRMED: 'Bevestigd',
      CANCELLED: 'Geannuleerd', EXPIRED: 'Verlopen'
    };
    return map[status] ?? status;
  }

  getStatusSeverity(status: string): 'info' | 'success' | 'danger' | 'warn' {
    const map: Record<string, 'info' | 'success' | 'danger' | 'warn'> = {
      RESERVED: 'warn', CONFIRMED: 'success',
      CANCELLED: 'danger', EXPIRED: 'danger'
    };
    return map[status] ?? 'info';
  }
}
