package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.LoginDTO;
import nl.ticketservice.dto.LoginResponseDTO;
import nl.ticketservice.entity.AdminUser;
import nl.ticketservice.service.AdminAuthService;

@Path("/api/admin/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminAuthResource {

    @Inject
    AdminAuthService adminAuthService;

    @POST
    @Path("/login")
    public LoginResponseDTO login(@Valid LoginDTO dto) {
        String token = adminAuthService.login(dto.username(), dto.password());
        AdminUser user = adminAuthService.validateToken(token);
        return new LoginResponseDTO(token, user.displayName, user.username);
    }

    @GET
    @Path("/verify")
    public LoginResponseDTO verify(@HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        String token = authHeader.substring(7);
        AdminUser user = adminAuthService.validateToken(token);
        return new LoginResponseDTO(token, user.displayName, user.username);
    }
}
