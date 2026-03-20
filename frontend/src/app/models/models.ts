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
  physicalTickets?: number;
  ticketPrice: number;
  serviceFee?: number;
  effectiveOnlineServiceFee?: number;
  maxTicketsPerOrder: number;
  onlineTickets?: number;
  ticketsSold?: number;
  ticketsReserved?: number;
  availableTickets?: number;
  physicalTicketsSold?: number;
  availablePhysicalTickets?: number;
  totalSold?: number;
  physicalTicketsGenerated?: boolean;
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
  ticketType?: string;
  scanned: boolean;
  scannedAt?: string;
  createdAt: string;
}

export interface TicketSales {
  eventId: number;
  eventName: string;
  eventStatus: string;
  maxTickets: number;
  physicalTickets: number;
  onlineTickets: number;
  totalSold: number;
  totalRemaining: number;
  onlineSold: number;
  onlineReserved: number;
  onlineAvailable: number;
  physicalSold: number;
  physicalAvailable: number;
  physicalTicketsGenerated: boolean;
  ticketPrice: number;
  serviceFeePerTicket: number;
  effectiveOnlineServiceFee: number;
  totalOnlineRevenue: number;
  totalPhysicalRevenue: number;
  totalServiceFeeRevenue: number;
  totalRevenue: number;
  ticketsScanned: number;
  ticketsNotScanned: number;
}
