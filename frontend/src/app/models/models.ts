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

export interface TicketCategory {
  id?: number;
  name: string;
  description?: string;
  price: number;
  serviceFee?: number;
  maxTickets?: number;
  ticketsSold?: number;
  ticketsReserved?: number;
  availableTickets?: number;
  validDate?: string;
  sortOrder?: number;
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
  ticketCategories?: TicketCategory[];
}

export interface OrderRequest {
  eventId: number;
  ticketCategoryId?: number;
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
  buyerStreet?: string;
  buyerHouseNumber?: string;
  buyerPostalCode?: string;
  buyerCity?: string;
  quantity: number;
  ticketPrice: number;
  serviceFeePerTicket: number;
  totalServiceFee: number;
  totalPrice: number;
  status: string;
  eventName: string;
  eventId: number;
  ticketCategoryName?: string;
  ticketCategoryId?: number;
  createdAt: string;
  confirmedAt?: string;
  expiresAt?: string;
  tickets: Ticket[];
}

export interface BuyerDetails {
  buyerStreet: string;
  buyerHouseNumber: string;
  buyerPostalCode: string;
  buyerCity: string;
}

export interface Ticket {
  id: number;
  ticketCode: string;
  qrCodeData: string;
  ticketType?: string;
  categoryName?: string;
  validDate?: string;
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
