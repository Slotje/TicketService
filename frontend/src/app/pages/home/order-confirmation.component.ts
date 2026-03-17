import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Order } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-order-confirmation',
  imports: [CommonModule, Card, Button, Tag, Message, ProgressBar, Divider],
  template: `
    @if (loading) {
      <p-progressBar mode="indeterminate" [style]="{'height': '6px'}" />
    }

    @if (order) {
      <div class="order-page">
        <p-card>
          <ng-template #title>
            <div class="flex align-center gap-1">
              <i class="pi pi-shopping-bag"></i>
              <span>Bestelling {{ order.orderNumber }}</span>
              <p-tag [value]="getStatusLabel()" [severity]="getStatusSeverity()" />
            </div>
          </ng-template>

          @if (order.status === 'RESERVED') {
            <p-message severity="warn" [style]="{'width': '100%'}">
              <ng-template #detail>
                Reservering verloopt over <strong>{{ remainingTime }}</strong>.
                Bevestig je bestelling om je tickets veilig te stellen.
              </ng-template>
            </p-message>
          }

          @if (errorMessage) {
            <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
          }
          @if (successMessage) {
            <p-message severity="success" [text]="successMessage" [style]="{'width': '100%'}" class="mb-2" />
          }

          <div class="order-details mt-2">
            <div class="detail-grid">
              <div class="detail-item">
                <label>Evenement</label>
                <span>{{ order.eventName }}</span>
              </div>
              <div class="detail-item">
                <label>Naam</label>
                <span>{{ order.buyerName }}</span>
              </div>
              <div class="detail-item">
                <label>E-mail</label>
                <span>{{ order.buyerEmail }}</span>
              </div>
              <div class="detail-item">
                <label>Aantal tickets</label>
                <span>{{ order.quantity }}</span>
              </div>
              <div class="detail-item">
                <label>Totaalprijs</label>
                <span class="total-price">EUR {{ order.totalPrice | number:'1.2-2' }}</span>
              </div>
              <div class="detail-item">
                <label>Besteld op</label>
                <span>{{ formatDate(order.createdAt) }}</span>
              </div>
            </div>
          </div>

          <p-divider />

          @if (order.status === 'CONFIRMED') {
            <h3>Tickets</h3>
            <div class="tickets-grid">
              @for (ticket of order.tickets; track ticket.id) {
                <div class="ticket-card">
                  <div class="ticket-code">{{ ticket.ticketCode }}</div>
                  <img [src]="api.getQrCodeUrl(ticket.qrCodeData)" alt="QR Code" class="qr-image" />
                  @if (ticket.scanned) {
                    <p-tag value="Gescand" severity="info" />
                  }
                </div>
              }
            </div>
          }

          <div class="action-buttons mt-2">
            @if (order.status === 'RESERVED') {
              <p-button
                label="Bestelling Bevestigen"
                icon="pi pi-check"
                (onClick)="confirmOrder()"
                [loading]="processing"
                severity="success" />
              <p-button
                label="Annuleren"
                icon="pi pi-times"
                (onClick)="cancelOrder()"
                [loading]="processing"
                severity="danger"
                [outlined]="true" />
            }
            @if (order.status === 'CONFIRMED') {
              <p-button
                label="Download PDF Tickets"
                icon="pi pi-download"
                (onClick)="downloadPdf()"
                severity="success" />
              <p-button
                label="Bestelling Annuleren"
                icon="pi pi-times"
                (onClick)="cancelOrder()"
                [loading]="processing"
                severity="danger"
                [outlined]="true" />
            }
          </div>
        </p-card>
      </div>
    }
  `,
  styles: [`
    .order-page {
      max-width: 800px;
      margin: 0 auto;
    }
    .detail-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
      @media (max-width: 600px) { grid-template-columns: 1fr; }
    }
    .detail-item {
      label {
        display: block;
        font-size: 0.85rem;
        color: var(--p-text-muted-color);
        margin-bottom: 0.25rem;
      }
      span { font-weight: 500; }
    }
    .total-price {
      font-size: 1.25rem;
      color: var(--p-primary-color);
    }
    .tickets-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 1rem;
    }
    .ticket-card {
      text-align: center;
      padding: 1rem;
      border: 1px solid var(--p-surface-300);
      border-radius: var(--p-border-radius);
      background: var(--p-surface-50);
    }
    .ticket-code {
      font-family: monospace;
      font-weight: 600;
      margin-bottom: 0.5rem;
    }
    .qr-image {
      width: 150px;
      height: 150px;
    }
    .action-buttons {
      display: flex;
      gap: 0.5rem;
      flex-wrap: wrap;
    }
  `]
})
export class OrderConfirmationComponent implements OnInit, OnDestroy {
  order: Order | null = null;
  loading = true;
  processing = false;
  errorMessage = '';
  successMessage = '';
  remainingTime = '';
  private timerInterval: any;

  constructor(public api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    const orderNumber = this.route.snapshot.paramMap.get('orderNumber')!;
    this.api.getOrderByNumber(orderNumber).subscribe({
      next: (order) => {
        this.order = order;
        this.loading = false;
        if (order.status === 'RESERVED') {
          this.startTimer();
        }
      },
      error: () => this.loading = false
    });
  }

  ngOnDestroy() {
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  private startTimer() {
    this.updateTimer();
    this.timerInterval = setInterval(() => this.updateTimer(), 1000);
  }

  private updateTimer() {
    if (!this.order?.expiresAt) return;
    const now = new Date().getTime();
    const expires = new Date(this.order.expiresAt).getTime();
    const diff = expires - now;

    if (diff <= 0) {
      this.remainingTime = 'Verlopen';
      clearInterval(this.timerInterval);
      this.order!.status = 'EXPIRED';
      return;
    }

    const minutes = Math.floor(diff / 60000);
    const seconds = Math.floor((diff % 60000) / 1000);
    this.remainingTime = `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getStatusLabel(): string {
    const map: Record<string, string> = {
      RESERVED: 'Gereserveerd', CONFIRMED: 'Bevestigd',
      CANCELLED: 'Geannuleerd', EXPIRED: 'Verlopen'
    };
    return map[this.order?.status ?? ''] ?? this.order?.status ?? '';
  }

  getStatusSeverity(): 'info' | 'success' | 'danger' | 'warn' {
    const map: Record<string, 'info' | 'success' | 'danger' | 'warn'> = {
      RESERVED: 'warn', CONFIRMED: 'success',
      CANCELLED: 'danger', EXPIRED: 'danger'
    };
    return map[this.order?.status ?? ''] ?? 'info';
  }

  confirmOrder() {
    this.processing = true;
    this.errorMessage = '';
    this.api.confirmOrder(this.order!.id).subscribe({
      next: (order) => {
        this.order = order;
        this.processing = false;
        this.successMessage = 'Bestelling is bevestigd! Je tickets zijn nu beschikbaar.';
        clearInterval(this.timerInterval);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Fout bij bevestigen';
        this.processing = false;
      }
    });
  }

  cancelOrder() {
    this.processing = true;
    this.errorMessage = '';
    this.api.cancelOrder(this.order!.id).subscribe({
      next: (order) => {
        this.order = order;
        this.processing = false;
        this.successMessage = 'Bestelling is geannuleerd.';
        clearInterval(this.timerInterval);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Fout bij annuleren';
        this.processing = false;
      }
    });
  }

  downloadPdf() {
    this.api.downloadOrderPdf(this.order!.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `tickets-${this.order!.orderNumber}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.errorMessage = 'Fout bij downloaden van PDF';
      }
    });
  }
}
