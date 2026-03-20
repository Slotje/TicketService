import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Customer, Event } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { ProgressBar } from 'primeng/progressbar';

@Component({
  selector: 'app-customer-landing',
  imports: [CommonModule, RouterLink, Card, Button, Tag, ProgressBar],
  template: `
    @if (loading) {
      <div class="text-center mt-3">
        <p-progressBar mode="indeterminate" [style]="{'height': '6px'}" />
      </div>
    }

    @if (!loading && !customer) {
      <div class="text-center mt-3">
        <h2>Pagina niet gevonden</h2>
        <p>Deze organisatie bestaat niet of is niet actief.</p>
      </div>
    }

    @if (customer) {
      <!-- Hero header with branding -->
      <div class="hero" [style.background-color]="customer.primaryColor || '#2980b9'">
        <div class="hero-content">
          @if (customer.logoUrl) {
            <img [src]="customer.logoUrl" [alt]="customer.companyName" class="hero-logo" />
          }
          <h1 class="hero-title">{{ customer.companyName }}</h1>
          @if (customer.website) {
            <a [href]="customer.website" target="_blank" class="hero-website">
              <i class="pi pi-external-link"></i> {{ customer.website }}
            </a>
          }
        </div>
      </div>

      <!-- Events section -->
      <div class="events-section">
        <h2 [style.color]="customer.primaryColor || '#2980b9'">Evenementen</h2>

        @if (events.length === 0) {
          <div class="text-center mt-2">
            <p style="color: var(--p-text-muted-color)">Er zijn momenteel geen evenementen beschikbaar.</p>
          </div>
        }

        <div class="grid grid-3 mt-2">
          @for (event of events; track event.id) {
            <p-card>
              <ng-template #header>
                <div class="event-card-header" [style.background-color]="(customer.primaryColor || '#2980b9') + '20'">
                  @if (event.imageUrl) {
                    <img [src]="event.imageUrl" [alt]="event.name" class="event-image" />
                  } @else {
                    <i class="pi pi-calendar" [style.color]="customer.primaryColor || '#2980b9'" style="font-size: 2rem"></i>
                  }
                  @if (event.availableTickets === 0) {
                    <div class="sold-out-banner">Uitverkocht</div>
                  }
                </div>
              </ng-template>
              <ng-template #title>{{ event.name }}</ng-template>
              <ng-template #subtitle>
                <div class="flex align-center gap-1">
                  <i class="pi pi-map-marker"></i>
                  <span>{{ event.location }}</span>
                </div>
              </ng-template>
              @if (event.description) {
                <p class="event-description">{{ event.description }}</p>
              }
              <div class="event-details mt-2">
                <div class="detail-row">
                  <i class="pi pi-clock" [style.color]="customer.primaryColor"></i>
                  <span>{{ formatDate(event.eventDate) }}</span>
                </div>
                <div class="detail-row">
                  <i class="pi pi-euro" [style.color]="customer.primaryColor"></i>
                  <span>{{ event.ticketPrice | number:'1.2-2' }} per ticket</span>
                </div>
                <div class="detail-row">
                  <i class="pi pi-ticket" [style.color]="customer.primaryColor"></i>
                  <span>{{ event.availableTickets }} beschikbaar</span>
                </div>
              </div>
              <ng-template #footer>
                <div class="flex justify-between align-center">
                  <p-tag
                    [value]="getStatusLabel(event)"
                    [severity]="getStatusSeverity(event)" />
                  <p-button
                    label="Tickets"
                    icon="pi pi-shopping-cart"
                    [routerLink]="'/event/' + event.id"
                    [disabled]="event.availableTickets === 0"
                    size="small"
                    [style]="{'background-color': customer.primaryColor, 'border-color': customer.primaryColor}" />
                </div>
              </ng-template>
            </p-card>
          }
        </div>
      </div>

      <!-- Footer -->
      <div class="landing-footer" [style.border-color]="customer.primaryColor || '#2980b9'">
        <p>&copy; {{ currentYear }} {{ customer.companyName }}</p>
      </div>
    }
  `,
  styles: [`
    .hero {
      margin: -1.5rem -1.5rem 0 -1.5rem;
      padding: 3rem 2rem;
      text-align: center;
      color: white;
    }
    .hero-content {
      max-width: 800px;
      margin: 0 auto;
    }
    .hero-logo {
      max-height: 100px;
      max-width: 300px;
      object-fit: contain;
      margin-bottom: 1rem;
      border-radius: 8px;
    }
    .hero-title {
      font-size: 2.5rem;
      font-weight: 700;
      margin: 0;
      text-shadow: 0 2px 4px rgba(0,0,0,0.2);
    }
    .hero-website {
      color: rgba(255,255,255,0.85);
      text-decoration: none;
      font-size: 0.95rem;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      margin-top: 0.5rem;
      &:hover { color: white; }
    }
    .events-section {
      padding: 2rem 0;
      h2 {
        text-align: center;
        font-size: 1.5rem;
        margin: 0;
      }
    }
    .event-card-header {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 120px;
      border-radius: var(--p-border-radius) var(--p-border-radius) 0 0;
      position: relative;
      overflow: hidden;
    }
    .event-image {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .sold-out-banner {
      position: absolute;
      top: 0; left: 0; right: 0; bottom: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: rgba(0,0,0,0.6);
      color: white;
      font-size: 1.5rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 2px;
    }
    .event-description {
      color: var(--p-text-muted-color);
      font-size: 0.9rem;
      line-height: 1.4;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .detail-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.25rem;
      font-size: 0.9rem;
      i { font-size: 0.85rem; }
    }
    .landing-footer {
      text-align: center;
      padding: 1.5rem;
      margin-top: 2rem;
      border-top: 2px solid;
      p {
        color: var(--p-text-muted-color);
        margin: 0;
        font-size: 0.85rem;
      }
    }
    @media (max-width: 768px) {
      .hero { padding: 2rem 1rem; }
      .hero-title { font-size: 1.75rem; }
    }
  `]
})
export class CustomerLandingComponent implements OnInit {
  customer: Customer | null = null;
  events: Event[] = [];
  loading = true;
  currentYear = new Date().getFullYear();

  constructor(private route: ActivatedRoute, private api: ApiService) {}

  ngOnInit() {
    const slug = this.route.snapshot.paramMap.get('slug');
    if (!slug) {
      this.loading = false;
      return;
    }

    this.api.getCustomerBySlug(slug).subscribe({
      next: (customer) => {
        this.customer = customer;
        this.api.getEventsByCustomer(customer.id!).subscribe({
          next: (events) => {
            // Only show published events
            this.events = events.filter(e => e.status === 'PUBLISHED' || e.status === 'SOLD_OUT');
            this.loading = false;
          },
          error: () => this.loading = false
        });
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

  getStatusLabel(event: Event): string {
    if (event.availableTickets === 0) return 'Uitverkocht';
    if ((event.availableTickets ?? 0) < event.maxTickets * 0.1) return 'Bijna uitverkocht';
    return 'Beschikbaar';
  }

  getStatusSeverity(event: Event): 'success' | 'warn' | 'danger' {
    if (event.availableTickets === 0) return 'danger';
    if ((event.availableTickets ?? 0) < event.maxTickets * 0.1) return 'warn';
    return 'success';
  }
}
