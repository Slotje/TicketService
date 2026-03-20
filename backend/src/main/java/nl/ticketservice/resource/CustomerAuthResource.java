package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.service.CustomerAuthService;

import java.util.Map;

@Path("/api/customer/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerAuthResource {

    @Inject
    CustomerAuthService customerAuthService;

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
}
