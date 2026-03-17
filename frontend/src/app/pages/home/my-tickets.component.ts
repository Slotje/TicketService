import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Order } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { Message } from 'primeng/message';
import { InputText } from 'primeng/inputtext';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-my-tickets',
  imports: [CommonModule, FormsModule, Card, Button, Tag, Message, InputText, Divider],
  template: `
    <div class="my-tickets-page">
      <p-card>
        <ng-template #title>
          <div class="flex align-center gap-1">
            <i class="pi pi-ticket"></i>
            <span>Mijn Tickets</span>
          </div>
        </ng-template>
        <ng-template #subtitle>
          Voer je e-mailadres in om je bestellingen en tickets te bekijken.
        </ng-template>

        <div class="search-form">
          <div class="p-inputgroup">
            <input
              type="email"
              pInputText
              [(ngModel)]="email"
              placeholder="E-mailadres"
              (keyup.enter)="searchOrders()" />
            <p-button
              icon="pi pi-search"
              label="Zoeken"
              (onClick)="searchOrders()"
              [loading]="loading" />
          </div>
        </div>

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mt-2" />
        }

        @if (searched && orders.length === 0 && !loading) {
          <p-message severity="info" text="Geen bestellingen gevonden voor dit e-mailadres." [style]="{'width': '100%'}" class="mt-2" />
        }

        @if (orders.length > 0) {
          <p-divider />
          <div class="orders-list">
            @for (order of orders; track order.id) {
              <div class="order-card" (click)="openOrder(order.orderNumber)">
                <div class="order-header">
                  <span class="order-number">{{ order.orderNumber }}</span>
                  <p-tag [value]="getStatusLabel(order.status)" [severity]="getStatusSeverity(order.status)" />
                </div>
                <div class="order-info">
                  <div class="info-item">
                    <i class="pi pi-calendar"></i>
                    <span>{{ order.eventName }}</span>
                  </div>
                  <div class="info-item">
                    <i class="pi pi-ticket"></i>
                    <span>{{ order.quantity }} ticket(s)</span>
                  </div>
                  <div class="info-item">
                    <i class="pi pi-euro"></i>
                    <span>{{ order.totalPrice | number:'1.2-2' }}</span>
                  </div>
                  <div class="info-item">
                    <i class="pi pi-clock"></i>
                    <span>{{ formatDate(order.createdAt) }}</span>
                  </div>
                </div>
                @if (order.status === 'CONFIRMED' && order.tickets) {
                  <div class="scan-status">
                    <span>{{ getScannedCount(order) }} van {{ order.tickets.length }} gescand</span>
                  </div>
                }
              </div>
            }
          </div>
        }
      </p-card>
    </div>
  `,
  styles: [`
    .my-tickets-page {
      max-width: 800px;
      margin: 0 auto;
    }
    .search-form {
      max-width: 500px;
    }
    .orders-list {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .order-card {
      padding: 1rem;
      border: 1px solid var(--p-surface-300);
      border-radius: var(--p-border-radius);
      background: var(--p-surface-50);
      cursor: pointer;
      transition: border-color 0.2s;
      &:hover {
        border-color: var(--p-primary-color);
      }
    }
    .order-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.75rem;
    }
    .order-number {
      font-family: monospace;
      font-weight: 600;
      font-size: 1.1rem;
    }
    .order-info {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.5rem;
      @media (max-width: 600px) { grid-template-columns: 1fr; }
    }
    .info-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--p-text-muted-color);
      font-size: 0.9rem;
      i { font-size: 0.85rem; }
    }
    .scan-status {
      margin-top: 0.75rem;
      font-size: 0.85rem;
      color: var(--p-text-muted-color);
    }
  `]
})
export class MyTicketsComponent {
  email = '';
  orders: Order[] = [];
  loading = false;
  searched = false;
  errorMessage = '';

  constructor(private api: ApiService, private router: Router) {}

  searchOrders() {
    if (!this.email.trim()) return;
    this.loading = true;
    this.errorMessage = '';
    this.searched = true;
    this.api.getOrdersByEmail(this.email.trim()).subscribe({
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
