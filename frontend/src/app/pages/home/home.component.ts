import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Event } from '../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { Tag } from 'primeng/tag';
import { ProgressBar } from 'primeng/progressbar';

@Component({
  selector: 'app-home',
  imports: [CommonModule, RouterLink, Card, Button, Tag, ProgressBar],
  template: `
    <div class="home-container">
      <div class="hero-section">
        <h1>Beschikbare Evenementen</h1>
        <p>Bekijk onze evenementen en bestel je tickets online</p>
      </div>

      @if (loading) {
        <div class="text-center mt-3">
          <p-progressBar mode="indeterminate" [style]="{'height': '6px'}" />
        </div>
      }

      @if (!loading && events.length === 0) {
        <div class="text-center mt-3">
          <p>Er zijn momenteel geen evenementen beschikbaar.</p>
        </div>
      }

      <div class="grid grid-3 mt-2">
        @for (event of events; track event.id) {
          <p-card>
            <ng-template #header>
              <div class="event-card-header" [style.background-color]="'var(--p-primary-100)'">
                <i class="pi pi-calendar" style="font-size: 2rem; color: var(--p-primary-color)"></i>
              </div>
            </ng-template>
            <ng-template #title>
              {{ event.name }}
            </ng-template>
            <ng-template #subtitle>
              <div class="flex align-center gap-1">
                <i class="pi pi-map-marker"></i>
                <span>{{ event.location }}</span>
              </div>
            </ng-template>
            <p class="event-description">{{ event.description }}</p>
            <div class="event-details mt-2">
              <div class="detail-row">
                <i class="pi pi-clock"></i>
                <span>{{ formatDate(event.eventDate) }}</span>
              </div>
              <div class="detail-row">
                <i class="pi pi-euro"></i>
                <span>{{ event.ticketPrice | number:'1.2-2' }} per ticket</span>
              </div>
              <div class="detail-row">
                <i class="pi pi-ticket"></i>
                <span>{{ event.availableTickets }} van {{ event.maxTickets }} beschikbaar</span>
              </div>
              <div class="availability-bar mt-1">
                <p-progressBar
                  [value]="getAvailabilityPercentage(event)"
                  [showValue]="false"
                  [style]="{'height': '8px'}" />
              </div>
            </div>
            <ng-template #footer>
              <div class="flex justify-between align-center">
                <p-tag
                  [value]="getStatusLabel(event)"
                  [severity]="getStatusSeverity(event)" />
                <p-button
                  label="Tickets bestellen"
                  icon="pi pi-shopping-cart"
                  [routerLink]="'/event/' + event.id"
                  [disabled]="event.availableTickets === 0"
                  size="small" />
              </div>
            </ng-template>
          </p-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .hero-section {
      text-align: center;
      padding: 2rem 0;
      h1 {
        font-size: 2rem;
        color: var(--p-primary-color);
        margin: 0 0 0.5rem 0;
      }
      p {
        color: var(--p-text-muted-color);
        font-size: 1.1rem;
      }
    }
    .event-card-header {
      display: flex;
      align-items: center;
      justify-content: center;
      height: 120px;
      border-radius: var(--p-border-radius) var(--p-border-radius) 0 0;
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
      i { color: var(--p-primary-color); font-size: 0.85rem; }
    }
  `]
})
export class HomeComponent implements OnInit {
  events: Event[] = [];
  loading = true;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getPublishedEvents().subscribe({
      next: (events) => {
        this.events = events;
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  formatDate(dateStr: string): string {
    const date = new Date(dateStr);
    return date.toLocaleDateString('nl-NL', {
      weekday: 'long', year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  getAvailabilityPercentage(event: Event): number {
    return ((event.availableTickets ?? 0) / event.maxTickets) * 100;
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
