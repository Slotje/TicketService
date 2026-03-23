package nl.ticketservice.dto;

public record UserResponseDTO(
        String token,
        String email,
        String firstName,
        String lastName,
        String phone
) {}
