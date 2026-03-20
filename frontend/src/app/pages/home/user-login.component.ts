import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { UserAuthService } from '../../services/user-auth.service';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-user-login',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Password, Divider],
  template: `
    <div class="login-page">
      <p-card>
        <ng-template #title>
          <div class="flex align-center gap-1">
            <i class="pi pi-user"></i>
            <span>{{ isRegister ? 'Account Aanmaken' : 'Inloggen' }}</span>
          </div>
        </ng-template>
        <ng-template #subtitle>
          {{ isRegister ? 'Maak een account aan om je tickets te beheren.' : 'Log in om je tickets te bekijken.' }}
        </ng-template>

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        <div class="login-form">
          @if (isRegister) {
            <div class="name-row">
              <div class="form-field">
                <p-floatlabel>
                  <input pInputText id="firstName" [(ngModel)]="firstName" class="w-full" />
                  <label for="firstName">Voornaam *</label>
                </p-floatlabel>
              </div>
              <div class="form-field">
                <p-floatlabel>
                  <input pInputText id="lastName" [(ngModel)]="lastName" class="w-full" />
                  <label for="lastName">Achternaam *</label>
                </p-floatlabel>
              </div>
            </div>
          }

          <div class="form-field">
            <p-floatlabel>
              <input pInputText id="email" [(ngModel)]="email" type="email" class="w-full" />
              <label for="email">E-mailadres *</label>
            </p-floatlabel>
          </div>

          @if (isRegister) {
            <div class="form-field">
              <p-floatlabel>
                <input pInputText id="phone" [(ngModel)]="phone" class="w-full" />
                <label for="phone">Telefoonnummer</label>
              </p-floatlabel>
            </div>
          }

          <div class="form-field">
            <p-floatlabel>
              <p-password id="password" [(ngModel)]="password" [toggleMask]="true" [feedback]="isRegister" styleClass="w-full" inputStyleClass="w-full" />
              <label for="password">Wachtwoord *</label>
            </p-floatlabel>
          </div>

          <p-button
            [label]="isRegister ? 'Registreren' : 'Inloggen'"
            [icon]="isRegister ? 'pi pi-user-plus' : 'pi pi-sign-in'"
            (onClick)="submit()"
            [loading]="loading"
            [disabled]="loading"
            [style]="{'width': '100%'}" />

          <p-divider />

          <div class="toggle-mode">
            @if (isRegister) {
              <span>Al een account? </span>
              <a (click)="toggleMode()" class="link">Inloggen</a>
            } @else {
              <span>Nog geen account? </span>
              <a (click)="toggleMode()" class="link">Registreren</a>
            }
          </div>
        </div>
      </p-card>
    </div>
  `,
  styles: [`
    .login-page {
      max-width: 450px;
      margin: 2rem auto;
    }
    .login-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .name-row {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .toggle-mode {
      text-align: center;
      font-size: 0.9rem;
    }
    .link {
      color: var(--p-primary-color);
      cursor: pointer;
      text-decoration: underline;
    }
  `]
})
export class UserLoginComponent {
  email = '';
  password = '';
  firstName = '';
  lastName = '';
  phone = '';
  isRegister = false;
  loading = false;
  errorMessage = '';

  constructor(private userAuth: UserAuthService, private router: Router) {}

  toggleMode() {
    this.isRegister = !this.isRegister;
    this.errorMessage = '';
  }

  submit() {
    this.errorMessage = '';

    if (!this.email || !this.password) {
      this.errorMessage = 'Vul alle verplichte velden in.';
      return;
    }

    if (this.isRegister && (!this.firstName || !this.lastName)) {
      this.errorMessage = 'Vul je voor- en achternaam in.';
      return;
    }

    this.loading = true;

    const obs = this.isRegister
      ? this.userAuth.register(this.email, this.password, this.firstName, this.lastName, this.phone || undefined)
      : this.userAuth.login(this.email, this.password);

    obs.subscribe({
      next: () => {
        this.loading = false;
        const returnUrl = history.state?.returnUrl || '/my-tickets';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Er is een fout opgetreden.';
        this.loading = false;
      }
    });
  }
}
