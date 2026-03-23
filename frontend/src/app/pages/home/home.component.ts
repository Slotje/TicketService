import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { Event } from '../../models/models';
import { Tag } from 'primeng/tag';

@Component({
  selector: 'app-home',
  imports: [CommonModule, RouterLink, Tag],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
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
