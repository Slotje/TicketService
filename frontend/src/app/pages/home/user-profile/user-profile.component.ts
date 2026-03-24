import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { UserAuthService, UserUpdateRequest } from '../../../services/user-auth.service';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-user-profile',
  imports: [CommonModule, FormsModule, Button, InputText, FloatLabel, Message, Divider],
  templateUrl: './user-profile.component.html',
  styleUrl: './user-profile.component.scss'
})
export class UserProfileComponent implements OnInit {
  profileForm: UserUpdateRequest = {
    firstName: '',
    lastName: '',
    phone: '',
    street: '',
    houseNumber: '',
    postalCode: '',
    city: ''
  };

  saving = false;
  submitted = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    public userAuth: UserAuthService,
    private router: Router
  ) {}

  ngOnInit() {
    if (!this.userAuth.isLoggedIn) {
      this.router.navigate(['/login'], { state: { returnUrl: '/profiel' } });
      return;
    }

    this.profileForm = {
      firstName: this.userAuth.firstName || '',
      lastName: this.userAuth.lastName || '',
      phone: this.userAuth.phone || '',
      street: this.userAuth.street || '',
      houseNumber: this.userAuth.houseNumber || '',
      postalCode: this.userAuth.postalCode || '',
      city: this.userAuth.city || ''
    };
  }

  saveProfile() {
    this.submitted = true;
    this.errorMessage = '';
    this.successMessage = '';

    if (!this.profileForm.firstName?.trim() || !this.profileForm.lastName?.trim()) {
      this.errorMessage = 'Voornaam en achternaam zijn verplicht.';
      return;
    }

    this.saving = true;
    this.userAuth.updateProfile(this.profileForm).subscribe({
      next: () => {
        this.saving = false;
        this.successMessage = 'Profiel is opgeslagen.';
      },
      error: (err) => {
        this.saving = false;
        this.errorMessage = err.error?.error || 'Fout bij opslaan van profiel.';
      }
    });
  }
}
