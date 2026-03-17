package nl.ticketservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateScannerUserDTO(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(min = 4, max = 100) String password,
        @Size(max = 200) String displayName
) {}
