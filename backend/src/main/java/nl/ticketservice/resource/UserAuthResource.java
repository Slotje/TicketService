package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.dto.RegisterDTO;
import nl.ticketservice.dto.UserLoginDTO;
import nl.ticketservice.dto.UserResponseDTO;
import nl.ticketservice.dto.UserUpdateDTO;
import nl.ticketservice.entity.User;
import nl.ticketservice.service.EmailService;
import nl.ticketservice.service.UserAuthService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api/user/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserAuthResource {

    @Inject
    UserAuthService userAuthService;

    @Inject
    EmailService emailService;

    @ConfigProperty(name = "ticket.app.base-url", defaultValue = "http://localhost:80")
    String baseUrl;

    @POST
    @Path("/register")
    public UserResponseDTO register(@Valid RegisterDTO dto) {
        User user = userAuthService.register(dto.email(), dto.password(), dto.firstName(), dto.lastName(), dto.phone());
        String token = userAuthService.login(dto.email(), dto.password());
        return toResponseDTO(token, user);
    }

    @POST
    @Path("/login")
    public UserResponseDTO login(@Valid UserLoginDTO dto) {
        String token = userAuthService.login(dto.email(), dto.password());
        User user = userAuthService.validateToken(token);
        return toResponseDTO(token, user);
    }

    @GET
    @Path("/verify")
    public UserResponseDTO verify(@HeaderParam("Authorization") String authHeader) {
        User user = userAuthService.requireUser(authHeader);
        return toResponseDTO(null, user);
    }

    @PUT
    @Path("/profile")
    public UserResponseDTO updateProfile(@HeaderParam("Authorization") String authHeader, @Valid UserUpdateDTO dto) {
        User user = userAuthService.requireUser(authHeader);
        user = userAuthService.updateProfile(user, dto.firstName(), dto.lastName(), dto.phone(),
                dto.street(), dto.houseNumber(), dto.postalCode(), dto.city());
        return toResponseDTO(null, user);
    }

    @POST
    @Path("/forgot-password")
    public Map<String, String> forgotPassword(Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return Map.of("message", "Als dit e-mailadres bij ons bekend is, ontvang je een e-mail met instructies.");
        }
        User user = User.findByEmail(email.toLowerCase().trim());
        if (user != null) {
            String token = userAuthService.generateResetToken(user);
            String resetUrl = baseUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.email, user.getFullName(), resetUrl);
        }
        return Map.of("message", "Als dit e-mailadres bij ons bekend is, ontvang je een e-mail met instructies.");
    }

    @POST
    @Path("/reset-password")
    public Map<String, String> resetPassword(Map<String, String> body) {
        String token = body.get("token");
        String password = body.get("password");
        userAuthService.resetPassword(token, password);
        return Map.of("message", "Wachtwoord is succesvol gewijzigd. Je kunt nu inloggen.");
    }

    private UserResponseDTO toResponseDTO(String token, User user) {
        return new UserResponseDTO(token, user.email, user.firstName, user.lastName, user.phone,
                user.street, user.houseNumber, user.postalCode, user.city);
    }
}
