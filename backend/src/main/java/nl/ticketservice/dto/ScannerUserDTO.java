package nl.ticketservice.dto;

import java.time.LocalDateTime;

public record ScannerUserDTO(
        Long id,
        String username,
        String displayName,
        boolean active,
        LocalDateTime createdAt
) {}
