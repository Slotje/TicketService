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
  templateUrl: './order-confirmation.component.html',
  styleUrl: './order-confirmation.component.scss'
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
