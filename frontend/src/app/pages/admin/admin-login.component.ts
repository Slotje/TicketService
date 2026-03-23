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
  templateUrl: './admin-login.component.html',
  styleUrl: './admin-login.component.scss'
})
export class AdminLoginComponent implements OnInit {
  email = '';
  password = '';
  firstName = '';
  lastName = '';
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
    if (this.isSetup && (!this.firstName || !this.lastName)) {
      this.errorMessage = 'Vul je voor- en achternaam in';
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    const obs = this.isSetup
      ? this.adminAuth.setup(this.email, this.password, this.firstName, this.lastName)
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
