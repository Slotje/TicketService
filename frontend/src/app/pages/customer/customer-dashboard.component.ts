import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { CustomerAuthService } from '../../services/customer-auth.service';
import { Event, TicketSales } from '../../models/models';
import { TableModule } from 'primeng/table';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { Textarea } from 'primeng/textarea';
import { FloatLabel } from 'primeng/floatlabel';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { Tag } from 'primeng/tag';
import { Message } from 'primeng/message';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ProgressBar } from 'primeng/progressbar';
import { ConfirmationService } from 'primeng/api';

@Component({
  selector: 'app-customer-dashboard',
  imports: [
    CommonModule, FormsModule, TableModule, Button, Dialog,
    InputText, InputNumber, Textarea, FloatLabel, Select, DatePicker,
    Tag, Message, ConfirmDialog, ProgressBar
  ],
  providers: [ConfirmationService],
  templateUrl: './customer-dashboard.component.html',
  styleUrl: './customer-dashboard.component.scss'
})
export class CustomerDashboardComponent implements OnInit {
  events: Event[] = [];
  loading = true;
  dialogVisible = false;
  editMode = false;
  saving = false;
  errorMessage = '';
  successMessage = '';
  dialogError = '';
  editingId: number | null = null;
  eventDateValue: Date | null = null;
  endDateValue: Date | null = null;

  salesDialogVisible = false;
  salesData: TicketSales | null = null;
  salesLoading = false;
  physicalSellQuantity = 1;

  eventForm: Event = this.emptyForm();

  constructor(
    private api: ApiService,
    public customerAuth: CustomerAuthService,
    private confirmService: ConfirmationService
  ) {}

  ngOnInit() {
    this.loadEvents();
  }

  loadEvents() {
    this.loading = true;
    this.api.getMyEvents().subscribe({
      next: (e) => { this.events = e; this.loading = false; },
      error: () => this.loading = false
    });
  }

  emptyForm(): Event {
    return {
      name: '', description: '', eventDate: '', endDate: '',
      location: '', address: '', maxTickets: 100, physicalTickets: 0,
      ticketPrice: 25.00, maxTicketsPerOrder: 10, imageUrl: '',
      status: 'DRAFT', customerId: 0
    };
  }

  openNew() {
    this.eventForm = this.emptyForm();
    this.eventDateValue = null;
    this.endDateValue = null;
    this.editMode = false;
    this.editingId = null;
    this.dialogError = '';
    this.dialogVisible = true;
  }

  editEvent(event: Event) {
    this.eventForm = { ...event };
    this.eventDateValue = event.eventDate ? new Date(event.eventDate) : null;
    this.endDateValue = event.endDate ? new Date(event.endDate) : null;
    this.editMode = true;
    this.editingId = event.id!;
    this.dialogError = '';
    this.dialogVisible = true;
  }

  saveEvent() {
    this.dialogError = '';

    if (this.eventDateValue) {
      this.eventForm.eventDate = this.eventDateValue.toISOString().slice(0, 19);
    }
    if (this.endDateValue) {
      this.eventForm.endDate = this.endDateValue.toISOString().slice(0, 19);
    }

    if (!this.eventForm.name || !this.eventForm.location || !this.eventForm.eventDate) {
      this.dialogError = 'Vul alle verplichte velden in';
      return;
    }

    this.saving = true;
    const obs = this.editMode
      ? this.api.updateMyEvent(this.editingId!, this.eventForm)
      : this.api.createMyEvent(this.eventForm);

    obs.subscribe({
      next: () => {
        this.dialogVisible = false;
        this.saving = false;
        this.successMessage = this.editMode ? 'Evenement bijgewerkt' : 'Evenement aangemaakt';
        this.loadEvents();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => {
        this.dialogError = err.error?.error || 'Fout bij opslaan';
        this.saving = false;
      }
    });
  }

  publishEvent(event: Event) {
    this.api.updateMyEventStatus(event.id!, 'PUBLISHED').subscribe({
      next: () => {
        this.successMessage = `"${event.name}" is nu live!`;
        this.loadEvents();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij publiceren'
    });
  }

  unpublishEvent(event: Event) {
    this.api.updateMyEventStatus(event.id!, 'DRAFT').subscribe({
      next: () => {
        this.successMessage = `"${event.name}" is offline gehaald`;
        this.loadEvents();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij offline halen'
    });
  }

  deleteEvent(event: Event) {
    this.confirmService.confirm({
      message: `Weet je zeker dat je "${event.name}" wilt verwijderen?`,
      header: 'Bevestiging',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.deleteMyEvent(event.id!).subscribe({
          next: () => this.loadEvents(),
          error: (err) => this.errorMessage = err.error?.error || 'Fout bij verwijderen'
        });
      }
    });
  }

  generatePhysicalTickets(event: Event) {
    this.api.generateMyPhysicalTickets(event.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `fysieke-tickets-${event.name}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
        this.successMessage = 'Fysieke tickets gegenereerd en verzonden per e-mail';
        this.loadEvents();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij genereren fysieke tickets'
    });
  }

  downloadPhysicalTickets(event: Event) {
    this.api.downloadMyPhysicalTicketsPdf(event.id!).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `fysieke-tickets-${event.name}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij downloaden'
    });
  }

  openSalesDialog(event: Event) {
    this.salesDialogVisible = true;
    this.salesLoading = true;
    this.salesData = null;
    this.physicalSellQuantity = 1;
    this.api.getMyTicketSales(event.id!).subscribe({
      next: (data) => { this.salesData = data; this.salesLoading = false; },
      error: () => this.salesLoading = false
    });
  }

  markPhysicalSold() {
    if (!this.salesData) return;
    this.api.markMyPhysicalTicketsSold(this.salesData.eventId, this.physicalSellQuantity).subscribe({
      next: () => {
        this.openSalesDialog({ id: this.salesData!.eventId } as Event);
        this.loadEvents();
      },
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij registreren verkoop'
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getStatusLabel(event: Event): string {
    const labels: Record<string, string> = {
      DRAFT: 'Concept', PUBLISHED: 'Live', SOLD_OUT: 'Uitverkocht',
      CANCELLED: 'Geannuleerd', COMPLETED: 'Afgerond'
    };
    return labels[event.status ?? ''] ?? event.status ?? '';
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger'> = {
      DRAFT: 'info', PUBLISHED: 'success', SOLD_OUT: 'warn',
      CANCELLED: 'danger', COMPLETED: 'info'
    };
    return map[status] ?? 'info';
  }
}
