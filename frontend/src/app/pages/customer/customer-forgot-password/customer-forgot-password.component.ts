import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-customer-forgot-password',
  imports: [CommonModule, FormsModule, RouterLink, Card, Button, InputText, FloatLabel, Message],
  templateUrl: './customer-forgot-password.component.html',
  styleUrl: './customer-forgot-password.component.scss'
})
export class CustomerForgotPasswordComponent {
  email = '';
  loading = false;
  sent = false;
  successMessage = '';
  errorMessage = '';

  constructor(private http: HttpClient) {}

  submit() {
    if (!this.email) {
      this.errorMessage = 'Vul je e-mailadres in.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.http.post<{ message: string }>('/api/customer/auth/forgot-password', { email: this.email }).subscribe({
      next: (res) => {
        this.successMessage = res.message;
        this.loading = false;
        this.sent = true;
      },
      error: () => {
        this.errorMessage = 'Er is een fout opgetreden. Probeer het later opnieuw.';
        this.loading = false;
      }
    });
  }
}
