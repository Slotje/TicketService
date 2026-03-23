import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../services/auth.service';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Password } from 'primeng/password';

@Component({
  selector: 'app-scanner-login',
  imports: [CommonModule, FormsModule, Card, Button, InputText, FloatLabel, Message, Password],
  templateUrl: './scanner-login.component.html',
  styleUrl: './scanner-login.component.scss'
})
export class ScannerLoginComponent {
  username = '';
  password = '';
  loading = false;
  errorMessage = '';

  constructor(private auth: AuthService, private router: Router) {
    if (auth.isLoggedIn) {
      router.navigate(['/scan']);
    }
  }

  login() {
    if (!this.username || !this.password) {
      this.errorMessage = 'Vul gebruikersnaam en wachtwoord in';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.auth.login(this.username, this.password).subscribe({
      next: () => {
        this.router.navigate(['/scan']);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Inloggen mislukt';
        this.loading = false;
      }
    });
  }
}
