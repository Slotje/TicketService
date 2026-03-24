import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, combineLatest, map } from 'rxjs';
import { Order } from '../models/models';

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

export interface CartState {
  totalCount: number;
  totalPrice: number;
  shortestTimer: string;
  isTimerUrgent: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly STORAGE_KEY = 'ts_cart';
  private items$ = new BehaviorSubject<CartItem[]>(this.loadFromStorage());
  private reservedOrders$ = new BehaviorSubject<Order[]>([]);
  private timerTick$ = new BehaviorSubject<number>(0);
  private timerInterval: any;

  reservedTimers: Record<number, string> = {};

  /** Reactive cart state observable for use in templates */
  state$: Observable<CartState> = combineLatest([
    this.items$, this.reservedOrders$, this.timerTick$
  ]).pipe(
    map(([items, orders]) => {
      const itemCount = items.reduce((sum, item) => sum + item.quantity, 0);
      const cartTotal = items.reduce((sum, item) =>
        sum + (item.ticketPrice + item.serviceFee) * item.quantity, 0);
      const reservedTotal = orders.reduce((sum, order) =>
        sum + (order.totalPrice || 0), 0);

      let shortestTimer = '';
      let isTimerUrgent = false;
      const now = Date.now();
      if (orders.length > 0) {
        let minDiff = Infinity;
        for (const order of orders) {
          if (!order.expiresAt) continue;
          const diff = new Date(order.expiresAt).getTime() - now;
          if (diff > 0 && diff < minDiff) minDiff = diff;
          if (diff > 0 && diff < 120000) isTimerUrgent = true;
        }
        if (minDiff !== Infinity) {
          const m = Math.floor(minDiff / 60000);
          const s = Math.floor((minDiff % 60000) / 1000);
          shortestTimer = `${m}:${s.toString().padStart(2, '0')}`;
        }
      }

      return {
        totalCount: itemCount + orders.length,
        totalPrice: cartTotal + reservedTotal,
        shortestTimer,
        isTimerUrgent
      };
    })
  );

  get cartItems$(): Observable<CartItem[]> {
    return this.items$.asObservable();
  }

  get cartItems(): CartItem[] {
    return this.items$.value;
  }

  get reservedOrders(): Order[] {
    return this.reservedOrders$.value;
  }

  get totalPrice(): number {
    const cartTotal = this.items$.value.reduce((sum, item) =>
      sum + (item.ticketPrice + item.serviceFee) * item.quantity, 0);
    const reservedTotal = this.reservedOrders$.value.reduce((sum, order) =>
      sum + (order.totalPrice || 0), 0);
    return cartTotal + reservedTotal;
  }

  setReservedOrders(orders: Order[]) {
    this.reservedOrders$.next(orders);
    if (orders.length > 0) {
      this.startTimers();
    } else {
      this.stopTimers();
    }
  }

  addReservedOrder(order: Order) {
    const current = [...this.reservedOrders$.value];
    current.push(order);
    this.setReservedOrders(current);
  }

  removeReservedOrder(orderId: number) {
    const filtered = this.reservedOrders$.value.filter(o => o.id !== orderId);
    this.setReservedOrders(filtered);
  }

  private startTimers() {
    this.stopTimers();
    this.updateTimers();
    this.timerInterval = setInterval(() => this.updateTimers(), 1000);
  }

  private stopTimers() {
    if (this.timerInterval) {
      clearInterval(this.timerInterval);
      this.timerInterval = null;
    }
  }

  private updateTimers() {
    const now = Date.now();
    for (const order of this.reservedOrders$.value) {
      if (!order.expiresAt) continue;
      const diff = new Date(order.expiresAt).getTime() - now;
      if (diff <= 0) {
        this.reservedTimers[order.id] = 'Verlopen';
      } else {
        const m = Math.floor(diff / 60000);
        const s = Math.floor((diff % 60000) / 1000);
        this.reservedTimers[order.id] = `${m}:${s.toString().padStart(2, '0')}`;
      }
    }
    // Remove expired orders
    const filtered = this.reservedOrders$.value.filter(o =>
      this.reservedTimers[o.id] !== 'Verlopen'
    );
    if (filtered.length !== this.reservedOrders$.value.length) {
      this.reservedOrders$.next(filtered);
      if (filtered.length === 0) this.stopTimers();
    }
    // Trigger state$ recalculation
    this.timerTick$.next(now);
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
