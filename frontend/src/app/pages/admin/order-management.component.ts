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
  template: `
    @if (event) {
      <div class="flex justify-between align-center mb-2">
        <div>
          <h1>{{ event.name }} - Bestellingen</h1>
          <p class="text-muted">{{ event.ticketsSold }} verkocht / {{ event.maxTickets }} totaal</p>
        </div>
      </div>
    }

    <p-table
      [value]="orders"
      [paginator]="true"
      [rows]="15"
      [loading]="loading"
      styleClass="p-datatable-striped">
      <ng-template #header>
        <tr>
          <th pSortableColumn="orderNumber">Bestelnr.</th>
          <th pSortableColumn="buyerFirstName">Naam</th>
          <th>E-mail</th>
          <th>Aantal</th>
          <th>Totaal</th>
          <th pSortableColumn="status">Status</th>
          <th>Besteld op</th>
          <th>Acties</th>
        </tr>
      </ng-template>
      <ng-template #body let-order>
        <tr>
          <td data-label="Bestelnr."><code>{{ order.orderNumber }}</code></td>
          <td data-label="Naam">{{ order.buyerFirstName }} {{ order.buyerLastName }}</td>
          <td data-label="E-mail">{{ order.buyerEmail }}</td>
          <td data-label="Aantal">{{ order.quantity }}</td>
          <td data-label="Totaal">{{ order.totalPrice | number:'1.2-2' }}</td>
          <td data-label="Status">
            <p-tag [value]="getStatusLabel(order.status)" [severity]="getStatusSeverity(order.status)" />
          </td>
          <td data-label="Besteld op">{{ formatDate(order.createdAt) }}</td>
          <td data-label="Acties">
            <div class="flex gap-1">
              @if (order.status === 'CONFIRMED') {
                <p-button icon="pi pi-download" [rounded]="true" [text]="true" (onClick)="downloadPdf(order)" pTooltip="Download PDF" />
              }
              @if (order.status === 'RESERVED') {
                <p-button icon="pi pi-check" [rounded]="true" [text]="true" severity="success" (onClick)="confirmOrder(order)" />
              }
            </div>
          </td>
        </tr>
      </ng-template>
    </p-table>
  `,
  styles: [`
    h1 { color: var(--p-primary-color); margin: 0; }
    .text-muted { color: var(--p-text-muted-color); margin: 0.25rem 0 0 0; }
  `]
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
