import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Ticket } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-ticket-scanner',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message],
  template: `
    <div class="scanner-page">
      <p-card>
        <ng-template #title>
          <i class="pi pi-qrcode"></i> Ticket Scanner
        </ng-template>

        <p class="description">Voer de QR code data in of scan de QR code om een ticket te valideren.</p>

        <div class="scan-form">
          <p-floatlabel>
            <input pInputText id="qrData" [(ngModel)]="qrCodeData" class="w-full scan-input"
                   (keyup.enter)="scanTicket()" />
            <label for="qrData">QR Code Data</label>
          </p-floatlabel>
          <p-button label="Scan" icon="pi pi-search" (onClick)="scanTicket()" [loading]="scanning" />
        </div>

        @if (successMessage) {
          <div class="result-card success mt-2">
            <i class="pi pi-check-circle"></i>
            <div>
              <strong>Ticket geldig!</strong>
              <p>{{ successMessage }}</p>
            </div>
          </div>
        }

        @if (errorMessage) {
          <div class="result-card error mt-2">
            <i class="pi pi-times-circle"></i>
            <div>
              <strong>Ongeldig!</strong>
              <p>{{ errorMessage }}</p>
            </div>
          </div>
        }

        @if (scannedTicket) {
          <div class="ticket-info mt-2">
            <div><strong>Ticketcode:</strong> {{ scannedTicket.ticketCode }}</div>
            <div><strong>Gescand op:</strong> {{ formatDate(scannedTicket.scannedAt!) }}</div>
          </div>
        }
      </p-card>
    </div>
  `,
  styles: [`
    .scanner-page { max-width: 600px; margin: 0 auto; }
    .description { color: var(--p-text-muted-color); margin-bottom: 1.5rem; }
    .scan-form {
      display: flex;
      gap: 0.5rem;
      align-items: flex-end;
    }
    .scan-input { font-size: 1.1rem; }
    .result-card {
      display: flex;
      gap: 1rem;
      align-items: center;
      padding: 1.5rem;
      border-radius: var(--p-border-radius);
      i { font-size: 2rem; }
      &.success {
        background: #e8f5e9;
        color: #2e7d32;
        border: 2px solid #4caf50;
      }
      &.error {
        background: #ffebee;
        color: #c62828;
        border: 2px solid #f44336;
      }
    }
    .ticket-info {
      background: var(--p-surface-100);
      padding: 1rem;
      border-radius: var(--p-border-radius);
      div { margin-bottom: 0.25rem; }
    }
  `]
})
export class TicketScannerComponent {
  qrCodeData = '';
  scanning = false;
  successMessage = '';
  errorMessage = '';
  scannedTicket: Ticket | null = null;

  constructor(private api: ApiService) {}

  scanTicket() {
    if (!this.qrCodeData.trim()) return;

    this.scanning = true;
    this.successMessage = '';
    this.errorMessage = '';
    this.scannedTicket = null;

    this.api.scanTicket(this.qrCodeData.trim()).subscribe({
      next: (ticket) => {
        this.scannedTicket = ticket;
        this.successMessage = `Ticket ${ticket.ticketCode} is succesvol gescand.`;
        this.scanning = false;
        this.qrCodeData = '';
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Ticket niet gevonden of ongeldig';
        this.scanning = false;
      }
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }
}
