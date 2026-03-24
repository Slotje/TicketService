package nl.ticketservice.dto;

import jakarta.validation.constraints.*;

public record OrderRequestDTO(
        @NotNull(message = "Event ID is verplicht")
        Long eventId,
        Long ticketCategoryId,
        @NotBlank(message = "Voornaam koper is verplicht")
        @Size(min = 1, max = 100)
        String buyerFirstName,
        @NotBlank(message = "Achternaam koper is verplicht")
        @Size(min = 1, max = 100)
        String buyerLastName,
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
