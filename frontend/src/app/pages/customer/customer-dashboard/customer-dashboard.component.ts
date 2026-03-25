import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../services/api.service';
import { CustomerAuthService } from '../../../services/customer-auth.service';
import { Event, TicketCategory, TicketSales } from '../../../models/models';
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
import { Tooltip } from 'primeng/tooltip';
import { ToggleSwitch } from 'primeng/toggleswitch';

@Component({
  selector: 'app-customer-dashboard',
  imports: [
    CommonModule, FormsModule, TableModule, Button, Dialog,
    InputText, InputNumber, Textarea, FloatLabel, Select, DatePicker,
    Tag, Message, ConfirmDialog, ProgressBar, Tooltip, ToggleSwitch
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
  eventCategories: any[] = [];

  uploadingImage = false;
  brandingDialogVisible = false;
  brandingForm: any = {
    logoUrl: '',
    primaryColor: '#0f172a',
    secondaryColor: '#1e293b',
    website: ''
  };
  savingBranding = false;
  brandingError = '';
  brandingSuccess = '';

  colorPresets = [
    '#0f172a', '#1e3a5f', '#1a365d', '#1e40af',
    '#7c3aed', '#be123c', '#9f1239', '#dc2626',
    '#ea580c', '#d97706', '#d4a853', '#16a34a',
    '#059669', '#0d9488', '#0891b2', '#6366f1'
  ];

  constructor(
    private api: ApiService,
    public customerAuth: CustomerAuthService,
    private confirmService: ConfirmationService,
    private http: HttpClient
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
      ticketPrice: 25.00, maxTicketsPerOrder: 10, showAvailability: true,
      imageUrl: '', status: 'DRAFT', customerId: 0
    };
  }

  openNew() {
    this.eventForm = this.emptyForm();
    this.eventDateValue = null;
    this.endDateValue = null;
    this.editMode = false;
    this.editingId = null;
    this.dialogError = '';
    this.eventCategories = [];
    this.dialogVisible = true;
  }

  editEvent(event: Event) {
    this.eventForm = { ...event };
    this.eventDateValue = event.eventDate ? new Date(event.eventDate) : null;
    this.endDateValue = event.endDate ? new Date(event.endDate) : null;
    this.editMode = true;
    this.editingId = event.id!;
    this.dialogError = '';
    this.eventCategories = (event.ticketCategories || []).map(c => ({
      ...c,
      _validDateObj: c.validDate ? new Date(c.validDate + 'T00:00:00') : null,
      _validEndDateObj: c.validEndDate ? new Date(c.validEndDate + 'T00:00:00') : null,
      _startTimeObj: c.startTime ? new Date(c.startTime) : null,
      _endTimeObj: c.endTime ? new Date(c.endTime) : null
    }));
    this.dialogVisible = true;
  }

  addCategory() {
    this.eventCategories.push({
      name: '', description: '', price: 0, serviceFee: null,
      maxTickets: 0, validDate: null, validEndDate: null, startTime: null, endTime: null,
      sortOrder: this.eventCategories.length,
      active: true, _validDateObj: null, _validEndDateObj: null, _startTimeObj: null, _endTimeObj: null
    });
  }

  removeCategory(index: number) {
    const cat = this.eventCategories[index];
    if (cat.id && cat.ticketsSold > 0) {
      this.dialogError = 'Categorie kan niet worden verwijderd: er zijn al tickets verkocht';
      return;
    }
    if (cat.id && this.editingId) {
      this.api.deleteMyTicketCategory(this.editingId, cat.id).subscribe({
        error: (err: any) => this.dialogError = err.error?.error || 'Fout bij verwijderen categorie'
      });
    }
    this.eventCategories.splice(index, 1);
  }

  private formatLocalDateTime(date: Date): string {
    const y = date.getFullYear();
    const mo = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    const h = String(date.getHours()).padStart(2, '0');
    const mi = String(date.getMinutes()).padStart(2, '0');
    const s = String(date.getSeconds()).padStart(2, '0');
    return `${y}-${mo}-${d}T${h}:${mi}:${s}`;
  }

  private formatLocalDate(date: Date): string {
    const y = date.getFullYear();
    const mo = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${mo}-${d}`;
  }

  saveEvent() {
    this.dialogError = '';

    if (this.eventDateValue) {
      this.eventForm.eventDate = this.formatLocalDateTime(this.eventDateValue);
    }
    if (this.endDateValue) {
      this.eventForm.endDate = this.formatLocalDateTime(this.endDateValue);
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
      next: (saved) => {
        const eventId = saved.id!;
        // Save categories (both for new and existing events)
        if (this.eventCategories.length > 0) {
          this.saveCategories(eventId);
        }
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

  private saveCategories(eventId: number) {
    for (let i = 0; i < this.eventCategories.length; i++) {
      const cat = this.eventCategories[i];
      if (!cat.name?.trim()) continue;

      const dto: TicketCategory = {
        name: cat.name,
        description: cat.description,
        price: cat.price || 0,
        serviceFee: cat.serviceFee,
        maxTickets: cat.maxTickets || 0,
        validDate: cat._validDateObj ? this.formatLocalDate(new Date(cat._validDateObj)) : null,
        validEndDate: cat._validEndDateObj ? this.formatLocalDate(new Date(cat._validEndDateObj)) : null,
        startTime: cat._startTimeObj ? this.formatLocalDateTime(new Date(cat._startTimeObj)) : null,
        endTime: cat._endTimeObj ? this.formatLocalDateTime(new Date(cat._endTimeObj)) : null,
        sortOrder: i,
        active: cat.active ?? true
      };

      if (cat.id) {
        this.api.updateMyTicketCategory(eventId, cat.id, dto).subscribe();
      } else {
        this.api.createMyTicketCategory(eventId, dto).subscribe();
      }
    }
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

  getTotalTicketsSold(): number {
    return this.events.reduce((sum, e) => sum + (e.totalSold || e.ticketsSold || 0), 0);
  }

  getEstimatedRevenue(): number {
    return this.events.reduce((sum, e) => sum + ((e.totalSold || e.ticketsSold || 0) * (e.ticketPrice || 0)), 0);
  }

  getStatusSeverity(status: string): 'success' | 'info' | 'warn' | 'danger' {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger'> = {
      DRAFT: 'info', PUBLISHED: 'success', SOLD_OUT: 'warn',
      CANCELLED: 'danger', COMPLETED: 'info'
    };
    return map[status] ?? 'info';
  }

  uploadImage(event: any) {
    const file = event.target?.files?.[0] || event;
    if (!file) return;

    this.uploadingImage = true;
    const formData = new FormData();
    formData.append('file', file);

    const token = localStorage.getItem('customer_token');
    const headers: any = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;

    this.http.post<{url: string}>('/api/images/upload', formData, { headers }).subscribe({
      next: (res) => {
        this.eventForm.imageUrl = res.url;
        this.uploadingImage = false;
      },
      error: (err) => {
        this.dialogError = err.error?.error || 'Fout bij uploaden afbeelding';
        this.uploadingImage = false;
      }
    });
  }

  openBranding() {
    this.brandingDialogVisible = true;
    this.brandingError = '';
    this.brandingSuccess = '';
    // Load current branding from localStorage or API
    const customerId = localStorage.getItem('customer_id');
    if (customerId) {
      this.http.get<any>('/api/customers/slug/' + (localStorage.getItem('customer_slug') || '')).subscribe({
        next: (customer) => {
          this.brandingForm = {
            logoUrl: customer.logoUrl || '',
            primaryColor: customer.primaryColor || '#0f172a',
            secondaryColor: customer.secondaryColor || '#1e293b',
            website: customer.website || ''
          };
        },
        error: () => {} // Ignore, use defaults
      });
    }
  }

  uploadLogo(event: any) {
    const file = event.target?.files?.[0];
    if (!file) return;

    const formData = new FormData();
    formData.append('file', file);

    const token = localStorage.getItem('customer_token');
    const headers: any = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;

    this.http.post<{url: string}>('/api/images/upload', formData, { headers }).subscribe({
      next: (res) => {
        this.brandingForm.logoUrl = res.url;
      },
      error: (err) => {
        this.brandingError = err.error?.error || 'Fout bij uploaden logo';
      }
    });
  }

  saveBranding() {
    this.savingBranding = true;
    this.brandingError = '';

    const token = localStorage.getItem('customer_token');
    const headers: any = { 'Content-Type': 'application/json' };
    if (token) headers['Authorization'] = 'Bearer ' + token;

    this.http.put<any>('/api/customer/auth/branding', this.brandingForm, { headers }).subscribe({
      next: () => {
        this.brandingSuccess = 'Branding opgeslagen!';
        this.savingBranding = false;
        setTimeout(() => this.brandingSuccess = '', 3000);
      },
      error: (err) => {
        this.brandingError = err.error?.error || 'Fout bij opslaan branding';
        this.savingBranding = false;
      }
    });
  }

  selectColor(color: string, target: 'primary' | 'secondary') {
    if (target === 'primary') this.brandingForm.primaryColor = color;
    else this.brandingForm.secondaryColor = color;
  }

  downloadPreviewTicket() {
    const token = localStorage.getItem('customer_token');
    const headers: any = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;

    this.http.get('/api/customer/auth/branding/preview-ticket', {
      headers, responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'voorbeeld-ticket.pdf';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.brandingError = 'Fout bij downloaden voorbeeld ticket';
      }
    });
  }
}
