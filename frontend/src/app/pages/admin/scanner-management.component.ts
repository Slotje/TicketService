import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, ScannerUser } from '../../services/auth.service';
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
  template: `
    <div class="flex justify-between align-center mb-2">
      <h1>Scanner Accounts</h1>
      <p-button label="Nieuw Account" icon="pi pi-plus" (onClick)="openNew()" />
    </div>

    @if (errorMessage) {
      <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
    }

    <p-table [value]="users" [loading]="loading" styleClass="p-datatable-striped">
      <ng-template #header>
        <tr>
          <th>Gebruikersnaam</th>
          <th>Weergavenaam</th>
          <th>Status</th>
          <th>Aangemaakt</th>
          <th>Acties</th>
        </tr>
      </ng-template>
      <ng-template #body let-user>
        <tr>
          <td data-label="Gebruikersnaam">{{ user.username }}</td>
          <td data-label="Weergavenaam">{{ user.displayName }}</td>
          <td data-label="Status">
            <p-tag [value]="user.active ? 'Actief' : 'Inactief'" [severity]="user.active ? 'success' : 'danger'" />
          </td>
          <td data-label="Aangemaakt">{{ formatDate(user.createdAt) }}</td>
          <td data-label="Acties">
            <div class="flex gap-1">
              <p-button [icon]="user.active ? 'pi pi-lock' : 'pi pi-lock-open'" [rounded]="true" [text]="true"
                        [severity]="user.active ? 'warn' : 'success'" (onClick)="toggleUser(user)"
                        [pTooltip]="user.active ? 'Deactiveren' : 'Activeren'" />
              <p-button icon="pi pi-trash" [rounded]="true" [text]="true" severity="danger" (onClick)="deleteUser(user)" />
            </div>
          </td>
        </tr>
      </ng-template>
    </p-table>

    <p-dialog [(visible)]="dialogVisible" header="Nieuw Scanner Account" [modal]="true"
              [style]="{'width': '450px'}" [closable]="true">
      @if (dialogError) {
        <p-message severity="error" [text]="dialogError" [style]="{'width': '100%'}" class="mb-2" />
      }

      <div class="form-column">
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="username" [(ngModel)]="form.username" class="w-full" autocomplete="off" />
            <label for="username">Gebruikersnaam *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <p-password id="newPassword" [(ngModel)]="form.password" [toggleMask]="true"
                        [feedback]="false" styleClass="w-full" inputStyleClass="w-full" autocomplete="new-password" />
            <label for="newPassword">Wachtwoord *</label>
          </p-floatlabel>
        </div>
        <div class="form-field">
          <p-floatlabel>
            <input pInputText id="displayName" [(ngModel)]="form.displayName" class="w-full" />
            <label for="displayName">Weergavenaam</label>
          </p-floatlabel>
        </div>
      </div>

      <ng-template #footer>
        <p-button label="Annuleren" icon="pi pi-times" (onClick)="dialogVisible = false" [text]="true" />
        <p-button label="Aanmaken" icon="pi pi-check" (onClick)="createUser()" [loading]="saving" />
      </ng-template>
    </p-dialog>

    <p-confirmDialog />
  `,
  styles: [`
    h1 { color: var(--p-primary-color); margin: 0; }
    .form-column {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
  `]
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
