package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.exception.TicketServiceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class CustomerAuthService {

    private static final String HMAC_ALGO = "HmacSHA256";

    @ConfigProperty(name = "ticket.auth.secret", defaultValue = "default-auth-secret-change-in-production")
    String authSecret;

    @ConfigProperty(name = "ticket.auth.token-expiry-hours", defaultValue = "24")
    int tokenExpiryHours;

    public String login(String email, String password) {
        Customer customer = Customer.findByEmail(email);
        if (customer == null || !customer.active) {
            throw new TicketServiceException("Ongeldig e-mailadres of wachtwoord", 401);
        }
        if (customer.passwordHash == null) {
            throw new TicketServiceException("Account is nog niet geactiveerd. Controleer je e-mail voor de uitnodiging.", 401);
        }
        if (!verifyPassword(password, customer.passwordHash)) {
            throw new TicketServiceException("Ongeldig e-mailadres of wachtwoord", 401);
        }
        return generateToken(customer);
    }

    public Customer validateToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) return null;

            String customerId = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String signature = parts[2];

            if (Instant.now().getEpochSecond() > expiry) return null;

            String expectedSig = hmac("customer|" + customerId + "|" + expiry);
            if (!expectedSig.equals(signature)) return null;

            Customer customer = Customer.findById(Long.parseLong(customerId));
            if (customer == null || !customer.active) return null;

            return customer;
        } catch (Exception e) {
            return null;
        }
    }

    public Customer requireCustomer(String authHeader) {
        String token = extractToken(authHeader);
        Customer customer = validateToken(token);
        if (customer == null) {
            throw new TicketServiceException("Niet geautoriseerd. Klant login vereist.", 401);
        }
        return customer;
    }

    @Transactional
    public String generateInviteToken(Customer customer) {
        String token = UUID.randomUUID().toString();
        customer.inviteToken = token;
        customer.inviteTokenExpiry = LocalDateTime.now().plusDays(7);
        return token;
    }

    @Transactional
    public Customer setPassword(String inviteToken, String password) {
        Customer customer = Customer.findByInviteToken(inviteToken);
        if (customer == null) {
            throw new TicketServiceException("Ongeldige of verlopen uitnodiging", 400);
        }
        if (customer.inviteTokenExpiry != null && customer.inviteTokenExpiry.isBefore(LocalDateTime.now())) {
            throw new TicketServiceException("Uitnodiging is verlopen. Neem contact op met de beheerder.", 400);
        }

        customer.passwordHash = hashPassword(password);
        customer.inviteToken = null;
        customer.inviteTokenExpiry = null;
        return customer;
    }

    private String generateToken(Customer customer) {
        long expiry = Instant.now().getEpochSecond() + (tokenExpiryHours * 3600L);
        String payload = customer.id + "|" + expiry;
        String signature = hmac("customer|" + payload);
        String token = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(authSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new TicketServiceException("Token generatie fout", 500);
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((authSecret + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new TicketServiceException("Wachtwoord hashing fout", 500);
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        return hashPassword(password).equals(storedHash);
    }

    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new TicketServiceException("Geen autorisatie token meegegeven", 401);
    }
}
