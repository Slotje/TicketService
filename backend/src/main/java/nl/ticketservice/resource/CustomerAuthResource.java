package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.service.CustomerAuthService;
import nl.ticketservice.service.EmailService;
import nl.ticketservice.service.PdfService;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;

@Path("/api/customer/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerAuthResource {

    @Inject
    CustomerAuthService customerAuthService;

    @Inject
    EmailService emailService;

    @Inject
    PdfService pdfService;

    @ConfigProperty(name = "ticket.app.base-url", defaultValue = "http://localhost:80")
    String baseUrl;

    @POST
    @Path("/login")
    public Map<String, Object> login(Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");
        String token = customerAuthService.login(email, password);

        Customer customer = customerAuthService.validateToken(token);
        return Map.of(
                "token", token,
                "customerId", customer.id,
                "companyName", customer.companyName,
                "contactPerson", customer.contactPerson,
                "email", customer.email
        );
    }

    @POST
    @Path("/set-password")
    public Map<String, Object> setPassword(Map<String, String> body) {
        String inviteToken = body.get("token");
        String password = body.get("password");

        Customer customer = customerAuthService.setPassword(inviteToken, password);
        String authToken = customerAuthService.login(customer.email, password);

        return Map.of(
                "token", authToken,
                "customerId", customer.id,
                "companyName", customer.companyName,
                "contactPerson", customer.contactPerson,
                "email", customer.email
        );
    }

    @GET
    @Path("/verify")
    public Map<String, Object> verify(@HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        return Map.of(
                "customerId", customer.id,
                "companyName", customer.companyName,
                "contactPerson", customer.contactPerson,
                "email", customer.email
        );
    }

    @GET
    @Path("/invite/{token}")
    public Map<String, Object> verifyInvite(@PathParam("token") String token) {
        Customer customer = Customer.findByInviteToken(token);
        if (customer == null) {
            throw new jakarta.ws.rs.BadRequestException("Ongeldige uitnodiging");
        }
        if (customer.inviteTokenExpiry != null && customer.inviteTokenExpiry.isBefore(java.time.LocalDateTime.now())) {
            throw new jakarta.ws.rs.BadRequestException("Uitnodiging is verlopen");
        }
        return Map.of(
                "companyName", customer.companyName,
                "email", customer.email
        );
    }

    @POST
    @Path("/forgot-password")
    public Map<String, String> forgotPassword(Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return Map.of("message", "Als dit e-mailadres bij ons bekend is, ontvang je een e-mail met instructies.");
        }
        Customer customer = Customer.findByEmail(email.toLowerCase().trim());
        if (customer != null && customer.passwordHash != null) {
            String token = customerAuthService.generateResetToken(customer);
            String resetUrl = baseUrl + "/klant/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(customer.email, customer.contactPerson, resetUrl);
        }
        return Map.of("message", "Als dit e-mailadres bij ons bekend is, ontvang je een e-mail met instructies.");
    }

    @POST
    @Path("/reset-password")
    public Map<String, String> resetPassword(Map<String, String> body) {
        String token = body.get("token");
        String password = body.get("password");
        customerAuthService.resetPassword(token, password);
        return Map.of("message", "Wachtwoord is succesvol gewijzigd. Je kunt nu inloggen.");
    }

    @PUT
    @Path("/branding")
    @Transactional
    public Response updateBranding(Map<String, String> body, @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);

        if (body.containsKey("logoUrl")) {
            customer.logoUrl = body.get("logoUrl");
        }
        if (body.containsKey("primaryColor")) {
            String color = body.get("primaryColor");
            if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
                customer.primaryColor = color;
            }
        }
        if (body.containsKey("secondaryColor")) {
            String color = body.get("secondaryColor");
            if (color != null && color.matches("^#[0-9a-fA-F]{6}$")) {
                customer.secondaryColor = color;
            }
        }
        if (body.containsKey("website")) {
            customer.website = body.get("website");
        }

        return Response.ok(Map.of("success", true)).build();
    }

    @GET
    @Path("/branding/preview-ticket")
    @Produces("application/pdf")
    public Response previewTicketPdf(@HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"voorbeeld-ticket.pdf\"")
                .build();
    }
}
