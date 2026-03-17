package nl.ticketservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponseDTO(
        Long id,
        String orderNumber,
        String buyerName,
        String buyerEmail,
        String buyerPhone,
        Integer quantity,
        BigDecimal totalPrice,
        String status,
        String eventName,
        Long eventId,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime expiresAt,
        List<TicketDTO> tickets
) {}
