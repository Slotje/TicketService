package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.AdminUser;
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
public class AdminAuthService {

    private static final String HMAC_ALGO = "HmacSHA256";

    @ConfigProperty(name = "ticket.auth.secret", defaultValue = "default-auth-secret-change-in-production")
    String authSecret;

    @ConfigProperty(name = "ticket.auth.token-expiry-hours", defaultValue = "24")
    int tokenExpiryHours;

    public String login(String username, String password) {
        AdminUser user = AdminUser.findByUsername(username);
        if (user == null || !user.active) {
            throw new TicketServiceException("Ongeldige gebruikersnaam of wachtwoord", 401);
        }

        if (!verifyPassword(password, user.passwordHash)) {
            throw new TicketServiceException("Ongeldige gebruikersnaam of wachtwoord", 401);
        }

        return generateToken(user);
    }

    public AdminUser validateToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|");
            if (parts.length != 3) return null;

            String userId = parts[0];
            long expiry = Long.parseLong(parts[1]);
            String signature = parts[2];

            if (Instant.now().getEpochSecond() > expiry) return null;

            String expectedSig = hmac("admin|" + userId + "|" + expiry);
            if (!expectedSig.equals(signature)) return null;

            AdminUser user = AdminUser.findById(Long.parseLong(userId));
            if (user == null || !user.active) return null;

            return user;
        } catch (Exception e) {
            return null;
        }
    }

    public void requireAdmin(String authHeader) {
        String token = extractToken(authHeader);
        AdminUser user = validateToken(token);
        if (user == null) {
            throw new TicketServiceException("Niet geautoriseerd. Admin login vereist.", 401);
        }
    }

    @Transactional
    public AdminUser createUser(String username, String password, String displayName) {
        if (AdminUser.findByUsername(username) != null) {
            throw new TicketServiceException("Gebruikersnaam is al in gebruik", 400);
        }

        AdminUser user = new AdminUser();
        user.username = username;
        user.passwordHash = hashPassword(password);
        user.displayName = displayName;
        user.persist();
        return user;
    }

    private String generateToken(AdminUser user) {
        long expiry = Instant.now().getEpochSecond() + (tokenExpiryHours * 3600L);
        String payload = user.id + "|" + expiry;
        String signature = hmac("admin|" + payload);
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
