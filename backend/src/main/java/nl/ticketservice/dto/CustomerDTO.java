package nl.ticketservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CustomerDTO(
        Long id,
        @NotBlank(message = "Bedrijfsnaam is verplicht")
        @Size(min = 2, max = 100)
        String companyName,
        @NotBlank(message = "Contactpersoon is verplicht")
        @Size(min = 2, max = 100)
        String contactPerson,
        @NotBlank(message = "E-mail is verplicht")
        @Email(message = "Ongeldig e-mailadres")
        String email,
        String phone,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String website,
        boolean active
) {}
