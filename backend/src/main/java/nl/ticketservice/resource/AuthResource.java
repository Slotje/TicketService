package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.*;
import nl.ticketservice.entity.ScannerUser;
import nl.ticketservice.service.AuthService;

import java.util.List;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @POST
    @Path("/login")
    public LoginResponseDTO login(@Valid LoginDTO dto) {
        String token = authService.login(dto.username(), dto.password());
        ScannerUser user = authService.validateToken(token);
        return new LoginResponseDTO(token, user.displayName, user.username);
    }

    @GET
    @Path("/verify")
    public LoginResponseDTO verify(@HeaderParam("Authorization") String authHeader) {
        String token = extractToken(authHeader);
        ScannerUser user = authService.validateToken(token);
        if (user == null) {
            throw new nl.ticketservice.exception.TicketServiceException("Ongeldige of verlopen token", 401);
        }
        return new LoginResponseDTO(token, user.displayName, user.username);
    }

    @GET
    @Path("/users")
    public List<ScannerUserDTO> getUsers() {
        return authService.getAllUsers().stream()
                .map(u -> new ScannerUserDTO(u.id, u.username, u.displayName, u.active, u.createdAt))
                .toList();
    }

    @POST
    @Path("/users")
    public ScannerUserDTO createUser(@Valid CreateScannerUserDTO dto) {
        ScannerUser user = authService.createUser(dto.username(), dto.password(), dto.displayName());
        return new ScannerUserDTO(user.id, user.username, user.displayName, user.active, user.createdAt);
    }

    @DELETE
    @Path("/users/{id}")
    public void deleteUser(@PathParam("id") Long id) {
        authService.deleteUser(id);
    }

    @PATCH
    @Path("/users/{id}/toggle")
    public ScannerUserDTO toggleUser(@PathParam("id") Long id) {
        ScannerUser user = authService.toggleActive(id);
        return new ScannerUserDTO(user.id, user.username, user.displayName, user.active, user.createdAt);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new nl.ticketservice.exception.TicketServiceException("Geen autorisatie token meegegeven", 401);
    }
}
