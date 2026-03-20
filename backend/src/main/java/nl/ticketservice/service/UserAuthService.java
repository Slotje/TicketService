package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.User;
import nl.ticketservice.exception.TicketServiceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@ApplicationScoped
public class UserAuthService {

    private static final String HMAC_ALGO = "HmacSHA256";

    @ConfigProperty(name = "ticket.auth.secret", defaultValue = "default-auth-secret-change-in-production")
    String authSecret;

    @ConfigProperty(name = "ticket.auth.token-expiry-hours", defaultValue = "24")
    int tokenExpiryHours;

    @Transactional
    public User register(String email, String password, String firstName, String lastName, String phone) {
        if (User.findByEmail(email) != null) {
            throw new TicketServiceException("Dit e-mailadres is al geregistreerd", 400);
        }

        User user = new User();
        user.email = email.toLowerCase().trim();
        user.passwordHash = hashPassword(password);
        user.firstName = firstName;
        user.lastName = lastName;
        user.phone = phone;
        user.persist();
        return user;
    }

    public String login(String email, String password) {
        User user = User.findByEmail(email.toLowerCase().trim());
        if (user == null) {
            throw new TicketServiceException("Ongeldig e-mailadres of wachtwoord", 401);
        }

        if (!verifyPassword(password, user.passwordHash)) {
            throw new TicketServiceException("Ongeldig e-mailadres of wachtwoord", 401);
        }

        return generateToken(user);
    }

    public User validateToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) return null;

            String userId = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String signature = parts[2];

            if (Instant.now().getEpochSecond() > expiry) return null;

            String expectedSig = hmac("user|" + userId + "|" + expiry);
            if (!expectedSig.equals(signature)) return null;

            User user = User.findById(Long.parseLong(userId));
            return user;
        } catch (Exception e) {
            return null;
        }
    }

    public String generateResetToken(User user) {
        long expiry = Instant.now().getEpochSecond() + 3600; // 1 hour
        String payload = user.id + "|" + expiry;
        String signature = hmac("reset|" + payload + "|" + user.passwordHash);
        String token = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) {
                throw new TicketServiceException("Ongeldige of verlopen resetlink", 400);
            }

            String userId = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String signature = parts[2];

            if (Instant.now().getEpochSecond() > expiry) {
                throw new TicketServiceException("Resetlink is verlopen", 400);
            }

            User user = User.findById(Long.parseLong(userId));
            if (user == null) {
                throw new TicketServiceException("Ongeldige resetlink", 400);
            }

            String expectedSig = hmac("reset|" + userId + "|" + expiry + "|" + user.passwordHash);
            if (!expectedSig.equals(signature)) {
                throw new TicketServiceException("Ongeldige of verlopen resetlink", 400);
            }

            user.passwordHash = hashPassword(newPassword);
        } catch (TicketServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new TicketServiceException("Ongeldige resetlink", 400);
        }
    }

    public User requireUser(String authHeader) {
        String token = extractToken(authHeader);
        User user = validateToken(token);
        if (user == null) {
            throw new TicketServiceException("Niet geautoriseerd. Log in.", 401);
        }
        return user;
    }

    private String generateToken(User user) {
        long expiry = Instant.now().getEpochSecond() + (tokenExpiryHours * 3600L);
        String payload = user.id + "|" + expiry;
        String signature = hmac("user|" + payload);
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
