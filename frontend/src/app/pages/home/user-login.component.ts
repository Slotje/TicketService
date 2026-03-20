import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
  imports: [CommonModule, FormsModule, RouterLink, Card, Button, InputText, FloatLabel, Message, Password, Divider],
  templateUrl: './user-login.component.html',
  styleUrl: './user-login.component.scss'
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
