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
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
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
