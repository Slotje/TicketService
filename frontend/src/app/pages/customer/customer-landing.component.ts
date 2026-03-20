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
  templateUrl: './customer-landing.component.html',
  styleUrl: './customer-landing.component.scss'
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
