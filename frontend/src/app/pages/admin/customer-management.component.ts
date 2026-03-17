import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Customer } from '../../models/models';
import { TableModule } from 'primeng/table';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { ColorPicker } from 'primeng/colorpicker';
import { ToggleSwitch } from 'primeng/toggleswitch';
import { Message } from 'primeng/message';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { Tag } from 'primeng/tag';

@Component({
  selector: 'app-customer-management',
  imports: [
    CommonModule, FormsModule, TableModule, Button, Dialog, InputText,
    FloatLabel, ColorPicker, ToggleSwitch, Message, ConfirmDialog, Tag
  ],
  providers: [ConfirmationService],
  template: `
    <div class="flex justify-between align-center mb-2">
      <h1>Klantenbeheer</h1>
      <p-button label="Nieuwe Klant" icon="pi pi-plus" (onClick)="openNew()" />
    </div>

    @if (errorMessage) {
      <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
    }

    <p-table
      [value]="customers"
      [paginator]="true"
      [rows]="10"
      [loading]="loading"
      [globalFilterFields]="['companyName', 'email', 'contactPerson']"
      styleClass="p-datatable-striped">
      <ng-template #header>
        <tr>
          <th pSortableColumn="companyName">Bedrijfsnaam</th>
          <th pSortableColumn="contactPerson">Contactpersoon</th>
          <th pSortableColumn="email">E-mail</th>
          <th>Kleuren</th>
          <th>Status</th>
          <th>Acties</th>
        </tr>
      </ng-template>
      <ng-template #body let-customer>
        <tr>
          <td data-label="Bedrijfsnaam">{{ customer.companyName }}</td>
          <td data-label="Contactpersoon">{{ customer.contactPerson }}</td>
          <td data-label="E-mail">{{ customer.email }}</td>
          <td data-label="Kleuren">
            <div class="flex gap-1">
              @if (customer.primaryColor) {
                <span class="color-swatch" [style.background-color]="customer.primaryColor"></span>
              }
              @if (customer.secondaryColor) {
                <span class="color-swatch" [style.background-color]="customer.secondaryColor"></span>
              }
            </div>
          </td>
          <td data-label="Status">
            <p-tag [value]="customer.active ? 'Actief' : 'Inactief'" [severity]="customer.active ? 'success' : 'danger'" />
          </td>
          <td data-label="Acties">
            <div class="flex gap-1">
              <p-button icon="pi pi-pencil" [rounded]="true" [text]="true" (onClick)="editCustomer(customer)" />
              <p-button icon="pi pi-trash" [rounded]="true" [text]="true" severity="danger" (onClick)="deleteCustomer(customer)" />
            </div>
          </td>
        </tr>
      </ng-template>
    </p-table>

    <p-dialog
      [(visible)]="dialogVisible"
      [header]="editMode ? 'Klant Bewerken' : 'Nieuwe Klant'"
      [modal]="true"
      [style]="{'width': '600px'}"
      [closable]="true">

      @if (dialogError) {
        <p-message severity="error" [text]="dialogError" [style]="{'width': '100%'}" class="mb-2" />
      }

      <div class="form-grid">
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="companyName" [(ngModel)]="customerForm.companyName" class="w-full" />
            <label for="companyName">Bedrijfsnaam *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="contactPerson" [(ngModel)]="customerForm.contactPerson" class="w-full" />
            <label for="contactPerson">Contactpersoon *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="email" [(ngModel)]="customerForm.email" type="email" class="w-full" />
            <label for="email">E-mail *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="phone" [(ngModel)]="customerForm.phone" class="w-full" />
            <label for="phone">Telefoon</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="website" [(ngModel)]="customerForm.website" class="w-full" />
            <label for="website">Website</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="logoUrl" [(ngModel)]="customerForm.logoUrl" class="w-full" />
            <label for="logoUrl">Logo URL</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <label>Primaire Kleur</label>
          <div class="flex align-center gap-1">
            <p-colorPicker [(ngModel)]="customerForm.primaryColor" />
            <input pInputText [(ngModel)]="customerForm.primaryColor" style="width: 100px" />
          </div>
        </div>
        <div class="form-field">
          <label>Secundaire Kleur</label>
          <div class="flex align-center gap-1">
            <p-colorPicker [(ngModel)]="customerForm.secondaryColor" />
            <input pInputText [(ngModel)]="customerForm.secondaryColor" style="width: 100px" />
          </div>
        </div>
        <div class="form-field">
          <label>Actief</label>
          <p-toggleSwitch [(ngModel)]="customerForm.active" />
        </div>
      </div>

      <ng-template #footer>
        <p-button label="Annuleren" icon="pi pi-times" (onClick)="dialogVisible = false" [text]="true" />
        <p-button label="Opslaan" icon="pi pi-check" (onClick)="saveCustomer()" [loading]="saving" />
      </ng-template>
    </p-dialog>

    <p-confirmDialog />
  `,
  styles: [`
    h1 { color: var(--p-primary-color); margin: 0; }
    .color-swatch {
      width: 24px; height: 24px;
      border-radius: 4px;
      border: 1px solid var(--p-surface-300);
      display: inline-block;
    }
    .form-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
      @media (max-width: 500px) { grid-template-columns: 1fr; }
    }
    .form-field label {
      display: block;
      margin-bottom: 0.5rem;
      font-size: 0.9rem;
      color: var(--p-text-muted-color);
    }
  `]
})
export class CustomerManagementComponent implements OnInit {
  customers: Customer[] = [];
  loading = true;
  dialogVisible = false;
  editMode = false;
  saving = false;
  errorMessage = '';
  dialogError = '';
  editingId: number | null = null;

  customerForm: Customer = this.emptyForm();

  constructor(private api: ApiService, private confirmService: ConfirmationService) {}

  ngOnInit() {
    this.loadCustomers();
  }

  loadCustomers() {
    this.loading = true;
    this.api.getCustomers().subscribe({
      next: (c) => { this.customers = c; this.loading = false; },
      error: () => this.loading = false
    });
  }

  emptyForm(): Customer {
    return {
      companyName: '', contactPerson: '', email: '', phone: '',
      logoUrl: '', primaryColor: '#2196F3', secondaryColor: '#607D8B',
      website: '', active: true
    };
  }

  openNew() {
    this.customerForm = this.emptyForm();
    this.editMode = false;
    this.editingId = null;
    this.dialogError = '';
    this.dialogVisible = true;
  }

  editCustomer(customer: Customer) {
    this.customerForm = { ...customer };
    this.editMode = true;
    this.editingId = customer.id!;
    this.dialogError = '';
    this.dialogVisible = true;
  }

  saveCustomer() {
    this.dialogError = '';
    if (!this.customerForm.companyName || !this.customerForm.contactPerson || !this.customerForm.email) {
      this.dialogError = 'Vul alle verplichte velden in';
      return;
    }

    this.saving = true;
    const obs = this.editMode
      ? this.api.updateCustomer(this.editingId!, this.customerForm)
      : this.api.createCustomer(this.customerForm);

    obs.subscribe({
      next: () => {
        this.dialogVisible = false;
        this.saving = false;
        this.loadCustomers();
      },
      error: (err) => {
        this.dialogError = err.error?.error || 'Fout bij opslaan';
        this.saving = false;
      }
    });
  }

  deleteCustomer(customer: Customer) {
    this.confirmService.confirm({
      message: `Weet je zeker dat je "${customer.companyName}" wilt verwijderen?`,
      header: 'Bevestiging',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.api.deleteCustomer(customer.id!).subscribe({
          next: () => this.loadCustomers(),
          error: (err) => this.errorMessage = err.error?.error || 'Fout bij verwijderen'
        });
      }
    });
  }
}
