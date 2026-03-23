import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Ticket, Event } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';

@Component({
  selector: 'app-ticket-scanner',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Select],
  templateUrl: './ticket-scanner.component.html',
  styleUrl: './ticket-scanner.component.scss'
})
export class TicketScannerComponent implements OnInit {
  qrCodeData = '';
  scanning = false;
  successMessage = '';
  errorMessage = '';
  scannedTicket: Ticket | null = null;
  events: Event[] = [];
  selectedEventId: number | null = null;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getEvents().subscribe({
      next: (events) => this.events = events,
      error: () => {}
    });
  }

  scanTicket() {
    if (!this.qrCodeData.trim()) return;

    if (!this.selectedEventId) {
      this.errorMessage = 'Selecteer eerst een evenement.';
      return;
    }

    this.scanning = true;
    this.successMessage = '';
    this.errorMessage = '';
    this.scannedTicket = null;

    this.api.scanTicket(this.qrCodeData.trim(), this.selectedEventId).subscribe({
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
