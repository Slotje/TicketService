package nl.ticketservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketDTO(
        Long id,
        String ticketCode,
        String qrCodeData,
        String ticketType,
        String categoryName,
        LocalDate validDate,
        LocalDate validEndDate,
        boolean scanned,
        LocalDateTime scannedAt,
        LocalDateTime createdAt
) {}
