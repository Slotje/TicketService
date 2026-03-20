import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { UserAuthService } from '../../services/user-auth.service';
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
  templateUrl: './event-detail.component.html',
  styleUrl: './event-detail.component.scss'
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
    buyerFirstName: '',
    buyerLastName: '',
    buyerEmail: '',
    buyerPhone: '',
    quantity: 1
  };

  get maxTickets(): number {
    if (!this.event) return 10;
    return Math.min(this.event.maxTicketsPerOrder, this.event.availableTickets ?? 0);
  }

  constructor(
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router,
    private userAuth: UserAuthService
  ) {}

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.getEvent(id).subscribe({
      next: (event) => {
        this.event = event;
        this.orderForm.eventId = event.id!;
        this.loading = false;
        this.prefillFromUser();
      },
      error: () => this.loading = false
    });
  }

  private prefillFromUser() {
    if (this.userAuth.isLoggedIn) {
      this.orderForm.buyerFirstName = this.userAuth.firstName || '';
      this.orderForm.buyerLastName = this.userAuth.lastName || '';
      this.orderForm.buyerEmail = this.userAuth.email || '';
      this.orderForm.buyerPhone = this.userAuth.phone || '';
    }
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

    if (!this.orderForm.buyerFirstName || !this.orderForm.buyerLastName || !this.isValidEmail(this.orderForm.buyerEmail)) {
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
