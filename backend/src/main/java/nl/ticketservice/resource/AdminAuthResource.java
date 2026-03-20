package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.UserLoginDTO;
import nl.ticketservice.dto.UserResponseDTO;
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
