import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService, CartItem } from '../../../services/cart.service';
import { ApiService } from '../../../services/api.service';
import { UserAuthService } from '../../../services/user-auth.service';
import { Order, OrderRequest } from '../../../models/models';
import { Button } from 'primeng/button';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Tag } from 'primeng/tag';
import { Subscription } from 'rxjs';

@Component({
  selector: 'app-cart',
  imports: [CommonModule, FormsModule, RouterLink, Button, InputNumber, Message, Tag],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.scss'
})
export class CartComponent implements OnInit, OnDestroy {
  Math = Math;
  items: CartItem[] = [];
  reservedOrders: Order[] = [];
  reservedTimers: Record<number, string> = {};
  processing: Record<number, boolean> = {};
  errorMessage = '';
  private sub?: Subscription;
  private timerInterval: any;

  constructor(
    public cart: CartService,
    private api: ApiService,
    private userAuth: UserAuthService,
    private router: Router
  ) {}

  ngOnInit() {
    this.sub = this.cart.cartItems$.subscribe(items => {
      this.items = items;
    });
    this.loadReservedOrders();
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
    if (this.timerInterval) clearInterval(this.timerInterval);
  }

  get isEmpty(): boolean {
    return this.items.length === 0 && this.reservedOrders.length === 0;
  }

  get totalItemCount(): number {
    return this.items.length + this.reservedOrders.length;
  }

  loadReservedOrders() {
    if (!this.userAuth.isLoggedIn) return;
    const email = this.userAuth.email;
    if (!email) return;

    this.api.getOrdersByEmail(email).subscribe({
      next: (orders) => {
        this.reservedOrders = orders.filter(o => o.status === 'RESERVED');
        if (this.reservedOrders.length > 0) {
          this.startTimers();
        }
      }
    });
  }

  private startTimers() {
    this.updateTimers();
    this.timerInterval = setInterval(() => this.updateTimers(), 1000);
  }

  private updateTimers() {
    const now = Date.now();
    for (const order of this.reservedOrders) {
      if (!order.expiresAt) continue;
      const expires = new Date(order.expiresAt).getTime();
      const diff = expires - now;
      if (diff <= 0) {
        this.reservedTimers[order.id] = 'Verlopen';
      } else {
        const m = Math.floor(diff / 60000);
        const s = Math.floor((diff % 60000) / 1000);
        this.reservedTimers[order.id] = `${m}:${s.toString().padStart(2, '0')}`;
      }
    }
    // Remove expired orders
    this.reservedOrders = this.reservedOrders.filter(o =>
      this.reservedTimers[o.id] !== 'Verlopen'
    );
  }

  getTimer(order: Order): string {
    return this.reservedTimers[order.id] || '';
  }

  isUrgent(order: Order): boolean {
    if (!order.expiresAt) return false;
    const diff = new Date(order.expiresAt).getTime() - Date.now();
    return diff < 120000; // less than 2 minutes
  }

  goToOrder(order: Order) {
    this.router.navigateByUrl('/order/' + order.orderNumber);
  }

  cancelReservedOrder(order: Order) {
    this.api.cancelOrder(order.id).subscribe({
      next: () => {
        this.reservedOrders = this.reservedOrders.filter(o => o.id !== order.id);
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Fout bij annuleren';
      }
    });
  }

  updateQuantity(item: CartItem, quantity: number) {
    if (quantity < 1) quantity = 1;
    if (quantity > Math.min(item.maxTicketsPerOrder, item.availableTickets)) {
      quantity = Math.min(item.maxTicketsPerOrder, item.availableTickets);
    }
    this.cart.updateQuantity(item.eventId, quantity);
  }

  removeItem(eventId: number) {
    this.cart.removeItem(eventId);
  }

  getItemTotal(item: CartItem): number {
    return (item.ticketPrice + item.serviceFee) * item.quantity;
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('nl-NL', {
      weekday: 'short', year: 'numeric', month: 'long', day: 'numeric',
      hour: '2-digit', minute: '2-digit'
    });
  }

  checkout(item: CartItem) {
    if (!this.userAuth.isLoggedIn) {
      this.router.navigate(['/login'], { state: { returnUrl: '/winkelwagen' } });
      return;
    }

    this.processing[item.eventId] = true;
    this.errorMessage = '';

    const orderRequest: OrderRequest = {
      eventId: item.eventId,
      buyerFirstName: this.userAuth.firstName || '',
      buyerLastName: this.userAuth.lastName || '',
      buyerEmail: this.userAuth.email || '',
      buyerPhone: this.userAuth.phone || '',
      quantity: item.quantity
    };

    this.api.createOrder(orderRequest).subscribe({
      next: (order) => {
        this.cart.removeItem(item.eventId);
        this.processing[item.eventId] = false;
        // Add to reserved orders instead of navigating away
        this.reservedOrders.push(order);
        if (!this.timerInterval) this.startTimers();
      },
      error: (err) => {
        this.errorMessage = err.error?.error || 'Er is een fout opgetreden bij het bestellen';
        this.processing[item.eventId] = false;
      }
    });
  }

  checkoutAll() {
    if (!this.userAuth.isLoggedIn) {
      this.router.navigate(['/login'], { state: { returnUrl: '/winkelwagen' } });
      return;
    }

    this.errorMessage = '';
    this.processNext([...this.items]);
  }

  private processNext(remaining: CartItem[]) {
    if (remaining.length === 0) return;

    const item = remaining.shift()!;
    this.processing[item.eventId] = true;

    const orderRequest: OrderRequest = {
      eventId: item.eventId,
      buyerFirstName: this.userAuth.firstName || '',
      buyerLastName: this.userAuth.lastName || '',
      buyerEmail: this.userAuth.email || '',
      buyerPhone: this.userAuth.phone || '',
      quantity: item.quantity
    };

    this.api.createOrder(orderRequest).subscribe({
      next: (order) => {
        this.cart.removeItem(item.eventId);
        this.processing[item.eventId] = false;
        this.reservedOrders.push(order);
        if (!this.timerInterval) this.startTimers();
        this.processNext(remaining);
      },
      error: (err) => {
        this.errorMessage = `Fout bij "${item.eventName}": ${err.error?.error || 'onbekende fout'}`;
        this.processing[item.eventId] = false;
      }
    });
  }
}
