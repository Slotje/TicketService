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
  template: `
    <div class="forgot-page">
      <p-card>
        <ng-template #header>
          <div class="forgot-header">
            <i class="pi pi-key" style="font-size: 2.5rem; color: var(--p-primary-color)"></i>
            <h2>Wachtwoord vergeten</h2>
            <p>Vul je e-mailadres in om een resetlink te ontvangen.</p>
          </div>
        </ng-template>

        @if (successMessage) {
          <p-message severity="success" [text]="successMessage" [style]="{'width': '100%'}" class="mb-2" />
        }
        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        <div class="form-stack">
          <p-floatlabel>
            <input pInputText id="email" [(ngModel)]="email" type="email" class="w-full" (keyup.enter)="submit()" />
            <label for="email">E-mailadres</label>
          </p-floatlabel>

          <p-button label="Verstuur resetlink" icon="pi pi-envelope" (onClick)="submit()"
                    [loading]="loading" [disabled]="sent" styleClass="w-full" />

          <div class="back-link">
            <a routerLink="/klant/login" class="link"><i class="pi pi-arrow-left"></i> Terug naar inloggen</a>
          </div>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .forgot-page {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 60vh;
    }
    :host ::ng-deep .p-card {
      width: 400px;
      max-width: 95vw;
    }
    .forgot-header {
      text-align: center;
      padding: 2rem 1rem 1rem;
      h2 { color: var(--p-primary-color); margin: 0.5rem 0 0.25rem; }
      p { color: var(--p-text-muted-color); margin: 0; font-size: 0.9rem; }
    }
    .form-stack {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .back-link {
      text-align: center;
      font-size: 0.9rem;
    }
    .link {
      color: var(--p-primary-color);
      cursor: pointer;
      text-decoration: none;
    }
    .link:hover {
      text-decoration: underline;
    }
  `]
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
