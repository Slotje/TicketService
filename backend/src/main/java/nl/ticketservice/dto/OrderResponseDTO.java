package nl.ticketservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDTO(
        Long id,
        String orderNumber,
        String buyerFirstName,
        String buyerLastName,
        String buyerEmail,
        String buyerPhone,
        String buyerStreet,
        String buyerHouseNumber,
        String buyerPostalCode,
        String buyerCity,
        Integer quantity,
        BigDecimal ticketPrice,
        BigDecimal serviceFeePerTicket,
        BigDecimal totalServiceFee,
        BigDecimal totalPrice,
        String status,
        String eventName,
        Long eventId,
        String ticketCategoryName,
        Long ticketCategoryId,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime expiresAt,
        List<TicketDTO> tickets
) {}
