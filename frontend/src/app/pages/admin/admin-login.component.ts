import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AdminAuthService } from '../../services/admin-auth.service';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';

@Component({
  selector: 'app-admin-login',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Password],
  template: `
    <div class="login-page">
      <p-card>
        <ng-template #header>
          <div class="login-header">
            <i class="pi pi-lock"></i>
          </div>
        </ng-template>
        <ng-template #title>Beheer Login</ng-template>
        <ng-template #subtitle>Log in om het beheerpaneel te openen</ng-template>

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        <div class="login-form">
          <div class="form-field">
            <p-floatlabel>
              <input pInputText id="email" [(ngModel)]="email" type="email" class="w-full"
                     (keyup.enter)="login()" autocomplete="email" />
              <label for="email">E-mailadres</label>
            </p-floatlabel>
          </div>
          <div class="form-field">
            <p-floatlabel>
              <p-password id="password" [(ngModel)]="password" [toggleMask]="true"
                          [feedback]="false" styleClass="w-full" inputStyleClass="w-full"
                          (keyup.enter)="login()" autocomplete="current-password" />
              <label for="password">Wachtwoord</label>
            </p-floatlabel>
          </div>
          <p-button label="Inloggen" icon="pi pi-sign-in" (onClick)="login()"
                    [loading]="loading" [style]="{'width': '100%'}" />
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .login-page {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 60vh;
    }
    .login-page p-card {
      width: 100%;
      max-width: 400px;
    }
    .login-header {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 100px;
      background: var(--p-primary-100);
      border-radius: var(--p-border-radius) var(--p-border-radius) 0 0;
      i {
        font-size: 3rem;
        color: var(--p-primary-color);
      }
    }
    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      margin-top: 1rem;
    }
  `]
})
export class AdminLoginComponent {
  email = '';
  password = '';
  loading = false;
  errorMessage = '';

  constructor(private adminAuth: AdminAuthService, private router: Router) {
    if (adminAuth.isLoggedIn) {
      router.navigate(['/admin']);
    }
  }

  login() {
    if (!this.email || !this.password) {
      this.errorMessage = 'Vul e-mailadres en wachtwoord in';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.adminAuth.login(this.email, this.password).subscribe({
      next: () => {
        this.router.navigate(['/admin']);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Inloggen mislukt';
        this.loading = false;
      }
    });
  }
}
