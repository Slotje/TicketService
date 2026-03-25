package nl.ticketservice.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TicketCategoryDTO(
        Long id,
        @NotBlank(message = "Naam is verplicht")
        @Size(max = 200)
        String name,
        @Size(max = 500)
        String description,
        @NotNull(message = "Prijs is verplicht")
        @DecimalMin("0.00")
        BigDecimal price,
        @DecimalMin("0.00")
        BigDecimal serviceFee,
        @Min(0)
        Integer maxTickets,
        Integer ticketsSold,
        Integer ticketsReserved,
        Integer availableTickets,
        LocalDate validDate,
        LocalDate validEndDate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer sortOrder,
        boolean active,
        String imageUrl,
        Integer physicalTickets,
        Integer physicalTicketsSold,
        boolean physicalTicketsGenerated,
        boolean showAvailability
) {}
