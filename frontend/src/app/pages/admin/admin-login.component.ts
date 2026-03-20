import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
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
            <i [class]="isSetup ? 'pi pi-user-plus' : 'pi pi-lock'"></i>
          </div>
        </ng-template>
        <ng-template #title>{{ isSetup ? 'Admin Account Aanmaken' : 'Beheer Login' }}</ng-template>
        <ng-template #subtitle>{{ isSetup ? 'Maak het eerste admin account aan.' : 'Log in om het beheerpaneel te openen' }}</ng-template>

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        <div class="login-form">
          @if (isSetup) {
            <div class="form-field">
              <p-floatlabel>
                <input pInputText id="name" [(ngModel)]="name" class="w-full"
                       (keyup.enter)="submit()" />
                <label for="name">Naam</label>
              </p-floatlabel>
            </div>
          }

          <div class="form-field">
            <p-floatlabel>
              <input pInputText id="email" [(ngModel)]="email" type="email" class="w-full"
                     (keyup.enter)="submit()" autocomplete="email" />
              <label for="email">E-mailadres</label>
            </p-floatlabel>
          </div>
          <div class="form-field">
            <p-floatlabel>
              <p-password id="password" [(ngModel)]="password" [toggleMask]="true"
                          [feedback]="isSetup" styleClass="w-full" inputStyleClass="w-full"
                          (keyup.enter)="submit()" autocomplete="current-password" />
              <label for="password">Wachtwoord</label>
            </p-floatlabel>
          </div>
          <p-button [label]="isSetup ? 'Account Aanmaken' : 'Inloggen'"
                    [icon]="isSetup ? 'pi pi-user-plus' : 'pi pi-sign-in'"
                    (onClick)="submit()"
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
export class AdminLoginComponent implements OnInit {
  email = '';
  password = '';
  name = '';
  loading = false;
  errorMessage = '';
  isSetup = false;

  constructor(
    private adminAuth: AdminAuthService,
    private router: Router,
    private http: HttpClient
  ) {
    if (adminAuth.isLoggedIn) {
      router.navigate(['/admin']);
    }
  }

  ngOnInit() {
    this.http.get<{ needsSetup: boolean }>('/api/admin/auth/setup').subscribe({
      next: (res) => this.isSetup = res.needsSetup
    });
  }

  submit() {
    if (!this.email || !this.password) {
      this.errorMessage = 'Vul alle velden in';
      return;
    }
    if (this.isSetup && !this.name) {
      this.errorMessage = 'Vul je naam in';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const obs = this.isSetup
      ? this.adminAuth.setup(this.email, this.password, this.name)
      : this.adminAuth.login(this.email, this.password);

    obs.subscribe({
      next: () => {
        this.router.navigate(['/admin']);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || (this.isSetup ? 'Aanmaken mislukt' : 'Inloggen mislukt');
        this.loading = false;
      }
    });
  }
}
