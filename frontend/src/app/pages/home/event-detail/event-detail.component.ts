import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../../services/api.service';
import { UserAuthService } from '../../../services/user-auth.service';
import { CartService } from '../../../services/cart.service';
import { Event, OrderRequest, TicketCategory } from '../../../models/models';
import { Card } from 'primeng/card';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { FloatLabel } from 'primeng/floatlabel';
import { Message } from 'primeng/message';
import { ProgressBar } from 'primeng/progressbar';
import { Divider } from 'primeng/divider';
import { Tag } from 'primeng/tag';

@Component({
  selector: 'app-event-detail',
  imports: [
    CommonModule, FormsModule, Card, Button, InputText,
    InputNumber, FloatLabel, Message, ProgressBar, Divider, Tag
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
  addedToCart = false;
  errorMessage = '';
  selectedCategory: TicketCategory | null = null;
  categoryQuantities: Record<number, number> = {};

  orderForm: OrderRequest = {
    eventId: 0,
    buyerFirstName: '',
    buyerLastName: '',
    buyerEmail: '',
    buyerPhone: '',
    quantity: 1
  };

  get hasCategories(): boolean {
    return !!(this.event?.ticketCategories && this.event.ticketCategories.length > 0);
  }

  get activeCategories(): TicketCategory[] {
    return this.event?.ticketCategories?.filter(c => c.active) ?? [];
  }

  get currentPrice(): number {
    return this.selectedCategory?.price ?? this.event?.ticketPrice ?? 0;
  }

  get maxTickets(): number {
    if (!this.event) return 10;
    let max = Math.min(this.event.maxTicketsPerOrder, this.event.availableTickets ?? 0);
    if (this.selectedCategory?.maxTickets && this.selectedCategory.maxTickets > 0) {
      max = Math.min(max, this.selectedCategory.availableTickets ?? 0);
    }
    return Math.max(max, 0);
  }

  get selectedCategories(): TicketCategory[] {
    return this.activeCategories.filter(c => (this.categoryQuantities[c.id!] ?? 0) > 0);
  }

  get hasSelectedCategories(): boolean {
    return this.selectedCategories.length > 0;
  }

  getCategoryMax(cat: TicketCategory): number {
    if (!this.event) return 10;
    let max = this.event.maxTicketsPerOrder;
    if (cat.maxTickets && cat.maxTickets > 0) {
      max = Math.min(max, cat.availableTickets ?? 0);
    }
    return Math.max(max, 0);
  }

  getCategoryQuantity(cat: TicketCategory): number {
    return this.categoryQuantities[cat.id!] ?? 0;
  }

  setCategoryQuantity(cat: TicketCategory, qty: number) {
    this.categoryQuantities[cat.id!] = qty ?? 0;
  }

  constructor(
    private api: ApiService,
    private route: ActivatedRoute,
    private router: Router,
    private userAuth: UserAuthService,
    public cart: CartService
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

  isCategorySoldOut(cat: TicketCategory): boolean {
    return !!(cat.maxTickets && cat.maxTickets > 0 && (cat.availableTickets ?? 0) <= 0);
  }

  selectCategory(category: TicketCategory) {
    this.selectedCategory = category;
    this.orderForm.ticketCategoryId = category.id;
  }

  formatValidDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      weekday: 'short', day: 'numeric', month: 'long'
    });
  }

  formatTime(dateStr: string): string {
    return new Date(dateStr).toLocaleTimeString('nl-NL', {
      hour: '2-digit', minute: '2-digit'
    });
  }

  getTotalPrice(): number {
    if (this.hasCategories) {
      return this.selectedCategories.reduce((sum, cat) =>
        sum + cat.price * (this.categoryQuantities[cat.id!] ?? 0), 0);
    }
    return this.currentPrice * this.orderForm.quantity;
  }

  isValidEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  }

  addToCart() {
    if (!this.event) return;
    this.submitted = true;

    if (this.hasCategories) {
      if (!this.hasSelectedCategories) {
        this.errorMessage = 'Selecteer minstens één ticket type';
        return;
      }
      this.errorMessage = '';
      for (const cat of this.selectedCategories) {
        this.cart.addItem({
          eventId: this.event.id!,
          eventName: this.event.name,
          eventDate: this.event.eventDate,
          location: this.event.location,
          ticketCategoryId: cat.id,
          ticketCategoryName: cat.name,
          ticketPrice: cat.price,
          serviceFee: cat.serviceFee ?? this.event.effectiveOnlineServiceFee ?? this.event.serviceFee ?? 0,
          quantity: this.categoryQuantities[cat.id!],
          maxTicketsPerOrder: this.event.maxTicketsPerOrder,
          availableTickets: cat.availableTickets ?? this.event.availableTickets ?? 0,
          imageUrl: this.event.imageUrl
        });
      }
    } else {
      this.cart.addItem({
        eventId: this.event.id!,
        eventName: this.event.name,
        eventDate: this.event.eventDate,
        location: this.event.location,
        ticketPrice: this.event.ticketPrice,
        serviceFee: this.event.effectiveOnlineServiceFee ?? this.event.serviceFee ?? 0,
        quantity: this.orderForm.quantity,
        maxTicketsPerOrder: this.event.maxTicketsPerOrder,
        availableTickets: this.event.availableTickets ?? 0,
        imageUrl: this.event.imageUrl
      });
    }

    this.addedToCart = true;
    setTimeout(() => this.addedToCart = false, 3000);
  }

  placeOrder() {
    this.submitted = true;
    this.errorMessage = '';

    if (!this.orderForm.buyerFirstName || !this.orderForm.buyerLastName || !this.isValidEmail(this.orderForm.buyerEmail)) {
      return;
    }

    if (this.hasCategories) {
      if (!this.hasSelectedCategories) {
        this.errorMessage = 'Selecteer minstens één ticket type';
        return;
      }
      this.submitting = true;
      this.placeOrdersSequential([...this.selectedCategories]);
    } else {
      if (this.orderForm.quantity < 1 || this.orderForm.quantity > this.maxTickets) {
        this.errorMessage = `Aantal tickets moet tussen 1 en ${this.maxTickets} zijn`;
        return;
      }
      this.submitting = true;
      this.api.createOrder(this.orderForm).subscribe({
        next: (order) => {
          this.orderPlaced = true;
          if (order?.orderNumber) {
            setTimeout(() => this.router.navigateByUrl('/order/' + order.orderNumber), 1500);
          }
        },
        error: (err) => {
          this.errorMessage = err.error?.error || 'Er is een fout opgetreden bij het plaatsen van de bestelling';
          this.submitting = false;
        }
      });
    }
  }

  private placeOrdersSequential(remaining: TicketCategory[], firstOrderNumber?: string) {
    if (remaining.length === 0) {
      this.orderPlaced = true;
      if (firstOrderNumber) {
        setTimeout(() => this.router.navigateByUrl('/order/' + firstOrderNumber), 1500);
      }
      return;
    }

    const cat = remaining.shift()!;
    const request: OrderRequest = {
      ...this.orderForm,
      ticketCategoryId: cat.id,
      quantity: this.categoryQuantities[cat.id!]
    };

    this.api.createOrder(request).subscribe({
      next: (order) => {
        this.placeOrdersSequential(remaining, firstOrderNumber ?? order.orderNumber);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || `Fout bij "${cat.name}"`;
        this.submitting = false;
      }
    });
  }
}
