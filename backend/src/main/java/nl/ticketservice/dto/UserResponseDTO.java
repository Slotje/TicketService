package nl.ticketservice.dto;

public record UserResponseDTO(
        String token,
        String email,
        String name
) {}
