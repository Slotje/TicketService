package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.OrderRequestDTO;
import nl.ticketservice.dto.OrderResponseDTO;
import nl.ticketservice.dto.TicketDTO;
import nl.ticketservice.entity.*;
import nl.ticketservice.exception.TicketServiceException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class OrderService {

    @ConfigProperty(name = "ticket.reservation.timeout-minutes", defaultValue = "10")
    int reservationTimeoutMinutes;

    @ConfigProperty(name = "ticket.order.max-tickets", defaultValue = "10")
    int maxTicketsPerOrder;

    @Inject
    QrCodeService qrCodeService;

    @Inject
    EmailService emailService;

    public List<OrderResponseDTO> getOrdersByEvent(Long eventId) {
        return TicketOrder.<TicketOrder>list("event.id", eventId).stream()
                .map(this::toDTO)
                .toList();
    }

    public OrderResponseDTO getOrder(Long id) {
        TicketOrder order = TicketOrder.findById(id);
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }
        return toDTO(order);
    }

    public OrderResponseDTO getOrderByNumber(String orderNumber) {
        TicketOrder order = TicketOrder.find("orderNumber", orderNumber).firstResult();
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }
        return toDTO(order);
    }

    public List<OrderResponseDTO> getOrdersByEmail(String email) {
        return TicketOrder.<TicketOrder>list("buyerEmail", email).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO dto) {
        Event event = Event.findById(dto.eventId());
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (event.status != EventStatus.PUBLISHED) {
            throw new TicketServiceException("Evenement is niet beschikbaar voor ticketverkoop", 400);
        }

        int effectiveMax = Math.min(dto.quantity(), Math.min(maxTicketsPerOrder, event.maxTicketsPerOrder));
        if (dto.quantity() > effectiveMax) {
            throw new TicketServiceException(
                    "Maximaal " + effectiveMax + " tickets per bestelling", 400
            );
        }

        if (!event.hasAvailableTickets(dto.quantity())) {
            int available = event.getAvailableTickets();
            if (available <= 0) {
                throw new TicketServiceException("Dit evenement is uitverkocht", 400);
            }
            throw new TicketServiceException(
                    "Er zijn slechts " + available + " tickets beschikbaar", 400
            );
        }

        // Reserve tickets
        event.ticketsReserved += dto.quantity();

        TicketOrder order = new TicketOrder();
        order.buyerName = dto.buyerName();
        order.buyerEmail = dto.buyerEmail();
        order.buyerPhone = dto.buyerPhone();
        order.quantity = dto.quantity();
        order.serviceFeePerTicket = event.serviceFee;
        order.totalServiceFee = event.serviceFee.multiply(BigDecimal.valueOf(dto.quantity()));
        BigDecimal ticketTotal = event.ticketPrice.multiply(BigDecimal.valueOf(dto.quantity()));
        order.totalPrice = ticketTotal.add(order.totalServiceFee);
        order.status = OrderStatus.RESERVED;
        order.event = event;
        order.expiresAt = LocalDateTime.now().plusMinutes(reservationTimeoutMinutes);
        order.persist();

        // Generate tickets
        for (int i = 0; i < dto.quantity(); i++) {
            Ticket ticket = new Ticket();
            ticket.order = order;
            ticket.persist();
            order.tickets.add(ticket);
        }

        return toDTO(order);
    }

    @Transactional
    public OrderResponseDTO confirmOrder(Long orderId) {
        TicketOrder order = TicketOrder.findById(orderId);
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }

        if (order.status != OrderStatus.RESERVED) {
            throw new TicketServiceException("Bestelling kan niet worden bevestigd (status: " + order.status + ")", 400);
        }

        if (order.expiresAt != null && order.expiresAt.isBefore(LocalDateTime.now())) {
            cancelExpiredOrder(order);
            throw new TicketServiceException("Reservering is verlopen. Plaats een nieuwe bestelling.", 400);
        }

        order.status = OrderStatus.CONFIRMED;
        order.confirmedAt = LocalDateTime.now();
        order.expiresAt = null;

        Event event = order.event;
        event.ticketsReserved -= order.quantity;
        event.ticketsSold += order.quantity;

        if (event.getAvailableTickets() <= 0) {
            event.status = EventStatus.SOLD_OUT;
        }

        order.lastEmailAttempt = LocalDateTime.now();
        order.emailRetryCount = 1;
        boolean emailSuccess = emailService.sendOrderConfirmation(order);
        order.emailSent = emailSuccess;

        return toDTO(order);
    }

    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId) {
        TicketOrder order = TicketOrder.findById(orderId);
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }

        if (order.status == OrderStatus.CANCELLED || order.status == OrderStatus.EXPIRED) {
            throw new TicketServiceException("Bestelling is al geannuleerd", 400);
        }

        if (order.status == OrderStatus.RESERVED) {
            order.event.ticketsReserved -= order.quantity;
        } else if (order.status == OrderStatus.CONFIRMED) {
            order.event.ticketsSold -= order.quantity;
            if (order.event.status == EventStatus.SOLD_OUT) {
                order.event.status = EventStatus.PUBLISHED;
            }
        }

        order.status = OrderStatus.CANCELLED;
        return toDTO(order);
    }

    private void cancelExpiredOrder(TicketOrder order) {
        order.event.ticketsReserved -= order.quantity;
        order.status = OrderStatus.EXPIRED;
    }

    @Transactional
    public TicketDTO scanTicket(String qrCodeData, Long eventId) {
        // Verify HMAC signature if present
        if (qrCodeData.contains("|")) {
            if (!qrCodeService.verifyQrCode(qrCodeData)) {
                throw new TicketServiceException("Ongeldige QR code: handtekening komt niet overeen", 400);
            }
            qrCodeData = qrCodeService.extractTicketData(qrCodeData);
        }

        Ticket ticket = Ticket.find("qrCodeData", qrCodeData).firstResult();
        if (ticket == null) {
            throw new TicketServiceException("Ticket niet gevonden", 404);
        }

        if (ticket.order.status != OrderStatus.CONFIRMED) {
            throw new TicketServiceException("Ticket behoort tot een niet-bevestigde bestelling", 400);
        }

        // Validate ticket belongs to the scanned event
        if (eventId != null && !ticket.order.event.id.equals(eventId)) {
            throw new TicketServiceException("Dit ticket hoort niet bij dit evenement", 400);
        }

        if (ticket.scanned) {
            throw new TicketServiceException(
                    "Ticket is al gescand op " + ticket.scannedAt, 400
            );
        }

        ticket.scanned = true;
        ticket.scannedAt = LocalDateTime.now();

        return toTicketDTO(ticket);
    }

    private OrderResponseDTO toDTO(TicketOrder o) {
        List<TicketDTO> ticketDTOs = o.tickets.stream()
                .map(this::toTicketDTO)
                .toList();

        return new OrderResponseDTO(
                o.id, o.orderNumber, o.buyerName, o.buyerEmail, o.buyerPhone,
                o.quantity, o.event.ticketPrice, o.serviceFeePerTicket, o.totalServiceFee,
                o.totalPrice, o.status.name(), o.event.name,
                o.event.id, o.createdAt, o.confirmedAt, o.expiresAt, ticketDTOs
        );
    }

    private TicketDTO toTicketDTO(Ticket t) {
        return new TicketDTO(t.id, t.ticketCode, t.qrCodeData, t.scanned, t.scannedAt, t.createdAt);
    }
}
