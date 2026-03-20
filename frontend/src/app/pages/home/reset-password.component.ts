import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { FloatLabel } from 'primeng/floatlabel';
import { Password } from 'primeng/password';
import { Message } from 'primeng/message';

@Component({
  selector: 'app-reset-password',
  imports: [CommonModule, FormsModule, RouterLink, Card, Button, FloatLabel, Password, Message],
  template: `
    <div class="reset-page">
      <p-card>
        <ng-template #title>
          <div class="flex align-center gap-1">
            <i class="pi pi-lock"></i>
            <span>Nieuw wachtwoord instellen</span>
          </div>
        </ng-template>

        @if (successMessage) {
          <p-message severity="success" [text]="successMessage" [style]="{'width': '100%'}" class="mb-2" />
          <div class="form-stack">
            <p-button label="Naar inloggen" icon="pi pi-sign-in" routerLink="/login" styleClass="w-full" />
          </div>
        } @else {
          @if (errorMessage) {
            <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
          }

          @if (!token) {
            <p-message severity="warn" text="Geen geldige resetlink. Vraag een nieuwe aan." [style]="{'width': '100%'}" />
            <div class="form-stack">
              <p-button label="Wachtwoord vergeten" icon="pi pi-key" routerLink="/forgot-password" styleClass="w-full" />
            </div>
          } @else {
            <div class="form-stack">
              <p-floatlabel>
                <p-password id="password" [(ngModel)]="password" [toggleMask]="true" [feedback]="true"
                            styleClass="w-full" inputStyleClass="w-full" />
                <label for="password">Nieuw wachtwoord</label>
              </p-floatlabel>

              <p-floatlabel>
                <p-password id="confirmPassword" [(ngModel)]="confirmPassword" [toggleMask]="true" [feedback]="false"
                            styleClass="w-full" inputStyleClass="w-full" (keyup.enter)="submit()" />
                <label for="confirmPassword">Wachtwoord bevestigen</label>
              </p-floatlabel>

              <p-button label="Wachtwoord opslaan" icon="pi pi-check" (onClick)="submit()"
                        [loading]="loading" styleClass="w-full" />
            </div>
          }
        }
      </p-card>
    </div>
  `,
  styles: [`
    .reset-page {
      max-width: 450px;
      margin: 2rem auto;
    }
    .form-stack {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      margin-top: 1rem;
    }
  `]
})
export class ResetPasswordComponent implements OnInit {
  token = '';
  password = '';
  confirmPassword = '';
  loading = false;
  successMessage = '';
  errorMessage = '';
  private apiUrl = '/api/user/auth/reset-password';

  constructor(private route: ActivatedRoute, private http: HttpClient, private router: Router) {}

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';
    // Detect customer reset URL
    if (this.router.url.includes('/klant/')) {
      this.apiUrl = '/api/customer/auth/reset-password';
    }
  }

  submit() {
    this.errorMessage = '';
    if (!this.password || this.password.length < 6) {
      this.errorMessage = 'Wachtwoord moet minimaal 6 tekens zijn.';
      return;
    }
    if (this.password !== this.confirmPassword) {
      this.errorMessage = 'Wachtwoorden komen niet overeen.';
      return;
    }
    this.loading = true;
    this.http.post<{ message: string }>(this.apiUrl, { token: this.token, password: this.password }).subscribe({
      next: (res) => {
        this.successMessage = res.message;
        this.loading = false;
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Ongeldige of verlopen resetlink.';
        this.loading = false;
      }
    });
  }
}
