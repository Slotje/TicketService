import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { CustomerAuthService } from '../../services/customer-auth.service';
import { Event } from '../../models/models';
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

@Component({
  selector: 'app-customer-dashboard',
  imports: [
    CommonModule, FormsModule, TableModule, Button, Dialog,
    InputText, InputNumber, Textarea, FloatLabel, Select, DatePicker,
    Tag, Message, ConfirmDialog
  ],
  providers: [ConfirmationService],
  template: `
    <div class="dashboard-header mb-2">
      <div>
        <h1>Mijn Evenementen</h1>
        <p class="subtitle">{{ customerAuth.companyName }}</p>
      </div>
      <div class="flex gap-1">
        <p-button label="Nieuw Evenement" icon="pi pi-plus" (onClick)="openNew()" />
        <p-button label="Uitloggen" icon="pi pi-sign-out" [text]="true" severity="secondary" (onClick)="customerAuth.logout()" />
      </div>
    </div>

    @if (errorMessage) {
      <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
    }
    @if (successMessage) {
      <p-message severity="success" [text]="successMessage" [style]="{'width': '100%'}" class="mb-2" />
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
          <td data-label="Naam">{{ event.name }}</td>
          <td data-label="Datum">{{ formatDate(event.eventDate) }}</td>
          <td data-label="Locatie">{{ event.location }}</td>
          <td data-label="Tickets">{{ event.ticketsSold }}/{{ event.maxTickets }}</td>
          <td data-label="Prijs">&euro; {{ event.ticketPrice | number:'1.2-2' }}</td>
          <td data-label="Status">
            <p-tag [value]="getStatusLabel(event)" [severity]="getStatusSeverity(event.status)" />
          </td>
          <td data-label="Acties">
            <div class="flex gap-1">
              <p-button icon="pi pi-pencil" [rounded]="true" [text]="true" (onClick)="editEvent(event)" pTooltip="Bewerken" />
              @if (event.status === 'DRAFT') {
                <p-button icon="pi pi-play" [rounded]="true" [text]="true" severity="success" (onClick)="publishEvent(event)" pTooltip="Live zetten" />
              }
              @if (event.status === 'PUBLISHED') {
                <p-button icon="pi pi-pause" [rounded]="true" [text]="true" severity="warn" (onClick)="unpublishEvent(event)" pTooltip="Offline halen" />
              }
              <p-button icon="pi pi-trash" [rounded]="true" [text]="true" severity="danger" (onClick)="deleteEvent(event)" pTooltip="Verwijderen" />
            </div>
          </td>
        </tr>
      </ng-template>
      <ng-template #emptymessage>
        <tr>
          <td colspan="7" class="text-center" style="padding: 2rem;">
            <i class="pi pi-calendar" style="font-size: 2rem; color: var(--p-text-muted-color)"></i>
            <p>Je hebt nog geen evenementen. Maak je eerste evenement aan!</p>
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
    .dashboard-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 1rem;
      h1 { color: var(--p-primary-color); margin: 0; }
      .subtitle { color: var(--p-text-muted-color); margin: 0; font-size: 0.9rem; }
    }
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
