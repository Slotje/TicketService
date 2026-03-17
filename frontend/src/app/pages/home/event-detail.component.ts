import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Event, OrderRequest } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Divider } from 'primeng/divider';

@Component({
  selector: 'app-event-detail',
  imports: [
    CommonModule, FormsModule, Card, Button, InputText,
    InputNumber, FloatLabel, Message, ProgressBar, Divider
  ],
  template: `
    @if (loading) {
      <p-progressBar mode="indeterminate" [style]="{'height': '6px'}" />
    }

    @if (event) {
      <div class="event-detail-layout">
        <div class="event-info-section">
          <p-card>
            <ng-template #title>
              <h1>{{ event.name }}</h1>
            </ng-template>
            <ng-template #subtitle>
              Georganiseerd door {{ event.customerName }}
            </ng-template>

            <p class="event-description">{{ event.description }}</p>

            <p-divider />

            <div class="event-meta">
              <div class="meta-item">
                <i class="pi pi-calendar"></i>
                <div>
                  <strong>Datum</strong>
                  <p>{{ formatDate(event.eventDate) }}</p>
                  @if (event.endDate) {
                    <p>tot {{ formatDate(event.endDate) }}</p>
                  }
                </div>
              </div>
              <div class="meta-item">
                <i class="pi pi-map-marker"></i>
                <div>
                  <strong>Locatie</strong>
                  <p>{{ event.location }}</p>
                  @if (event.address) {
                    <p class="text-muted">{{ event.address }}</p>
                  }
                </div>
              </div>
              <div class="meta-item">
                <i class="pi pi-euro"></i>
                <div>
                  <strong>Prijs</strong>
                  <p>{{ event.ticketPrice | number:'1.2-2' }} per ticket</p>
                </div>
              </div>
              <div class="meta-item">
                <i class="pi pi-ticket"></i>
                <div>
                  <strong>Beschikbaarheid</strong>
                  <p>{{ event.availableTickets }} van {{ event.maxTickets }} tickets beschikbaar</p>
                  <p class="text-muted">Max {{ event.maxTicketsPerOrder }} per bestelling</p>
                </div>
              </div>
            </div>
          </p-card>
        </div>

        <div class="order-section">
          <p-card>
            <ng-template #title>
              <i class="pi pi-shopping-cart"></i> Tickets Bestellen
            </ng-template>

            @if (event.availableTickets === 0) {
              <p-message severity="warn" text="Dit evenement is uitverkocht" [style]="{'width': '100%'}" />
            } @else if (orderPlaced) {
              <p-message severity="success" text="Bestelling geplaatst! Je wordt doorgestuurd..." [style]="{'width': '100%'}" />
            } @else {
              @if (errorMessage) {
                <p-message severity="error" [text]="errorMessage" [style]="{'width': '100%'}" class="mb-2" />
              }

              <div class="order-form">
                <div class="form-field">
                  <p-floatlabel>
                    <input pInputText id="buyerName" [(ngModel)]="orderForm.buyerName" class="w-full" />
                    <label for="buyerName">Naam *</label>
                  </p-floatlabel>
                  @if (submitted && !orderForm.buyerName) {
                    <small class="error-text">Naam is verplicht</small>
                  }
                </div>

                <div class="form-field">
                  <p-floatlabel>
                    <input pInputText id="buyerEmail" [(ngModel)]="orderForm.buyerEmail" type="email" class="w-full" />
                    <label for="buyerEmail">E-mail *</label>
                  </p-floatlabel>
                  @if (submitted && !isValidEmail(orderForm.buyerEmail)) {
                    <small class="error-text">Geldig e-mailadres is verplicht</small>
                  }
                </div>

                <div class="form-field">
                  <p-floatlabel>
                    <input pInputText id="buyerPhone" [(ngModel)]="orderForm.buyerPhone" class="w-full" />
                    <label for="buyerPhone">Telefoon</label>
                  </p-floatlabel>
                </div>

                <div class="form-field">
                  <p-floatlabel>
                    <p-inputNumber
                      id="quantity"
                      [(ngModel)]="orderForm.quantity"
                      [min]="1"
                      [max]="maxTickets"
                      [showButtons]="true"
                      class="w-full" />
                    <label for="quantity">Aantal tickets *</label>
                  </p-floatlabel>
                  <small class="text-muted">Max {{ maxTickets }} tickets</small>
                </div>

                <p-divider />

                <div class="order-summary">
                  <div class="summary-row">
                    <span>{{ orderForm.quantity }}x ticket</span>
                    <span>{{ event.ticketPrice | number:'1.2-2' }}</span>
                  </div>
                  <div class="summary-row total">
                    <strong>Totaal</strong>
                    <strong>EUR {{ getTotalPrice() | number:'1.2-2' }}</strong>
                  </div>
                </div>

                <p-message severity="info" text="Na bestellen heb je 10 minuten om te bevestigen. Tickets worden gereserveerd." [style]="{'width': '100%'}" />

                <p-button
                  label="Bestelling Plaatsen"
                  icon="pi pi-check"
                  (onClick)="placeOrder()"
                  [loading]="submitting"
                  [disabled]="submitting"
                  class="w-full mt-2"
                  [style]="{'width': '100%'}" />
              </div>
            }
          </p-card>
        </div>
      </div>
    }
  `,
  styles: [`
    .event-detail-layout {
      display: grid;
      grid-template-columns: 1fr 400px;
      gap: 1.5rem;
      @media (max-width: 900px) {
        grid-template-columns: 1fr;
      }
    }
    h1 { margin: 0; font-size: 1.75rem; }
    .event-description {
      color: var(--p-text-muted-color);
      line-height: 1.6;
    }
    .event-meta {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .meta-item {
      display: flex;
      gap: 0.75rem;
      i { font-size: 1.25rem; color: var(--p-primary-color); margin-top: 0.25rem; }
      p { margin: 0.25rem 0 0 0; }
      .text-muted { color: var(--p-text-muted-color); font-size: 0.9rem; }
    }
    .order-form {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .form-field {
      .error-text { color: var(--p-red-500); font-size: 0.8rem; }
    }
    .order-summary {
      background: var(--p-surface-100);
      padding: 1rem;
      border-radius: var(--p-border-radius);
    }
    .summary-row {
      display: flex;
      justify-content: space-between;
      padding: 0.25rem 0;
      &.total {
        border-top: 1px solid var(--p-surface-300);
        margin-top: 0.5rem;
        padding-top: 0.5rem;
        font-size: 1.1rem;
      }
    }
  `]
})
export class EventDetailComponent implements OnInit {
  event: Event | null = null;
  loading = true;
  submitted = false;
  submitting = false;
  orderPlaced = false;
  errorMessage = '';

  orderForm: OrderRequest = {
    eventId: 0,
    buyerName: '',
    buyerEmail: '',
    buyerPhone: '',
    quantity: 1
  };

  get maxTickets(): number {
    if (!this.event) return 10;
    return Math.min(this.event.maxTicketsPerOrder, this.event.availableTickets ?? 0);
  }

  constructor(private api: ApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getEvent(id).subscribe({
      next: (event) => {
        this.event = event;
        this.orderForm.eventId = event.id!;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getTotalPrice(): number {
    return (this.event?.ticketPrice ?? 0) * this.orderForm.quantity;
  }

  isValidEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  placeOrder() {
    this.submitted = true;
    this.errorMessage = '';

    if (!this.orderForm.buyerName || !this.isValidEmail(this.orderForm.buyerEmail)) {
      return;
    }

    if (this.orderForm.quantity < 1 || this.orderForm.quantity > this.maxTickets) {
      this.errorMessage = `Aantal tickets moet tussen 1 en ${this.maxTickets} zijn`;
      return;
    }

    this.submitting = true;
    this.api.createOrder(this.orderForm).subscribe({
      next: (order) => {
        this.orderPlaced = true;
        if (order?.orderNumber) {
          setTimeout(() => {
            this.router.navigateByUrl('/order/' + order.orderNumber);
          }, 1500);
        }
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Er is een fout opgetreden bij het plaatsen van de bestelling';
        this.submitting = false;
      }
    });
  }
}
