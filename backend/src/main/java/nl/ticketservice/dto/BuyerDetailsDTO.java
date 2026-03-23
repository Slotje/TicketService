package nl.ticketservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BuyerDetailsDTO(
        @NotBlank(message = "Straat is verplicht")
        @Size(max = 200)
        String buyerStreet,

        @NotBlank(message = "Huisnummer is verplicht")
        @Size(max = 10)
        String buyerHouseNumber,

        @NotBlank(message = "Postcode is verplicht")
        @Size(max = 10)
        String buyerPostalCode,

        @NotBlank(message = "Plaats is verplicht")
        @Size(max = 100)
        String buyerCity
) {}
