import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { CustomerAuthService } from '../../services/customer-auth.service';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Password } from 'primeng/password';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';

@Component({
  selector: 'app-customer-activate',
  imports: [CommonModule, FormsModule, Card, Button, Password, FloatLabel, Message, ProgressBar],
  template: `
    <div class="activate-container">
      <p-card>
        <ng-template #header>
          <div class="activate-header">
            <i class="pi pi-lock" style="font-size: 2.5rem; color: var(--p-primary-color)"></i>
            <h2>Account Activeren</h2>
            @if (companyName) {
              <p>Welkom bij <strong>{{ companyName }}</strong></p>
            }
          </div>
        </ng-template>

        @if (verifying) {
          <p-progressBar mode="indeterminate" [style]="{'height': '6px'}" />
        }

        @if (errorMessage) {
          <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
        }

        @if (!verifying && !errorMessage) {
          <p style="text-align: center; color: var(--p-text-muted-color); margin-bottom: 1rem;">
            Stel een wachtwoord in voor <strong>{{ email }}</strong>
          </p>

          <div class="form-stack">
            <p-floatlabel>
              <p-password id="password" [(ngModel)]="password" [toggleMask]="true"
                          styleClass="w-full" inputStyleClass="w-full" (keyup.enter)="activate()" />
              <label for="password">Wachtwoord</label>
            </p-floatlabel>

            <p-floatlabel>
              <p-password id="confirmPassword" [(ngModel)]="confirmPassword" [toggleMask]="true" [feedback]="false"
                          styleClass="w-full" inputStyleClass="w-full" (keyup.enter)="activate()" />
              <label for="confirmPassword">Wachtwoord bevestigen</label>
            </p-floatlabel>

            <p-button label="Account Activeren" icon="pi pi-check" (onClick)="activate()"
                      [loading]="loading" styleClass="w-full" />
          </div>
        }

        @if (errorMessage && !verifying) {
          <div style="text-align: center; margin-top: 1rem;">
            <p-button label="Naar inloggen" icon="pi pi-sign-in" [text]="true" routerLink="/klant/login" />
          </div>
        }
      </p-card>
    </div>
  `,
  styles: [`
    .activate-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 60vh;
    }
    :host ::ng-deep .p-card {
      width: 450px;
      max-width: 95vw;
    }
    .activate-header {
      text-align: center;
      padding: 2rem 1rem 1rem;
      h2 { color: var(--p-primary-color); margin: 0.5rem 0 0.25rem; }
      p { color: var(--p-text-muted-color); margin: 0; font-size: 0.95rem; }
    }
    .form-stack {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
  `]
})
export class CustomerActivateComponent implements OnInit {
  inviteToken = '';
  companyName = '';
  email = '';
  password = '';
  confirmPassword = '';
  loading = false;
  verifying = true;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private customerAuth: CustomerAuthService
  ) {}

  ngOnInit() {
    this.inviteToken = this.route.snapshot.paramMap.get('token') || '';
    if (!this.inviteToken) {
      this.errorMessage = 'Geen activatietoken gevonden';
      this.verifying = false;
      return;
    }

    this.customerAuth.verifyInvite(this.inviteToken).subscribe({
      next: (res) => {
        this.companyName = res.companyName;
        this.email = res.email;
        this.verifying = false;
      },
      error: () => {
        this.errorMessage = 'Ongeldige of verlopen uitnodiging. Neem contact op met de beheerder.';
        this.verifying = false;
      }
    });
  }

  activate() {
    if (!this.password || this.password.length < 8) {
      this.errorMessage = 'Wachtwoord moet minimaal 8 tekens zijn';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Wachtwoorden komen niet overeen';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.customerAuth.setPassword(this.inviteToken, this.password).subscribe({
      next: () => this.router.navigate(['/klant/dashboard']),
      error: (err) => {
        this.errorMessage = err.error?.error || 'Activatie mislukt';
        this.loading = false;
      }
    });
  }
}
