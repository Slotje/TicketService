import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { ApiService } from '../../../services/api.service';
import { Customer } from '../../../models/models';
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
  templateUrl: './customer-management.component.html',
  styleUrl: './customer-management.component.scss'
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
  uploadingLogo = false;

  customerForm: Customer = this.emptyForm();

  constructor(private api: ApiService, private http: HttpClient, private confirmService: ConfirmationService) {}

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

  resendInvite(customer: Customer) {
    this.api.resendCustomerInvite(customer.id!).subscribe({
      next: () => this.errorMessage = '',
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij versturen uitnodiging'
    });
  }

  uploadLogo(event: any) {
    const file = event.target?.files?.[0];
    if (!file) return;

    this.uploadingLogo = true;
    const formData = new FormData();
    formData.append('file', file);

    const token = localStorage.getItem('admin_token');
    const headers: any = {};
    if (token) headers['Authorization'] = 'Bearer ' + token;

    this.http.post<{url: string}>('/api/images/upload', formData, { headers }).subscribe({
      next: (res) => {
        this.customerForm.logoUrl = res.url;
        this.uploadingLogo = false;
      },
      error: (err) => {
        this.dialogError = err.error?.error || 'Fout bij uploaden logo';
        this.uploadingLogo = false;
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
