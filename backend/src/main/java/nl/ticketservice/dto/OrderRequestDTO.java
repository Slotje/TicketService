package nl.ticketservice.dto;

import jakarta.validation.constraints.*;

public record OrderRequestDTO(
        @NotNull(message = "Event ID is verplicht")
        Long eventId,
        @NotBlank(message = "Naam koper is verplicht")
        @Size(min = 2, max = 100)
        String buyerName,
        @NotBlank(message = "E-mail koper is verplicht")
        @Email(message = "Ongeldig e-mailadres")
        String buyerEmail,
        @Size(max = 20)
        String buyerPhone,
        @NotNull(message = "Aantal tickets is verplicht")
        @Min(value = 1, message = "Minimaal 1 ticket")
        @Max(value = 10, message = "Maximaal 10 tickets per bestelling")
        Integer quantity
) {}
