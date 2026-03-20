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
  templateUrl: './customer-login.component.html',
  styleUrl: './customer-login.component.scss'
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
