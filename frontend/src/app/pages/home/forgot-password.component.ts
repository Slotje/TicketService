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
  selector: 'app-forgot-password',
  imports: [CommonModule, FormsModule, RouterLink, Card, Button, InputText, FloatLabel, Message],
  template: `
    <div class="forgot-page">
      <p-card>
        <ng-template #title>
          <div class="flex align-center gap-1">
            <i class="pi pi-key"></i>
            <span>Wachtwoord vergeten</span>
          </div>
        </ng-template>
        <ng-template #subtitle>
          Vul je e-mailadres in en we sturen je een link om je wachtwoord te herstellen.
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
            <a routerLink="/login" class="link"><i class="pi pi-arrow-left"></i> Terug naar inloggen</a>
          </div>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .forgot-page {
      max-width: 450px;
      margin: 2rem auto;
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
export class ForgotPasswordComponent {
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
    this.http.post<{ message: string }>('/api/user/auth/forgot-password', { email: this.email }).subscribe({
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
