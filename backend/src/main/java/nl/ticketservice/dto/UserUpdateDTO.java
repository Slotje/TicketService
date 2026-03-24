package nl.ticketservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateDTO(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Size(max = 20) String phone,
        @Size(max = 200) String street,
        @Size(max = 10) String houseNumber,
        @Size(max = 10) String postalCode,
        @Size(max = 100) String city
) {}
