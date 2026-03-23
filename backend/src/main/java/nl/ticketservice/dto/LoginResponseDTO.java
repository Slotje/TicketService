package nl.ticketservice.dto;

public record LoginResponseDTO(
        String token,
        String displayName,
        String username
) {}
