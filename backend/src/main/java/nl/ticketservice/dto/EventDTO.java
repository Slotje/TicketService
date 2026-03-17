package nl.ticketservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record EventDTO(
        Long id,
        @NotBlank(message = "Evenementnaam is verplicht")
        @Size(min = 2, max = 200)
        String name,
        @Size(max = 2000)
        String description,
        @NotNull(message = "Evenementdatum is verplicht")
        LocalDateTime eventDate,
        LocalDateTime endDate,
        @NotBlank(message = "Locatie is verplicht")
        String location,
        String address,
        @NotNull(message = "Maximaal aantal tickets is verplicht")
        @Min(1) @Max(100000)
        Integer maxTickets,
        @NotNull(message = "Prijs is verplicht")
        @DecimalMin("0.00")
        BigDecimal ticketPrice,
        @Min(1) @Max(10)
        Integer maxTicketsPerOrder,
        Integer ticketsSold,
        Integer ticketsReserved,
        Integer availableTickets,
        String imageUrl,
        String status,
        Long customerId,
        String customerName
) {}
