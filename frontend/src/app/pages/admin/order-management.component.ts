import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Order, Event } from '../../models/models';
import { TableModule } from 'primeng/table';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { Card } from 'primeng/card';

@Component({
  selector: 'app-order-management',
  imports: [CommonModule, TableModule, Button, Tag, Card],
  templateUrl: './order-management.component.html',
  styleUrl: './order-management.component.scss'
})
export class OrderManagementComponent implements OnInit {
  orders: Order[] = [];
  event: Event | null = null;
  loading = true;
  eventId!: number;

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.eventId = Number(this.route.snapshot.paramMap.get('eventId'));
    this.api.getEvent(this.eventId).subscribe(e => this.event = e);
    this.loadOrders();
  }

  loadOrders() {
    this.loading = true;
    this.api.getOrdersByEvent(this.eventId).subscribe({
      next: (o) => { this.orders = o; this.loading = false; },
      error: () => this.loading = false
    });
  }

  confirmOrder(order: Order) {
    this.api.confirmOrder(order.id).subscribe(() => this.loadOrders());
  }

  downloadPdf(order: Order) {
    this.api.downloadOrderPdf(order.id).subscribe(blob => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `tickets-${order.orderNumber}.pdf`;
      a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
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

  getStatusSeverity(status: string): 'success' | 'warn' | 'danger' | 'info' {
    const map: Record<string, 'success' | 'warn' | 'danger' | 'info'> = {
      RESERVED: 'warn', CONFIRMED: 'success',
      CANCELLED: 'danger', EXPIRED: 'danger'
    };
    return map[status] ?? 'info';
  }
}
