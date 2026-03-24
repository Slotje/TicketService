import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BuyerDetails, Customer, Event, Order, OrderRequest, Ticket, TicketSales } from '../models/models';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private baseUrl = '/api';

  constructor(private http: HttpClient) {}

  // Customers
  getCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(`${this.baseUrl}/customers`);
  }

  getCustomer(id: number): Observable<Customer> {
    return this.http.get<Customer>(`${this.baseUrl}/customers/${id}`);
  }

  createCustomer(customer: Customer): Observable<Customer> {
    return this.http.post<Customer>(`${this.baseUrl}/customers`, customer);
  }

  updateCustomer(id: number, customer: Customer): Observable<Customer> {
    return this.http.put<Customer>(`${this.baseUrl}/customers/${id}`, customer);
  }

  deleteCustomer(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/customers/${id}`);
  }

  resendCustomerInvite(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/customers/${id}/resend-invite`, {});
  }

  getCustomerBySlug(slug: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.baseUrl}/customers/slug/${slug}`);
  }

  // Customer's own events
  getMyEvents(): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.baseUrl}/events/my`);
  }

  createMyEvent(event: Event): Observable<Event> {
    return this.http.post<Event>(`${this.baseUrl}/events/my`, event);
  }

  updateMyEvent(id: number, event: Event): Observable<Event> {
    return this.http.put<Event>(`${this.baseUrl}/events/my/${id}`, event);
  }

  updateMyEventStatus(id: number, status: string): Observable<Event> {
    return this.http.patch<Event>(`${this.baseUrl}/events/my/${id}/status`, { status });
  }

  deleteMyEvent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/events/my/${id}`);
  }

  // Customer: Physical tickets & Sales
  generateMyPhysicalTickets(eventId: number): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/events/my/${eventId}/physical-tickets/generate`, {}, {
      responseType: 'blob'
    });
  }

  downloadMyPhysicalTicketsPdf(eventId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/events/my/${eventId}/physical-tickets/pdf`, {
      responseType: 'blob'
    });
  }

  markMyPhysicalTicketsSold(eventId: number, quantity: number): Observable<Event> {
    return this.http.post<Event>(`${this.baseUrl}/events/my/${eventId}/physical-tickets/sell`, { quantity });
  }

  adjustMyPhysicalTicketsSold(eventId: number, count: number): Observable<Event> {
    return this.http.put<Event>(`${this.baseUrl}/events/my/${eventId}/physical-tickets/sold-count`, { count });
  }

  getMyTicketSales(eventId: number): Observable<TicketSales> {
    return this.http.get<TicketSales>(`${this.baseUrl}/events/my/${eventId}/sales`);
  }

  // Events
  getEvents(): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.baseUrl}/events`);
  }

  getPublishedEvents(): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.baseUrl}/events/published`);
  }

  getEventsByCustomer(customerId: number): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.baseUrl}/events/customer/${customerId}`);
  }

  getEvent(id: number): Observable<Event> {
    return this.http.get<Event>(`${this.baseUrl}/events/${id}`);
  }

  createEvent(event: Event): Observable<Event> {
    return this.http.post<Event>(`${this.baseUrl}/events`, event);
  }

  updateEvent(id: number, event: Event): Observable<Event> {
    return this.http.put<Event>(`${this.baseUrl}/events/${id}`, event);
  }

  updateEventStatus(id: number, status: string): Observable<Event> {
    return this.http.patch<Event>(`${this.baseUrl}/events/${id}/status`, { status });
  }

  deleteEvent(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/events/${id}`);
  }

  // Physical tickets & Sales (admin)
  generatePhysicalTickets(eventId: number): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/events/${eventId}/physical-tickets/generate`, {}, {
      responseType: 'blob'
    });
  }

  downloadPhysicalTicketsPdf(eventId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/events/${eventId}/physical-tickets/pdf`, {
      responseType: 'blob'
    });
  }

  markPhysicalTicketsSold(eventId: number, quantity: number): Observable<Event> {
    return this.http.post<Event>(`${this.baseUrl}/events/${eventId}/physical-tickets/sell`, { quantity });
  }

  adjustPhysicalTicketsSold(eventId: number, count: number): Observable<Event> {
    return this.http.put<Event>(`${this.baseUrl}/events/${eventId}/physical-tickets/sold-count`, { count });
  }

  getTicketSales(eventId: number): Observable<TicketSales> {
    return this.http.get<TicketSales>(`${this.baseUrl}/events/${eventId}/sales`);
  }

  // Orders
  getOrdersByEvent(eventId: number): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.baseUrl}/orders/event/${eventId}`);
  }

  getOrder(id: number): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/orders/${id}`);
  }

  getOrderByNumber(orderNumber: string): Observable<Order> {
    return this.http.get<Order>(`${this.baseUrl}/orders/number/${orderNumber}`);
  }

  getOrdersByEmail(email: string): Observable<Order[]> {
    return this.http.get<Order[]>(`${this.baseUrl}/orders/email/${encodeURIComponent(email)}`);
  }

  createOrder(order: OrderRequest): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/orders`, order);
  }

  updateOrderDetails(id: number, details: BuyerDetails): Observable<Order> {
    return this.http.put<Order>(`${this.baseUrl}/orders/${id}/details`, details);
  }

  confirmOrder(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/orders/${id}/confirm`, {});
  }

  cancelOrder(id: number): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/orders/${id}/cancel`, {});
  }

  downloadOrderPdf(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/orders/${id}/pdf`, {
      responseType: 'blob'
    });
  }

  getQrCodeUrl(qrCodeData: string): string {
    return `${this.baseUrl}/orders/ticket/${qrCodeData}/qr`;
  }

  scanTicket(qrCodeData: string, eventId: number): Observable<Ticket> {
    return this.http.post<Ticket>(`${this.baseUrl}/orders/scan/${encodeURIComponent(qrCodeData)}?eventId=${eventId}`, {});
  }
}
