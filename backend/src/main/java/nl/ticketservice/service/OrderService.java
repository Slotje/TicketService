package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.BuyerDetailsDTO;
import nl.ticketservice.dto.OrderRequestDTO;
import nl.ticketservice.dto.OrderResponseDTO;
import nl.ticketservice.dto.TicketDTO;
import nl.ticketservice.entity.*;
import nl.ticketservice.exception.TicketServiceException;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        return TicketOrder.<TicketOrder>list("LOWER(buyerEmail) = ?1", email.toLowerCase().trim()).stream()
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

        // Resolve ticket category (optional)
        TicketCategory category = null;
        BigDecimal ticketPrice = event.ticketPrice;
        BigDecimal effectiveFee = event.getEffectiveOnlineServiceFee();

        if (dto.ticketCategoryId() != null) {
            category = TicketCategory.findById(dto.ticketCategoryId());
            if (category == null || !category.event.id.equals(event.id)) {
                throw new TicketServiceException("Ticket categorie niet gevonden", 404);
            }
            if (!category.active) {
                throw new TicketServiceException("Deze ticket categorie is niet beschikbaar", 400);
            }
            ticketPrice = category.price;
            if (category.serviceFee != null) {
                effectiveFee = category.serviceFee;
            }
            // Check category-level availability
            if (category.maxTickets > 0 && category.getAvailableTickets() < dto.quantity()) {
                int available = category.getAvailableTickets();
                if (available <= 0) {
                    throw new TicketServiceException("Deze ticket categorie is uitverkocht", 400);
                }
                throw new TicketServiceException(
                        "Er zijn slechts " + available + " tickets beschikbaar voor deze categorie", 400);
            }
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
        if (category != null) {
            category.ticketsReserved += dto.quantity();
        }

        TicketOrder order = new TicketOrder();
        order.buyerFirstName = dto.buyerFirstName();
        order.buyerLastName = dto.buyerLastName();
        order.buyerEmail = dto.buyerEmail().toLowerCase().trim();
        order.buyerPhone = dto.buyerPhone();
        order.quantity = dto.quantity();
        order.serviceFeePerTicket = effectiveFee;
        order.totalServiceFee = effectiveFee.multiply(BigDecimal.valueOf(dto.quantity()));
        BigDecimal ticketTotal = ticketPrice.multiply(BigDecimal.valueOf(dto.quantity()));
        order.totalPrice = ticketTotal.add(order.totalServiceFee);
        order.status = OrderStatus.RESERVED;
        order.event = event;
        order.expiresAt = LocalDateTime.now().plusMinutes(reservationTimeoutMinutes);
        order.persist();

        // Generate tickets
        for (int i = 0; i < dto.quantity(); i++) {
            Ticket ticket = new Ticket();
            ticket.order = order;
            ticket.event = event;
            ticket.ticketType = nl.ticketservice.entity.TicketType.ONLINE;
            if (category != null) {
                ticket.ticketCategory = category;
                ticket.validDate = category.validDate;
                ticket.categoryName = category.name;
            }
            ticket.persist();
            order.tickets.add(ticket);
        }

        return toDTO(order);
    }

    @Transactional
    public OrderResponseDTO updateBuyerDetails(Long orderId, BuyerDetailsDTO dto) {
        TicketOrder order = TicketOrder.findById(orderId);
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }
        if (order.status != OrderStatus.RESERVED) {
            throw new TicketServiceException("Gegevens kunnen alleen worden bijgewerkt bij een gereserveerde bestelling", 400);
        }
        if (order.expiresAt != null && order.expiresAt.isBefore(LocalDateTime.now())) {
            cancelExpiredOrder(order);
            throw new TicketServiceException("Reservering is verlopen. Plaats een nieuwe bestelling.", 400);
        }

        order.buyerStreet = dto.buyerStreet();
        order.buyerHouseNumber = dto.buyerHouseNumber();
        order.buyerPostalCode = dto.buyerPostalCode();
        order.buyerCity = dto.buyerCity();
        order.persist();

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

        if (isBlank(order.buyerStreet) || isBlank(order.buyerHouseNumber)
                || isBlank(order.buyerPostalCode) || isBlank(order.buyerCity)) {
            throw new TicketServiceException("Vul eerst je adresgegevens in voordat je de bestelling bevestigt", 400);
        }

        order.status = OrderStatus.CONFIRMED;
        order.confirmedAt = LocalDateTime.now();
        order.expiresAt = null;

        Event event = order.event;
        event.ticketsReserved -= order.quantity;
        event.ticketsSold += order.quantity;

        // Update category counters
        if (!order.tickets.isEmpty() && order.tickets.get(0).ticketCategory != null) {
            TicketCategory cat = order.tickets.get(0).ticketCategory;
            cat.ticketsReserved -= order.quantity;
            cat.ticketsSold += order.quantity;
        }

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

        if (order.status != OrderStatus.RESERVED) {
            throw new TicketServiceException("Alleen gereserveerde bestellingen kunnen worden geannuleerd", 400);
        }

        order.event.ticketsReserved -= order.quantity;
        // Update category counters
        if (!order.tickets.isEmpty() && order.tickets.get(0).ticketCategory != null) {
            order.tickets.get(0).ticketCategory.ticketsReserved -= order.quantity;
        }

        order.status = OrderStatus.CANCELLED;
        return toDTO(order);
    }

    private void cancelExpiredOrder(TicketOrder order) {
        order.event.ticketsReserved -= order.quantity;
        if (!order.tickets.isEmpty() && order.tickets.get(0).ticketCategory != null) {
            order.tickets.get(0).ticketCategory.ticketsReserved -= order.quantity;
        }
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

        Event event = ticket.event;
        LocalDateTime now = LocalDateTime.now();

        // Check if event is in the past
        LocalDateTime eventEnd = event.endDate != null ? event.endDate : event.eventDate.plusHours(12);
        if (now.isAfter(eventEnd)) {
            throw new TicketServiceException("Dit evenement is al afgelopen", 400);
        }

        // Check if scanning is allowed (only from event start time, with 1 hour margin)
        if (now.isBefore(event.eventDate.minusHours(1))) {
            throw new TicketServiceException(
                    "Scannen is pas mogelijk vanaf 1 uur voor de start van het evenement", 400);
        }

        // For day tickets: check if today matches the valid date
        if (ticket.validDate != null) {
            LocalDate today = now.toLocalDate();
            if (!today.equals(ticket.validDate)) {
                throw new TicketServiceException(
                        "Dit dagticket is alleen geldig op " + ticket.validDate, 400);
            }
        }

        if (ticket.scanned) {
            throw new TicketServiceException(
                    "Ticket is al gescand op " + ticket.scannedAt, 400
            );
        }

        ticket.scanned = true;
        ticket.scannedAt = now;

        return toTicketDTO(ticket);
    }

    private OrderResponseDTO toDTO(TicketOrder o) {
        List<TicketDTO> ticketDTOs = o.tickets.stream()
                .map(this::toTicketDTO)
                .toList();

        // Determine category name from first ticket
        String categoryName = null;
        Long categoryId = null;
        if (!o.tickets.isEmpty() && o.tickets.get(0).categoryName != null) {
            categoryName = o.tickets.get(0).categoryName;
            if (o.tickets.get(0).ticketCategory != null) {
                categoryId = o.tickets.get(0).ticketCategory.id;
            }
        }

        return new OrderResponseDTO(
                o.id, o.orderNumber, o.buyerFirstName, o.buyerLastName,
                o.buyerEmail, o.buyerPhone,
                o.buyerStreet, o.buyerHouseNumber, o.buyerPostalCode, o.buyerCity,
                o.quantity, o.event.ticketPrice, o.serviceFeePerTicket, o.totalServiceFee,
                o.totalPrice, o.status.name(), o.event.name,
                o.event.id, categoryName, categoryId,
                o.createdAt, o.confirmedAt, o.expiresAt, ticketDTOs
        );
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private TicketDTO toTicketDTO(Ticket t) {
        return new TicketDTO(t.id, t.ticketCode, t.qrCodeData, t.ticketType.name(),
                t.categoryName, t.validDate, t.scanned, t.scannedAt, t.createdAt);
    }
}
