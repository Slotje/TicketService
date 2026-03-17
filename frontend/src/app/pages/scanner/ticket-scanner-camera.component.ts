import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { Ticket } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';
import { Html5Qrcode } from 'html5-qrcode';

@Component({
  selector: 'app-ticket-scanner-camera',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Tag],
  template: `
    <div class="scanner-page">
      <div class="scanner-header">
        <div class="flex justify-between align-center">
          <h2><i class="pi pi-qrcode"></i> Ticket Scanner</h2>
          <div class="flex align-center gap-1">
            <p-tag [value]="auth.displayName" icon="pi pi-user" severity="info" />
            <p-button icon="pi pi-sign-out" [rounded]="true" [text]="true" severity="danger"
                      (onClick)="logout()" pTooltip="Uitloggen" />
          </div>
        </div>
      </div>

      <p-card>
        <div class="camera-section">
          <div id="qr-reader" #qrReader></div>

          @if (!cameraActive) {
            <p-button label="Camera starten" icon="pi pi-camera" (onClick)="startCamera()"
                      [style]="{'width': '100%'}" class="mt-1" />
          } @else {
            <p-button label="Camera stoppen" icon="pi pi-stop" (onClick)="stopCamera()"
                      severity="secondary" [style]="{'width': '100%'}" class="mt-1" />
          }
        </div>

        <div class="manual-section mt-2">
          <p class="or-divider">of voer handmatig in</p>
          <div class="scan-form">
            <p-floatlabel>
              <input pInputText id="qrData" [(ngModel)]="qrCodeData" class="w-full"
                     (keyup.enter)="scanTicket()" />
              <label for="qrData">QR Code Data</label>
            </p-floatlabel>
            <p-button label="Scan" icon="pi pi-search" (onClick)="scanTicket()" [loading]="scanning" />
          </div>
        </div>
      </p-card>

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

      <div class="scan-history mt-2">
        <h3>Scan Geschiedenis</h3>
        @if (scanHistory.length === 0) {
          <p class="text-muted">Nog geen tickets gescand in deze sessie.</p>
        }
        @for (scan of scanHistory; track scan.ticketCode) {
          <div class="history-item" [class.valid]="scan.valid" [class.invalid]="!scan.valid">
            <i [class]="scan.valid ? 'pi pi-check-circle' : 'pi pi-times-circle'"></i>
            <span class="history-code">{{ scan.ticketCode || scan.rawData }}</span>
            <span class="history-time">{{ scan.time }}</span>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .scanner-page {
      max-width: 600px;
      margin: 0 auto;
    }
    .scanner-header h2 {
      margin: 0;
      color: var(--p-primary-color);
      i { margin-right: 0.5rem; }
    }
    #qr-reader {
      width: 100%;
      border-radius: var(--p-border-radius);
      overflow: hidden;
    }
    .or-divider {
      text-align: center;
      color: var(--p-text-muted-color);
      position: relative;
      &::before, &::after {
        content: '';
        position: absolute;
        top: 50%;
        width: 30%;
        height: 1px;
        background: var(--p-surface-300);
      }
      &::before { left: 0; }
      &::after { right: 0; }
    }
    .scan-form {
      display: flex;
      gap: 0.5rem;
      align-items: flex-end;
    }
    .result-card {
      display: flex;
      gap: 1rem;
      align-items: center;
      padding: 1.5rem;
      border-radius: var(--p-border-radius);
      i { font-size: 2rem; }
      p { margin: 0.25rem 0 0; }
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
    .scan-history {
      h3 { color: var(--p-primary-color); }
      .text-muted { color: var(--p-text-muted-color); }
    }
    .history-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.5rem 0.75rem;
      border-radius: var(--p-border-radius);
      margin-bottom: 0.25rem;
      font-size: 0.9rem;
      &.valid {
        background: #e8f5e9;
        color: #2e7d32;
      }
      &.invalid {
        background: #ffebee;
        color: #c62828;
      }
      .history-code {
        flex: 1;
        font-family: monospace;
        font-weight: 600;
      }
      .history-time {
        font-size: 0.8rem;
        opacity: 0.7;
      }
    }
  `]
})
export class TicketScannerCameraComponent implements OnInit, OnDestroy {
  @ViewChild('qrReader') qrReaderEl!: ElementRef;

  qrCodeData = '';
  scanning = false;
  cameraActive = false;
  successMessage = '';
  errorMessage = '';
  scannedTicket: Ticket | null = null;

  scanHistory: { ticketCode: string; rawData: string; valid: boolean; time: string }[] = [];

  private html5Qrcode: Html5Qrcode | null = null;

  constructor(
    public auth: AuthService,
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit() {}

  ngOnDestroy() {
    this.stopCamera();
  }

  async startCamera() {
    try {
      this.html5Qrcode = new Html5Qrcode('qr-reader');
      await this.html5Qrcode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        (decodedText) => this.onQrCodeScanned(decodedText),
        () => {}
      );
      this.cameraActive = true;
    } catch (err) {
      this.errorMessage = 'Kan camera niet openen. Controleer de toestemming.';
    }
  }

  async stopCamera() {
    if (this.html5Qrcode && this.cameraActive) {
      try {
        await this.html5Qrcode.stop();
      } catch {}
      this.cameraActive = false;
    }
  }

  private onQrCodeScanned(data: string) {
    if (this.scanning) return;
    this.qrCodeData = data;
    this.scanTicket();
  }

  scanTicket() {
    const data = this.qrCodeData.trim();
    if (!data) return;

    this.scanning = true;
    this.successMessage = '';
    this.errorMessage = '';
    this.scannedTicket = null;

    this.http.post<Ticket>(`/api/orders/scan/${encodeURIComponent(data)}`, {}, {
      headers: { Authorization: `Bearer ${this.auth.token}` }
    }).subscribe({
      next: (ticket) => {
        this.scannedTicket = ticket;
        this.successMessage = `Ticket ${ticket.ticketCode} is succesvol gescand.`;
        this.scanHistory.unshift({
          ticketCode: ticket.ticketCode,
          rawData: data,
          valid: true,
          time: new Date().toLocaleTimeString('nl-NL')
        });
        this.scanning = false;
        this.qrCodeData = '';
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Ticket niet gevonden of ongeldig';
        this.scanHistory.unshift({
          ticketCode: '',
          rawData: data.substring(0, 20) + '...',
          valid: false,
          time: new Date().toLocaleTimeString('nl-NL')
        });
        this.scanning = false;
      }
    });
  }

  logout() {
    this.stopCamera();
    this.auth.logout();
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
  }
}
