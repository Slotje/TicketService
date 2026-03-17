package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.RegisterDTO;
import nl.ticketservice.dto.UserLoginDTO;
import nl.ticketservice.dto.UserResponseDTO;
import nl.ticketservice.entity.User;
import nl.ticketservice.service.UserAuthService;

@Path("/api/user/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserAuthResource {

    @Inject
    UserAuthService userAuthService;

    @POST
    @Path("/register")
    public UserResponseDTO register(@Valid RegisterDTO dto) {
        User user = userAuthService.register(dto.email(), dto.password(), dto.name());
        String token = userAuthService.login(dto.email(), dto.password());
        return new UserResponseDTO(token, user.email, user.name);
    }

    @POST
    @Path("/login")
    public UserResponseDTO login(@Valid UserLoginDTO dto) {
        String token = userAuthService.login(dto.email(), dto.password());
        User user = userAuthService.validateToken(token);
        return new UserResponseDTO(token, user.email, user.name);
    }

    @GET
    @Path("/verify")
    public UserResponseDTO verify(@HeaderParam("Authorization") String authHeader) {
        User user = userAuthService.requireUser(authHeader);
        return new UserResponseDTO(null, user.email, user.name);
    }
}
