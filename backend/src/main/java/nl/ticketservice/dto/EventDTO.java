package nl.ticketservice.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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
        @Min(0) @Max(100000)
        Integer maxTickets,
        @Min(0)
        Integer physicalTickets,
        @DecimalMin("0.00")
        BigDecimal ticketPrice,
        @DecimalMin("0.00")
        BigDecimal serviceFee,
        BigDecimal effectiveOnlineServiceFee,
        @Min(1) @Max(10)
        Integer maxTicketsPerOrder,
        Integer onlineTickets,
        Integer ticketsSold,
        Integer ticketsReserved,
        Integer availableTickets,
        Integer physicalTicketsSold,
        Integer availablePhysicalTickets,
        Integer totalSold,
        boolean physicalTicketsGenerated,
        boolean showAvailability,
        String imageUrl,
        String status,
        Long customerId,
        String customerName,
        List<TicketCategoryDTO> ticketCategories
) {}
