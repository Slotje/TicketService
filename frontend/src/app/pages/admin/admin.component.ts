import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';

@Component({
  selector: 'app-admin',
  imports: [RouterLink, Card, Button],
  template: `
    <h1>Beheer Dashboard</h1>
    <div class="grid grid-3">
      <p-card>
        <ng-template #title><i class="pi pi-users"></i> Klanten</ng-template>
        <p>Beheer klanten, personalisatie en branding instellingen.</p>
        <ng-template #footer>
          <p-button label="Klanten Beheren" icon="pi pi-arrow-right" routerLink="/admin/customers" />
        </ng-template>
      </p-card>
      <p-card>
        <ng-template #title><i class="pi pi-calendar"></i> Evenementen</ng-template>
        <p>Maak en beheer evenementen, stel ticket capaciteit in.</p>
        <ng-template #footer>
          <p-button label="Evenementen Beheren" icon="pi pi-arrow-right" routerLink="/admin/events" />
        </ng-template>
      </p-card>
      <p-card>
        <ng-template #title><i class="pi pi-id-card"></i> Scanner Accounts</ng-template>
        <p>Beheer scanner accounts voor het scannen van tickets bij evenementen.</p>
        <ng-template #footer>
          <p-button label="Accounts Beheren" icon="pi pi-arrow-right" routerLink="/admin/scanners" />
        </ng-template>
      </p-card>
      <p-card>
        <ng-template #title><i class="pi pi-qrcode"></i> Ticket Scanner</ng-template>
        <p>Open de ticket scanner met camera om tickets te scannen.</p>
        <ng-template #footer>
          <p-button label="Scanner Openen" icon="pi pi-arrow-right" routerLink="/scan/login" />
        </ng-template>
      </p-card>
    </div>
  `,
  styles: [`
    h1 { color: var(--p-primary-color); margin-bottom: 1.5rem; }
    p { color: var(--p-text-muted-color); min-height: 3rem; }
  `]
})
export class AdminComponent {}
