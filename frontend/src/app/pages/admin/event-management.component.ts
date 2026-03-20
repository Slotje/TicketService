import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Customer, Event } from '../../models/models';
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
import { ConfirmationService } from 'primeng/api';
import { ProgressBar } from 'primeng/progressbar';

@Component({
  selector: 'app-event-management',
  imports: [
    CommonModule, FormsModule, RouterLink, TableModule, Button, Dialog,
    InputText, InputNumber, Textarea, FloatLabel, Select, DatePicker,
    Tag, Message, ConfirmDialog, ProgressBar
  ],
  providers: [ConfirmationService],
  templateUrl: './event-management.component.html',
  styleUrl: './event-management.component.scss'
})
export class EventManagementComponent implements OnInit {
  events: Event[] = [];
  customers: Customer[] = [];
  loading = true;
  dialogVisible = false;
  editMode = false;
  saving = false;
  errorMessage = '';
  dialogError = '';
  editingId: number | null = null;
  eventDateValue: Date | null = null;
  endDateValue: Date | null = null;

  eventForm: Event = this.emptyForm();

  customerOptions: { label: string; value: number }[] = [];
  statusOptions = [
    { label: 'Concept', value: 'DRAFT' },
    { label: 'Gepubliceerd', value: 'PUBLISHED' },
    { label: 'Geannuleerd', value: 'CANCELLED' }
  ];

  constructor(private api: ApiService, private confirmService: ConfirmationService) {}

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    this.loading = true;
    this.api.getEvents().subscribe({
      next: (e) => { this.events = e; this.loading = false; },
      error: () => this.loading = false
    });
    this.api.getCustomers().subscribe({
      next: (c) => {
        this.customers = c;
        this.customerOptions = c.map(cu => ({ label: cu.companyName, value: cu.id! }));
      }
    });
  }

  emptyForm(): Event {
    return {
      name: '', description: '', eventDate: '', endDate: '',
      location: '', address: '', maxTickets: 100, ticketPrice: 25.00,
      maxTicketsPerOrder: 10, imageUrl: '', status: 'DRAFT', customerId: 0
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

    if (!this.eventForm.name || !this.eventForm.location || !this.eventForm.customerId || !this.eventForm.eventDate) {
      this.dialogError = 'Vul alle verplichte velden in';
      return;
    }

    this.saving = true;
    const obs = this.editMode
      ? this.api.updateEvent(this.editingId!, this.eventForm)
      : this.api.createEvent(this.eventForm);

    obs.subscribe({
      next: () => {
        this.dialogVisible = false;
        this.saving = false;
        this.loadData();
      },
      error: (err) => {
        this.dialogError = err.error?.error || err.error?.errors
          ? JSON.stringify(err.error.errors)
          : 'Fout bij opslaan';
        this.saving = false;
      }
    });
  }

  publishEvent(event: Event) {
    this.api.updateEventStatus(event.id!, 'PUBLISHED').subscribe({
      next: () => this.loadData(),
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij publiceren'
    });
  }

  deleteEvent(event: Event) {
    this.confirmService.confirm({
      message: `Weet je zeker dat je "${event.name}" wilt verwijderen?`,
      header: 'Bevestiging',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.deleteEvent(event.id!).subscribe({
          next: () => this.loadData(),
          error: (err) => this.errorMessage = err.error?.error || 'Fout bij verwijderen'
        });
      }
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
      DRAFT: 'Concept', PUBLISHED: 'Gepubliceerd', SOLD_OUT: 'Uitverkocht',
      CANCELLED: 'Geannuleerd', COMPLETED: 'Afgerond'
    };
    return labels[event.status ?? ''] ?? event.status ?? '';
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger'> = {
      DRAFT: 'info', PUBLISHED: 'success', SOLD_OUT: 'warn',
      CANCELLED: 'danger', COMPLETED: 'info'
    };
    return map[status] ?? 'info';
  }

  isPastEvent(event: Event): boolean {
    return !!event.eventDate && new Date(event.eventDate) < new Date();
  }
}
