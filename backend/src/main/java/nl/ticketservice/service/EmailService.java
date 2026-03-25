package nl.ticketservice.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.TicketOrder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", new Locale("nl"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    Mailer mailer;

    @Inject
    PdfService pdfService;

    @ConfigProperty(name = "ticket.app.base-url", defaultValue = "http://localhost:80")
    String baseUrl;

    @ConfigProperty(name = "ticket.mail.domain", defaultValue = "ticketing.lockitree.com")
    String mailDomain;

    @ConfigProperty(name = "ticket.mail.fallback-from", defaultValue = "noreply@ticketing.lockitree.com")
    String fallbackFrom;

    // =========================================================================
    // Shared template helpers (use .replace() to avoid %-escaping issues)
    // =========================================================================

    private static String wrapEmail(String headerIcon, String headerTitle, String headerSubtitle, String content) {
        return """
                <!DOCTYPE html>
                <html lang="nl">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background-color:#f0f2f5;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;-webkit-font-smoothing:antialiased;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:#f0f2f5;padding:40px 20px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;">
                        <!-- Header -->
                        <tr><td style="background:linear-gradient(135deg,#1a1a2e 0%,#16213e 50%,#0f3460 100%);padding:40px 40px 35px;border-radius:16px 16px 0 0;text-align:center;">
                          <div style="font-size:36px;line-height:1;margin-bottom:12px;">{{ICON}}</div>
                          <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:-0.5px;">{{TITLE}}</h1>
                          {{SUBTITLE}}
                        </td></tr>
                        <!-- Body -->
                        <tr><td style="background-color:#ffffff;padding:40px;border-radius:0 0 16px 16px;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                          {{CONTENT}}
                          <!-- Footer -->
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="margin-top:32px;border-top:1px solid #e8ecf1;padding-top:24px;">
                            <tr><td style="text-align:center;">
                              <p style="margin:0;font-size:12px;color:#9ca3af;">Verzonden via TicketService</p>
                            </td></tr>
                          </table>
                        </td></tr>
                        <!-- Bottom spacer -->
                        <tr><td style="padding:20px;text-align:center;">
                          <p style="margin:0;font-size:11px;color:#9ca3af;">Dit is een automatisch gegenereerd bericht.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """
                .replace("{{ICON}}", headerIcon)
                .replace("{{TITLE}}", headerTitle)
                .replace("{{SUBTITLE}}", headerSubtitle)
                .replace("{{CONTENT}}", content);
    }

    private static String subtitle(String text) {
        return "<p style=\"margin:8px 0 0;color:rgba(255,255,255,0.7);font-size:14px;font-weight:400;\">" + text + "</p>";
    }

    private static String button(String url, String label, String color) {
        return """
                <div style="text-align:center;margin:32px 0;">
                  <a href="{{URL}}" style="display:inline-block;background:{{COLOR}};color:#ffffff;padding:16px 40px;text-decoration:none;border-radius:12px;font-size:16px;font-weight:600;letter-spacing:0.3px;box-shadow:0 4px 14px rgba(0,0,0,0.15);">
                    {{LABEL}}
                  </a>
                </div>
                """
                .replace("{{URL}}", url)
                .replace("{{LABEL}}", label)
                .replace("{{COLOR}}", color);
    }

    private static String infoRow(String icon, String label, String value) {
        return """
                <tr>
                  <td style="padding:12px 16px;border-bottom:1px solid #f3f4f6;width:40px;text-align:center;font-size:18px;">{{ICON}}</td>
                  <td style="padding:12px 16px;border-bottom:1px solid #f3f4f6;">
                    <span style="display:block;font-size:11px;text-transform:uppercase;letter-spacing:0.5px;color:#9ca3af;font-weight:600;">{{LABEL}}</span>
                    <span style="display:block;font-size:15px;color:#1f2937;font-weight:500;margin-top:2px;">{{VALUE}}</span>
                  </td>
                </tr>
                """
                .replace("{{ICON}}", icon)
                .replace("{{LABEL}}", label)
                .replace("{{VALUE}}", value);
    }

    private static String infoTable(String rows) {
        return """
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f8fafc;border-radius:12px;overflow:hidden;margin:24px 0;">
                  {{ROWS}}
                </table>
                """.replace("{{ROWS}}", rows);
    }

    // =========================================================================
    // 1. Order Confirmation
    // =========================================================================

    public boolean sendOrderConfirmation(TicketOrder order) {
        try {
            byte[] pdfBytes = pdfService.generateOrderPdf(order);

            String subject = "Je tickets voor " + order.event.name + " - " + order.orderNumber;
            String body = buildOrderConfirmationBody(order);
            String from = buildCustomerFrom(order.event.customer.companyName);

            Mail mail = Mail.withHtml(order.buyerEmail, subject, body)
                    .setFrom(from)
                    .addAttachment(
                            "tickets-" + order.orderNumber + ".pdf",
                            pdfBytes,
                            "application/pdf"
                    );

            mailer.send(mail);
            LOG.infof("Bevestigingsmail verstuurd vanaf %s naar %s voor bestelling %s",
                    from, order.buyerEmail, order.orderNumber);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van bevestigingsmail naar %s voor bestelling %s",
                    order.buyerEmail, order.orderNumber);
            return false;
        }
    }

    private String buildOrderConfirmationBody(TicketOrder order) {
        String eventName = order.event.name;
        String eventDateLong = order.event.eventDate.format(DATE_LONG);
        String eventTime = order.event.eventDate.format(TIME_FMT);
        String location = order.event.location;
        String address = order.event.address != null ? order.event.address : "";
        String companyName = order.event.customer.companyName;
        String locationDisplay = address.isEmpty() ? location : location + ", " + address;

        String rows = infoRow("&#127915;", "Evenement", eventName)
                + infoRow("&#128197;", "Datum", eventDateLong)
                + infoRow("&#128336;", "Aanvang", eventTime + " uur")
                + infoRow("&#128205;", "Locatie", locationDisplay)
                + infoRow("&#127903;", "Tickets", order.quantity + "x")
                + infoRow("&#128176;", "Totaal", "EUR " + order.totalPrice.toPlainString());

        String content = """
                <h2 style="margin:0 0 8px;color:#1f2937;font-size:22px;font-weight:700;">Hoi {{BUYER_NAME}}!</h2>
                <p style="margin:0 0 24px;color:#6b7280;font-size:15px;line-height:1.6;">
                  Geweldig, je bestelling is bevestigd. Je tickets zijn als PDF bijgevoegd bij deze e-mail.
                </p>

                <!-- Order number badge -->
                <div style="text-align:center;margin-bottom:24px;">
                  <span style="display:inline-block;background:#f0fdf4;color:#166534;padding:8px 20px;border-radius:20px;font-size:13px;font-weight:600;border:1px solid #bbf7d0;">
                    &#10004; Bestelling {{ORDER_NUMBER}}
                  </span>
                </div>

                {{INFO_TABLE}}

                <div style="background:#eff6ff;border-radius:12px;padding:20px;margin:24px 0;border-left:4px solid #3b82f6;">
                  <p style="margin:0;font-size:14px;color:#1e40af;font-weight:600;">&#128206; PDF bijgevoegd</p>
                  <p style="margin:4px 0 0;font-size:13px;color:#3b82f6;">
                    Open de bijlage om je tickets te bekijken. Toon de QR-code bij de ingang.
                  </p>
                </div>

                <p style="margin:24px 0 0;font-size:14px;color:#6b7280;text-align:center;">
                  Veel plezier bij het evenement!
                </p>
                """
                .replace("{{BUYER_NAME}}", order.buyerFirstName)
                .replace("{{ORDER_NUMBER}}", order.orderNumber)
                .replace("{{INFO_TABLE}}", infoTable(rows));

        return wrapEmail(
                "&#127903;",
                "Bestelling bevestigd",
                subtitle(companyName),
                content
        );
    }

    // =========================================================================
    // 2. Customer Invitation
    // =========================================================================

    public boolean sendCustomerInvite(Customer customer, String inviteToken) {
        try {
            String activateUrl = baseUrl + "/klant/activeren/" + inviteToken;
            String subject = "Welkom bij TicketService - Activeer je account";

            String content = """
                    <h2 style="margin:0 0 8px;color:#1f2937;font-size:22px;font-weight:700;">Welkom, {{NAME}}!</h2>
                    <p style="margin:0 0 24px;color:#6b7280;font-size:15px;line-height:1.6;">
                      Er is een account aangemaakt voor <strong style="color:#1f2937;">{{COMPANY}}</strong> bij TicketService.
                    </p>

                    <div style="background:#f8fafc;border-radius:12px;padding:24px;margin:24px 0;">
                      <p style="margin:0 0 12px;font-size:14px;font-weight:600;color:#1f2937;">Met je account kun je:</p>
                      <table role="presentation" cellpadding="0" cellspacing="0">
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#9989;&nbsp;&nbsp;Je eigen evenementen aanmaken en beheren</td></tr>
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#127918;&nbsp;&nbsp;Evenementen live zetten voor ticketverkoop</td></tr>
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#127912;&nbsp;&nbsp;Je eigen gepersonaliseerde landingspagina gebruiken</td></tr>
                      </table>
                    </div>

                    <p style="margin:0 0 8px;color:#6b7280;font-size:14px;text-align:center;">
                      Klik op de knop om je wachtwoord in te stellen en je account te activeren:
                    </p>

                    {{BUTTON}}

                    <div style="text-align:center;margin-top:8px;">
                      <p style="margin:0;font-size:12px;color:#9ca3af;">
                        &#128274; Deze link is 7 dagen geldig
                      </p>
                    </div>
                    """
                    .replace("{{NAME}}", customer.contactPerson)
                    .replace("{{COMPANY}}", customer.companyName)
                    .replace("{{BUTTON}}", button(activateUrl, "Account activeren &#8594;", "linear-gradient(135deg,#10b981,#059669)"));

            String body = wrapEmail(
                    "&#128075;",
                    "Welkom bij TicketService",
                    subtitle("Account activeren"),
                    content
            );

            Mail mail = Mail.withHtml(customer.email, subject, body);
            mailer.send(mail);
            LOG.infof("Uitnodigingsmail verstuurd naar %s (%s)", customer.email, customer.companyName);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van uitnodigingsmail naar %s", customer.email);
            return false;
        }
    }

    // =========================================================================
    // 3. Password Reset
    // =========================================================================

    public boolean sendPasswordResetEmail(String toEmail, String name, String resetUrl) {
        try {
            String subject = "Wachtwoord herstellen - TicketService";

            String content = """
                    <h2 style="margin:0 0 8px;color:#1f2937;font-size:22px;font-weight:700;">Wachtwoord herstellen</h2>
                    <p style="margin:0 0 4px;color:#6b7280;font-size:15px;line-height:1.6;">
                      Hallo <strong style="color:#1f2937;">{{NAME}}</strong>,
                    </p>
                    <p style="margin:0 0 24px;color:#6b7280;font-size:15px;line-height:1.6;">
                      We hebben een verzoek ontvangen om je wachtwoord te herstellen. Klik op de onderstaande knop om een nieuw wachtwoord in te stellen.
                    </p>

                    {{BUTTON}}

                    <div style="background:#fefce8;border-radius:12px;padding:16px 20px;margin:24px 0;border-left:4px solid #eab308;">
                      <p style="margin:0;font-size:13px;color:#854d0e;">
                        &#9888;&#65039; Deze link is <strong>1 uur</strong> geldig. Heb je geen wachtwoord reset aangevraagd? Dan kun je deze e-mail veilig negeren.
                      </p>
                    </div>
                    """
                    .replace("{{NAME}}", name)
                    .replace("{{BUTTON}}", button(resetUrl, "Nieuw wachtwoord instellen &#8594;", "linear-gradient(135deg,#6366f1,#4f46e5)"));

            String body = wrapEmail(
                    "&#128274;",
                    "Wachtwoord herstellen",
                    subtitle("Beveiligingsverzoek"),
                    content
            );

            Mail mail = Mail.withHtml(toEmail, subject, body);
            mailer.send(mail);
            LOG.infof("Wachtwoord reset mail verstuurd naar %s", toEmail);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van wachtwoord reset mail naar %s", toEmail);
            return false;
        }
    }

    // =========================================================================
    // 4. Physical Tickets PDF
    // =========================================================================

    public boolean sendPhysicalTicketsPdf(Event event, byte[] pdfBytes) {
        try {
            Customer customer = event.customer;
            String subject = "Fysieke tickets voor " + event.name + " - Klaar om te printen";

            String rows = infoRow("&#127915;", "Evenement", event.name)
                    + infoRow("&#127903;", "Fysieke tickets", String.valueOf(event.physicalTickets))
                    + infoRow("&#128197;", "Datum", event.eventDate.format(DATE_LONG))
                    + infoRow("&#128336;", "Aanvang", event.eventDate.format(TIME_FMT) + " uur");

            String content = """
                    <h2 style="margin:0 0 8px;color:#1f2937;font-size:22px;font-weight:700;">Fysieke tickets klaar!</h2>
                    <p style="margin:0 0 24px;color:#6b7280;font-size:15px;line-height:1.6;">
                      Beste <strong style="color:#1f2937;">{{CONTACT}}</strong>, de fysieke tickets voor
                      <strong style="color:#1f2937;">{{EVENT}}</strong> zijn gegenereerd en bijgevoegd als PDF.
                    </p>

                    {{INFO_TABLE}}

                    <div style="background:#f8fafc;border-radius:12px;padding:24px;margin:24px 0;">
                      <p style="margin:0 0 16px;font-size:15px;font-weight:700;color:#1f2937;">&#128424; Printinstructies</p>
                      <table role="presentation" cellpadding="0" cellspacing="0">
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#128196;&nbsp;&nbsp;Print op <strong>dik papier</strong> (minimaal 250 g/m&sup2;)</td></tr>
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#128260;&nbsp;&nbsp;Gebruik <strong>dubbelzijdig printen</strong> (korte zijde spiegelen)</td></tr>
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#9986;&nbsp;&nbsp;Snij de tickets langs de stippellijnen (4 per pagina)</td></tr>
                        <tr><td style="padding:6px 0;font-size:14px;color:#4b5563;">&#128274;&nbsp;&nbsp;Elke ticket heeft een <strong>unieke QR-code</strong></td></tr>
                      </table>
                    </div>

                    <div style="background:#eff6ff;border-radius:12px;padding:20px;margin:24px 0;border-left:4px solid #3b82f6;">
                      <p style="margin:0;font-size:14px;color:#1e40af;font-weight:600;">&#128206; PDF bijgevoegd</p>
                      <p style="margin:4px 0 0;font-size:13px;color:#3b82f6;">
                        Open de bijlage en print de tickets. De QR-codes kunnen bij de ingang gescand worden.
                      </p>
                    </div>
                    """
                    .replace("{{CONTACT}}", customer.contactPerson)
                    .replace("{{EVENT}}", event.name)
                    .replace("{{INFO_TABLE}}", infoTable(rows));

            String body = wrapEmail(
                    "&#127903;",
                    "Fysieke tickets gegenereerd",
                    subtitle(customer.companyName),
                    content
            );

            String from = buildCustomerFrom(customer.companyName);

            Mail mail = Mail.withHtml(customer.email, subject, body)
                    .setFrom(from)
                    .addAttachment(
                            "fysieke-tickets-" + event.name.replaceAll("[^a-zA-Z0-9]", "-") + ".pdf",
                            pdfBytes,
                            "application/pdf"
                    );

            mailer.send(mail);
            LOG.infof("Fysieke tickets PDF verstuurd naar %s voor evenement '%s'",
                    customer.email, event.name);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van fysieke tickets PDF naar %s voor evenement '%s'",
                    event.customer.email, event.name);
            return false;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Generates a from address like "Test Bedrijf <testbedrijf@ticketing.lockitree.com>"
     */
    private String buildCustomerFrom(String companyName) {
        String localPart = companyName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "")
                .replaceAll("^-|-$", "");
        if (localPart.isEmpty()) {
            return fallbackFrom;
        }
        return companyName + " <" + localPart + "@" + mailDomain + ">";
    }
}
