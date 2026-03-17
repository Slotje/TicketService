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
  template: `
    <div class="flex justify-between align-center mb-2">
      <h1>Evenementenbeheer</h1>
      <p-button label="Nieuw Evenement" icon="pi pi-plus" (onClick)="openNew()" />
    </div>

    @if (errorMessage) {
      <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
    }

    <p-table
      [value]="events"
      [paginator]="true"
      [rows]="10"
      [loading]="loading"
      styleClass="p-datatable-striped">
      <ng-template #header>
        <tr>
          <th pSortableColumn="name">Naam</th>
          <th pSortableColumn="customerName">Klant</th>
          <th>Datum</th>
          <th>Locatie</th>
          <th>Tickets</th>
          <th>Prijs</th>
          <th>Status</th>
          <th>Acties</th>
        </tr>
      </ng-template>
      <ng-template #body let-event>
        <tr>
          <td>{{ event.name }}</td>
          <td>{{ event.customerName }}</td>
          <td>{{ formatDate(event.eventDate) }}</td>
          <td>{{ event.location }}</td>
          <td>
            <span>{{ event.ticketsSold }}/{{ event.maxTickets }}</span>
            <p-progressBar [value]="(event.ticketsSold / event.maxTickets) * 100" [showValue]="false" [style]="{'height': '6px', 'margin-top': '4px'}" />
          </td>
          <td>{{ event.ticketPrice | number:'1.2-2' }}</td>
          <td>
            <p-tag [value]="getStatusLabel(event)" [severity]="getStatusSeverity(event.status)" />
            @if (isPastEvent(event)) {
              <p-tag value="Verlopen" severity="secondary" class="ml-1" />
            }
          </td>
          <td>
            <div class="flex gap-1">
              <p-button icon="pi pi-pencil" [rounded]="true" [text]="true" (onClick)="editEvent(event)" />
              <p-button icon="pi pi-list" [rounded]="true" [text]="true" severity="info" [routerLink]="'/admin/orders/' + event.id" pTooltip="Bestellingen" />
              @if (event.status === 'DRAFT') {
                <p-button icon="pi pi-play" [rounded]="true" [text]="true" severity="success" (onClick)="publishEvent(event)" />
              }
              <p-button icon="pi pi-trash" [rounded]="true" [text]="true" severity="danger" (onClick)="deleteEvent(event)" />
            </div>
          </td>
        </tr>
      </ng-template>
    </p-table>

    <p-dialog
      [(visible)]="dialogVisible"
      [header]="editMode ? 'Evenement Bewerken' : 'Nieuw Evenement'"
      [modal]="true"
      [style]="{'width': '700px'}"
      [closable]="true">

      @if (dialogError) {
        <p-message severity="error" [text]="dialogError" [style]="{'width': '100%'}" class="mb-2" />
      }

      <div class="form-grid">
        <div class="form-field span-2">
          <p-floatlabel>
            <input pInputText id="eventName" [(ngModel)]="eventForm.name" class="w-full" />
            <label for="eventName">Naam *</label>
          </p-floatlabel>
        </div>
        <div class="form-field span-2">
          <p-floatlabel>
            <textarea pTextarea id="description" [(ngModel)]="eventForm.description" [rows]="3" class="w-full"></textarea>
            <label for="description">Beschrijving</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <label>Klant *</label>
          <p-select
            [options]="customerOptions"
            [(ngModel)]="eventForm.customerId"
            optionLabel="label"
            optionValue="value"
            placeholder="Selecteer klant"
            class="w-full" />
        </div>
        <div class="form-field">
          <label>Status</label>
          <p-select
            [options]="statusOptions"
            [(ngModel)]="eventForm.status"
            optionLabel="label"
            optionValue="value"
            class="w-full" />
        </div>
        <div class="form-field">
          <label>Startdatum *</label>
          <p-datePicker [(ngModel)]="eventDateValue" [showTime]="true" dateFormat="dd-mm-yy" class="w-full" />
        </div>
        <div class="form-field">
          <label>Einddatum</label>
          <p-datePicker [(ngModel)]="endDateValue" [showTime]="true" dateFormat="dd-mm-yy" class="w-full" />
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="location" [(ngModel)]="eventForm.location" class="w-full" />
            <label for="location">Locatie *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="address" [(ngModel)]="eventForm.address" class="w-full" />
            <label for="address">Adres</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <label>Max Tickets *</label>
          <p-inputNumber [(ngModel)]="eventForm.maxTickets" [min]="1" [max]="100000" class="w-full" />
        </div>
        <div class="form-field">
          <label>Ticketprijs (EUR) *</label>
          <p-inputNumber [(ngModel)]="eventForm.ticketPrice" [min]="0" [minFractionDigits]="2" [maxFractionDigits]="2" mode="currency" currency="EUR" locale="nl-NL" class="w-full" />
        </div>
        <div class="form-field">
          <label>Max per bestelling</label>
          <p-inputNumber [(ngModel)]="eventForm.maxTicketsPerOrder" [min]="1" [max]="10" [showButtons]="true" class="w-full" />
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="imageUrl" [(ngModel)]="eventForm.imageUrl" class="w-full" />
            <label for="imageUrl">Afbeelding URL</label>
          </p-floatlabel>
        </div>
      </div>

      <ng-template #footer>
        <p-button label="Annuleren" icon="pi pi-times" (onClick)="dialogVisible = false" [text]="true" />
        <p-button label="Opslaan" icon="pi pi-check" (onClick)="saveEvent()" [loading]="saving" />
      </ng-template>
    </p-dialog>

    <p-confirmDialog />
  `,
  styles: [`
    h1 { color: var(--p-primary-color); margin: 0; }
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
    }
    .span-2 { grid-column: span 2; }
    .form-field label {
      display: block;
      margin-bottom: 0.5rem;
      font-size: 0.9rem;
      color: var(--p-text-muted-color);
    }
    @media (max-width: 600px) {
      .form-grid { grid-template-columns: 1fr; }
      .span-2 { grid-column: span 1; }
    }
  `]
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
