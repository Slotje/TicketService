import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CustomerAuthService } from '../../services/customer-auth.service';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Password } from 'primeng/password';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-customer-login',
  imports: [CommonModule, FormsModule, RouterLink, Card, Button, InputText, FloatLabel, Password, Message],
  template: `
    <div class="login-container">
      <p-card>
        <ng-template #header>
          <div class="login-header">
            <i class="pi pi-building" style="font-size: 2.5rem; color: var(--p-primary-color)"></i>
            <h2>Klant Login</h2>
            <p>Log in om je evenementen te beheren</p>
          </div>
        </ng-template>

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        <div class="form-stack">
          <p-floatlabel>
            <input pInputText id="email" [(ngModel)]="email" class="w-full" (keyup.enter)="login()" />
            <label for="email">E-mailadres</label>
          </p-floatlabel>

          <p-floatlabel>
            <p-password id="password" [(ngModel)]="password" [toggleMask]="true" [feedback]="false"
                        styleClass="w-full" inputStyleClass="w-full" (keyup.enter)="login()" />
            <label for="password">Wachtwoord</label>
          </p-floatlabel>

          <p-button label="Inloggen" icon="pi pi-sign-in" (onClick)="login()"
                    [loading]="loading" styleClass="w-full" />

          <div class="forgot-link">
            <a routerLink="/klant/forgot-password" class="link">Wachtwoord vergeten?</a>
          </div>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .login-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 60vh;
    }
    :host ::ng-deep .p-card {
      width: 400px;
      max-width: 95vw;
    }
    .login-header {
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
    .forgot-link {
      text-align: center;
      font-size: 0.85rem;
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
export class CustomerLoginComponent {
  email = '';
  password = '';
  loading = false;
  errorMessage = '';

  constructor(private customerAuth: CustomerAuthService, private router: Router) {
    if (customerAuth.isLoggedIn) {
      router.navigate(['/klant/dashboard']);
    }
  }

  login() {
    if (!this.email || !this.password) {
      this.errorMessage = 'Vul e-mailadres en wachtwoord in';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.customerAuth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/klant/dashboard']),
      error: (err) => {
        this.errorMessage = err.error?.error || 'Inloggen mislukt';
        this.loading = false;
      }
    });
  }
}
