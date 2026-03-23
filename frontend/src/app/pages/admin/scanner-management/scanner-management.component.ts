import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, ScannerUser } from '../../../services/auth.service';
import { TableModule } from 'primeng/table';
import { Button } from 'primeng/button';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { FloatLabel } from 'primeng/floatlabel';
import { Tag } from 'primeng/tag';
import { Message } from 'primeng/message';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { ConfirmationService } from 'primeng/api';
import { Password } from 'primeng/password';
import { Tooltip } from 'primeng/tooltip';

@Component({
  selector: 'app-scanner-management',
  imports: [
    CommonModule, FormsModule, TableModule, Button, Dialog, InputText,
    FloatLabel, Tag, Message, ConfirmDialog, Password, Tooltip
  ],
  providers: [ConfirmationService],
  templateUrl: './scanner-management.component.html',
  styleUrl: './scanner-management.component.scss'
})
export class ScannerManagementComponent implements OnInit {
  users: ScannerUser[] = [];
  loading = true;
  dialogVisible = false;
  saving = false;
  errorMessage = '';
  dialogError = '';

  form = { username: '', password: '', displayName: '' };

  constructor(private auth: AuthService, private confirmService: ConfirmationService) {}

  ngOnInit() {
    this.loadUsers();
  }

  loadUsers() {
    this.loading = true;
    this.auth.getUsers().subscribe({
      next: (u) => { this.users = u; this.loading = false; },
      error: () => this.loading = false
    });
  }

  openNew() {
    this.form = { username: '', password: '', displayName: '' };
    this.dialogError = '';
    this.dialogVisible = true;
  }

  createUser() {
    if (!this.form.username || !this.form.password) {
      this.dialogError = 'Gebruikersnaam en wachtwoord zijn verplicht';
      return;
    }
    this.saving = true;
    this.auth.createUser(this.form.username, this.form.password, this.form.displayName).subscribe({
      next: () => {
        this.dialogVisible = false;
        this.saving = false;
        this.loadUsers();
      },
      error: (err) => {
        this.dialogError = err.error?.error || 'Fout bij aanmaken';
        this.saving = false;
      }
    });
  }

  toggleUser(user: ScannerUser) {
    this.auth.toggleUser(user.id).subscribe({
      next: () => this.loadUsers(),
      error: (err) => this.errorMessage = err.error?.error || 'Fout bij wijzigen'
    });
  }

  deleteUser(user: ScannerUser) {
    this.confirmService.confirm({
      message: `Weet je zeker dat je "${user.username}" wilt verwijderen?`,
      header: 'Bevestiging',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.auth.deleteUser(user.id).subscribe({
          next: () => this.loadUsers(),
          error: (err) => this.errorMessage = err.error?.error || 'Fout bij verwijderen'
        });
      }
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      day: '2-digit', month: '2-digit', year: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }
}
