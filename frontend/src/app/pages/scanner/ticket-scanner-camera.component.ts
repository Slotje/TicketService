import { Component, OnInit, OnDestroy, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../../services/auth.service';
import { Ticket, Event } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';
import { Select } from 'primeng/select';
import { Html5Qrcode } from 'html5-qrcode';

@Component({
  selector: 'app-ticket-scanner-camera',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Tag, Select],
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

      <!-- Event Selection -->
      <p-card class="mb-2">
        <ng-template #title>
          <div class="flex align-center gap-1">
            <i class="pi pi-calendar"></i>
            <span>Selecteer Evenement</span>
          </div>
        </ng-template>
        <p-select
          [options]="events"
          [(ngModel)]="selectedEventId"
          optionLabel="name"
          optionValue="id"
          placeholder="Kies een evenement om te scannen"
          [style]="{'width': '100%'}"
          [filter]="true"
          filterPlaceholder="Zoek evenement..."
          [showClear]="true">
          <ng-template #selectedItem let-selected>
            <span>{{ selected.name }} - {{ selected.location }}</span>
          </ng-template>
          <ng-template #item let-event>
            <div>
              <div><strong>{{ event.name }}</strong></div>
              <div class="text-sm text-muted">{{ event.location }} - {{ formatEventDate(event.eventDate) }}</div>
            </div>
          </ng-template>
        </p-select>
        @if (!selectedEventId) {
          <p-message severity="warn" text="Selecteer eerst een evenement voordat je tickets kunt scannen." [style]="{'width': '100%', 'margin-top': '0.75rem'}" />
        }
      </p-card>

      @if (selectedEventId) {
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
      }

      <!-- Scan Result Popup Overlay -->
      @if (showPopup) {
        <div class="scan-popup-overlay" (click)="dismissPopup()">
          <div class="scan-popup" [class.success]="popupType === 'success'"
               [class.error]="popupType === 'error'" [class.warning]="popupType === 'warning'">
            @if (popupType === 'success') {
              <i class="pi pi-check-circle popup-icon"></i>
              <h2>Geldig!</h2>
            }
            @if (popupType === 'warning') {
              <i class="pi pi-exclamation-circle popup-icon"></i>
              <h2>Al Gescand!</h2>
            }
            @if (popupType === 'error') {
              <i class="pi pi-times-circle popup-icon"></i>
              <h2>Ongeldig!</h2>
            }
            <p class="popup-message">{{ popupMessage }}</p>
            @if (scannedTicket && popupType === 'success') {
              <div class="popup-ticket-info">
                <span>{{ scannedTicket.ticketCode }}</span>
              </div>
            }
            <p class="popup-dismiss">Tik om door te gaan</p>
          </div>
        </div>
      }

      <div class="scan-history mt-2">
        <h3>Scan Geschiedenis</h3>
        @if (scanHistory.length === 0) {
          <p class="text-muted">Nog geen tickets gescand in deze sessie.</p>
        }
        @for (scan of scanHistory; track $index) {
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
    .text-sm { font-size: 0.85rem; }
    .text-muted { color: var(--p-text-muted-color); }
    .mb-2 { margin-bottom: 1rem; }
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
    .scan-popup-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.7);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 9999;
      cursor: pointer;
    }
    .scan-popup {
      background: white;
      border-radius: 16px;
      padding: 3rem 2rem;
      text-align: center;
      min-width: 300px;
      max-width: 90vw;
      animation: popupIn 0.2s ease-out;
      h2 { margin: 0.5rem 0; font-size: 1.75rem; }
      &.success {
        border: 4px solid #4caf50;
        h2 { color: #2e7d32; }
        .popup-icon { color: #4caf50; }
      }
      &.warning {
        border: 4px solid #ff9800;
        h2 { color: #e65100; }
        .popup-icon { color: #ff9800; }
      }
      &.error {
        border: 4px solid #f44336;
        h2 { color: #c62828; }
        .popup-icon { color: #f44336; }
      }
    }
    .popup-icon {
      font-size: 4rem;
    }
    .popup-message {
      font-size: 1.1rem;
      color: #555;
      margin: 0.5rem 0;
    }
    .popup-ticket-info {
      background: #f5f5f5;
      padding: 0.5rem 1rem;
      border-radius: 8px;
      font-family: monospace;
      font-weight: 600;
      font-size: 1.1rem;
      margin-top: 0.5rem;
    }
    .popup-dismiss {
      margin-top: 1.5rem;
      font-size: 0.85rem;
      color: #999;
    }
    @keyframes popupIn {
      from { transform: scale(0.8); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
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
  scannedTicket: Ticket | null = null;

  events: Event[] = [];
  selectedEventId: number | null = null;

  // Popup state
  showPopup = false;
  popupType: 'success' | 'warning' | 'error' = 'success';
  popupMessage = '';
  private scanCooldown = false;
  private popupTimeout: any;

  scanHistory: { ticketCode: string; rawData: string; valid: boolean; time: string }[] = [];

  private html5Qrcode: Html5Qrcode | null = null;

  constructor(
    public auth: AuthService,
    private http: HttpClient,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadEvents();
  }

  ngOnDestroy() {
    this.stopCamera();
    if (this.popupTimeout) clearTimeout(this.popupTimeout);
  }

  loadEvents() {
    this.http.get<Event[]>('/api/events/published').subscribe({
      next: (events) => this.events = events,
      error: () => {}
    });
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
      this.showResultPopup('error', 'Kan camera niet openen. Controleer de toestemming.');
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
    if (this.scanning || this.scanCooldown) return;
    this.qrCodeData = data;
    this.scanTicket();
  }

  scanTicket() {
    const data = this.qrCodeData.trim();
    if (!data || this.scanCooldown) return;

    if (!this.selectedEventId) {
      this.showResultPopup('error', 'Selecteer eerst een evenement.');
      return;
    }

    this.scanning = true;
    this.scannedTicket = null;

    this.http.post<Ticket>(`/api/orders/scan/${encodeURIComponent(data)}?eventId=${this.selectedEventId}`, {}, {
      headers: { Authorization: `Bearer ${this.auth.token}` }
    }).subscribe({
      next: (ticket) => {
        this.scannedTicket = ticket;
        this.showResultPopup('success', `Ticket ${ticket.ticketCode} is succesvol gescand.`);
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
        const errorMsg = err.error?.error || 'Ticket niet gevonden of ongeldig';
        const isAlreadyScanned = errorMsg.toLowerCase().includes('al gescand');
        this.showResultPopup(
          isAlreadyScanned ? 'warning' : 'error',
          errorMsg
        );
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

  private showResultPopup(type: 'success' | 'warning' | 'error', message: string) {
    this.popupType = type;
    this.popupMessage = message;
    this.showPopup = true;
    this.scanCooldown = true;

    if (this.popupTimeout) clearTimeout(this.popupTimeout);
    // Auto-dismiss after 3 seconds
    this.popupTimeout = setTimeout(() => this.dismissPopup(), 3000);
  }

  dismissPopup() {
    this.showPopup = false;
    if (this.popupTimeout) {
      clearTimeout(this.popupTimeout);
      this.popupTimeout = null;
    }
    // Brief cooldown after dismiss to prevent immediate re-scan
    setTimeout(() => this.scanCooldown = false, 500);
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

  formatEventDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }
}
