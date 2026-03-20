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
  templateUrl: './ticket-scanner-camera.component.html',
  styleUrl: './ticket-scanner-camera.component.scss'
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
