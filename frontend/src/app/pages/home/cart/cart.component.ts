import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService, CartItem } from '../../../services/cart.service';
import { ApiService } from '../../../services/api.service';
import { UserAuthService } from '../../../services/user-auth.service';
import { OrderRequest } from '../../../models/models';
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
  processing: Record<number, boolean> = {};
  errorMessage = '';
  successMessage = '';
  private sub?: Subscription;

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
  }

  ngOnDestroy() {
    this.sub?.unsubscribe();
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
        this.router.navigateByUrl('/order/' + order.orderNumber);
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

    if (this.items.length === 1) {
      this.checkout(this.items[0]);
      return;
    }

    // For multiple items, process sequentially
    this.errorMessage = '';
    this.processNext([...this.items], []);
  }

  private processNext(remaining: CartItem[], orderNumbers: string[]) {
    if (remaining.length === 0) {
      if (orderNumbers.length === 1) {
        this.router.navigateByUrl('/order/' + orderNumbers[0]);
      } else {
        this.cart.clearCart();
        this.router.navigate(['/my-tickets']);
      }
      return;
    }

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
        orderNumbers.push(order.orderNumber);
        this.processNext(remaining, orderNumbers);
      },
      error: (err) => {
        this.errorMessage = `Fout bij bestelling voor "${item.eventName}": ${err.error?.error || 'onbekende fout'}`;
        this.processing[item.eventId] = false;
      }
    });
  }
}
