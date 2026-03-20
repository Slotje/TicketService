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
  templateUrl: './customer-activate.component.html',
  styleUrl: './customer-activate.component.scss'
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
