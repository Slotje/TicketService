package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import nl.ticketservice.exception.TicketServiceException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Service for interacting with the Mollie Payments API v2.
 * Uses java.net.http.HttpClient — no external dependencies needed.
 */
@ApplicationScoped
public class MolliePaymentService {

    private static final Logger LOG = Logger.getLogger(MolliePaymentService.class);
    private static final String MOLLIE_API_BASE = "https://api.mollie.com/v2";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @ConfigProperty(name = "ticket.mollie.webhook-url", defaultValue = "http://localhost:8080/api/orders/webhooks/mollie")
    String webhookUrl;

    @ConfigProperty(name = "ticket.app.base-url", defaultValue = "http://localhost:80")
    String baseUrl;

    // =========================================================================
    // Create Payment
    // =========================================================================

    public MolliePayment createPayment(String apiKey, BigDecimal amount, String description, String orderNumber) {
        String redirectUrl = baseUrl + "/order/" + orderNumber;

        String body = """
                {
                  "amount": { "currency": "EUR", "value": "%s" },
                  "description": "%s",
                  "redirectUrl": "%s",
                  "webhookUrl": "%s",
                  "metadata": { "orderNumber": "%s" }
                }
                """.formatted(
                amount.setScale(2).toPlainString(),
                escapeJson(description),
                escapeJson(redirectUrl),
                escapeJson(webhookUrl),
                escapeJson(orderNumber)
        );

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOLLIE_API_BASE + "/payments"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.errorf("Mollie create payment failed: %d - %s", response.statusCode(), response.body());
                throw new TicketServiceException("Fout bij aanmaken betaling bij Mollie", 502);
            }

            String responseBody = response.body();
            String id = extractJsonString(responseBody, "id");
            String status = extractJsonString(responseBody, "status");
            String checkoutUrl = extractCheckoutUrl(responseBody);

            // Validate checkout URL is from Mollie
            if (checkoutUrl != null && !checkoutUrl.startsWith("https://")) {
                LOG.errorf("Suspicious checkout URL from Mollie: %s", checkoutUrl);
                throw new TicketServiceException("Ongeldige betaal-URL ontvangen", 502);
            }

            LOG.infof("Mollie payment created: %s (status: %s)", id, status);
            return new MolliePayment(id, status, checkoutUrl);

        } catch (TicketServiceException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating Mollie payment");
            throw new TicketServiceException("Kan geen verbinding maken met betaalprovider", 502);
        }
    }

    // =========================================================================
    // Get Payment Status
    // =========================================================================

    public MolliePayment getPayment(String apiKey, String paymentId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOLLIE_API_BASE + "/payments/" + paymentId))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.errorf("Mollie get payment failed: %d - %s", response.statusCode(), response.body());
                throw new TicketServiceException("Fout bij ophalen betaalstatus", 502);
            }

            String responseBody = response.body();
            String id = extractJsonString(responseBody, "id");
            String status = extractJsonString(responseBody, "status");

            return new MolliePayment(id, status, null);

        } catch (TicketServiceException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting Mollie payment %s", paymentId);
            throw new TicketServiceException("Kan betaalstatus niet ophalen", 502);
        }
    }

    // =========================================================================
    // Create Refund
    // =========================================================================

    public MollieRefund createRefund(String apiKey, String paymentId, BigDecimal amount) {
        String body = """
                {
                  "amount": { "currency": "EUR", "value": "%s" }
                }
                """.formatted(amount.setScale(2).toPlainString());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MOLLIE_API_BASE + "/payments/" + paymentId + "/refunds"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOG.errorf("Mollie create refund failed: %d - %s", response.statusCode(), response.body());
                throw new TicketServiceException("Fout bij aanmaken terugbetaling", 502);
            }

            String responseBody = response.body();
            String id = extractJsonString(responseBody, "id");
            String status = extractJsonString(responseBody, "status");

            LOG.infof("Mollie refund created: %s for payment %s (status: %s)", id, paymentId, status);
            return new MollieRefund(id, status);

        } catch (TicketServiceException e) {
            throw e;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating Mollie refund for payment %s", paymentId);
            throw new TicketServiceException("Kan terugbetaling niet aanmaken", 502);
        }
    }

    // =========================================================================
    // Simple JSON helpers (no external JSON library needed)
    // =========================================================================

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx + pattern.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private static String extractCheckoutUrl(String json) {
        // Look for _links.checkout.href
        int checkoutIdx = json.indexOf("\"checkout\"");
        if (checkoutIdx < 0) return null;
        int hrefIdx = json.indexOf("\"href\"", checkoutIdx);
        if (hrefIdx < 0) return null;
        int colonIdx = json.indexOf(":", hrefIdx + 6);
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf("\"", colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // =========================================================================
    // Records
    // =========================================================================

    public record MolliePayment(String id, String status, String checkoutUrl) {}
    public record MollieRefund(String id, String status) {}
}
