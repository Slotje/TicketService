import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export interface CartItem {
  eventId: number;
  eventName: string;
  eventDate: string;
  location: string;
  ticketPrice: number;
  serviceFee: number;
  quantity: number;
  maxTicketsPerOrder: number;
  availableTickets: number;
  imageUrl?: string;
  addedAt: number;
}

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly STORAGE_KEY = 'ts_cart';
  private items$ = new BehaviorSubject<CartItem[]>(this.loadFromStorage());

  get cartItems$(): Observable<CartItem[]> {
    return this.items$.asObservable();
  }

  get cartItems(): CartItem[] {
    return this.items$.value;
  }

  get itemCount(): number {
    return this.items$.value.reduce((sum, item) => sum + item.quantity, 0);
  }

  get totalPrice(): number {
    return this.items$.value.reduce((sum, item) =>
      sum + (item.ticketPrice + item.serviceFee) * item.quantity, 0);
  }

  addItem(item: Omit<CartItem, 'addedAt'>): void {
    const items = [...this.items$.value];
    const existing = items.findIndex(i => i.eventId === item.eventId);

    if (existing >= 0) {
      items[existing].quantity = item.quantity;
    } else {
      items.push({ ...item, addedAt: Date.now() });
    }

    this.save(items);
  }

  updateQuantity(eventId: number, quantity: number): void {
    const items = this.items$.value.map(item =>
      item.eventId === eventId ? { ...item, quantity } : item
    );
    this.save(items);
  }

  removeItem(eventId: number): void {
    const items = this.items$.value.filter(i => i.eventId !== eventId);
    this.save(items);
  }

  clearCart(): void {
    this.save([]);
  }

  hasItem(eventId: number): boolean {
    return this.items$.value.some(i => i.eventId === eventId);
  }

  getItem(eventId: number): CartItem | undefined {
    return this.items$.value.find(i => i.eventId === eventId);
  }

  private save(items: CartItem[]): void {
    localStorage.setItem(this.STORAGE_KEY, JSON.stringify(items));
    this.items$.next(items);
  }

  private loadFromStorage(): CartItem[] {
    try {
      const data = localStorage.getItem(this.STORAGE_KEY);
      return data ? JSON.parse(data) : [];
    } catch {
      return [];
    }
  }
}
