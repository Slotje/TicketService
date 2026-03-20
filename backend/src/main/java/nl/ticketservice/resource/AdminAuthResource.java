package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.RegisterDTO;
import nl.ticketservice.dto.UserLoginDTO;
import nl.ticketservice.dto.UserResponseDTO;
import nl.ticketservice.entity.AdminUser;
import nl.ticketservice.exception.TicketServiceException;
import nl.ticketservice.service.AdminAuthService;

@Path("/api/admin/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAuthResource {

    @Inject
    AdminAuthService adminAuthService;

    @GET
    @Path("/setup")
    public java.util.Map<String, Boolean> needsSetup() {
        return java.util.Map.of("needsSetup", AdminUser.count() == 0);
    }

    @POST
    @Path("/setup")
    public UserResponseDTO setup(@Valid RegisterDTO dto) {
        if (AdminUser.count() > 0) {
            throw new TicketServiceException("Er bestaat al een admin account. Gebruik /login.", 400);
        }
        AdminUser user = adminAuthService.createUser(dto.email(), dto.password(), dto.name());
        String token = adminAuthService.login(dto.email(), dto.password());
        return new UserResponseDTO(token, user.email, user.displayName);
    }

    @POST
    @Path("/login")
    public UserResponseDTO login(@Valid UserLoginDTO dto) {
        String token = adminAuthService.login(dto.email(), dto.password());
        AdminUser user = adminAuthService.validateToken(token);
        return new UserResponseDTO(token, user.email, user.displayName);
    }

    @GET
    @Path("/verify")
    public UserResponseDTO verify(@HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        String token = authHeader.substring(7);
        AdminUser user = adminAuthService.validateToken(token);
        return new UserResponseDTO(token, user.email, user.displayName);
    }
}
