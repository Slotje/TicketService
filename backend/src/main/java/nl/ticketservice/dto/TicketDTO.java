package nl.ticketservice.dto;

import java.time.LocalDateTime;

public record TicketDTO(
        Long id,
        String ticketCode,
        String qrCodeData,
        String ticketType,
        boolean scanned,
        LocalDateTime scannedAt,
        LocalDateTime createdAt
) {}
