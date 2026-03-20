export interface Customer {
  id?: number;
  companyName: string;
  contactPerson: string;
  email: string;
  phone?: string;
  logoUrl?: string;
  primaryColor?: string;
  secondaryColor?: string;
  website?: string;
  active: boolean;
}

export interface Event {
  id?: number;
  name: string;
  description?: string;
  eventDate: string;
  endDate?: string;
  location: string;
  address?: string;
  maxTickets: number;
  ticketPrice: number;
  serviceFee?: number;
  maxTicketsPerOrder: number;
  ticketsSold?: number;
  ticketsReserved?: number;
  availableTickets?: number;
  imageUrl?: string;
  status: string;
  customerId: number;
  customerName?: string;
}

export interface OrderRequest {
  eventId: number;
  buyerFirstName: string;
  buyerLastName: string;
  buyerEmail: string;
  buyerPhone?: string;
  quantity: number;
}

export interface Order {
  id: number;
  orderNumber: string;
  buyerFirstName: string;
  buyerLastName: string;
  buyerEmail: string;
  buyerPhone?: string;
  quantity: number;
  totalPrice: number;
  status: string;
  eventName: string;
  eventId: number;
  createdAt: string;
  confirmedAt?: string;
  expiresAt?: string;
  tickets: Ticket[];
}

export interface Ticket {
  id: number;
  ticketCode: string;
  qrCodeData: string;
  scanned: boolean;
  scannedAt?: string;
  createdAt: string;
}
