package nl.ticketservice.dto;

import java.time.LocalDateTime;

public record TicketDTO(
        Long id,
        String ticketCode,
        String qrCodeData,
        boolean scanned,
        LocalDateTime scannedAt,
        LocalDateTime createdAt
) {}
