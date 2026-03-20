package nl.ticketservice.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.TicketOrder;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class EmailService {

    private static final Logger LOG = Logger.getLogger(EmailService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

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

    public boolean sendOrderConfirmation(TicketOrder order) {
        try {
            byte[] pdfBytes = pdfService.generateOrderPdf(order);

            String subject = "Je tickets voor " + order.event.name + " - " + order.orderNumber;
            String body = buildEmailBody(order);
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

    public boolean sendCustomerInvite(Customer customer, String inviteToken) {
        try {
            String activateUrl = baseUrl + "/klant/activeren/" + inviteToken;
            String subject = "Welkom bij TicketService - Activeer je account";
            String body = """
                    <html>
                    <body style="font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto;">
                        <div style="background-color: #2980b9; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                            <h1 style="color: white; margin: 0;">TicketService</h1>
                        </div>
                        <div style="padding: 30px; background: #f9f9f9; border-radius: 0 0 8px 8px;">
                            <h2 style="color: #2c3e50;">Welkom, %s!</h2>
                            <p>Er is een account aangemaakt voor <strong>%s</strong> bij TicketService.</p>
                            <p>Met dit account kun je:</p>
                            <ul>
                                <li>Je eigen evenementen aanmaken en beheren</li>
                                <li>Evenementen live zetten voor ticketverkoop</li>
                                <li>Je eigen gepersonaliseerde landingspagina gebruiken</li>
                            </ul>
                            <p>Klik op de onderstaande knop om je wachtwoord in te stellen en je account te activeren:</p>
                            <div style="text-align: center; margin: 30px 0;">
                                <a href="%s"
                                   style="background-color: #2980b9; color: white; padding: 14px 30px; text-decoration: none; border-radius: 6px; font-size: 16px; font-weight: bold;">
                                    Account activeren
                                </a>
                            </div>
                            <p style="font-size: 13px; color: #888;">
                                Deze link is 7 dagen geldig. Als de link is verlopen, neem dan contact op met de beheerder.
                            </p>
                            <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                            <p style="font-size: 12px; color: #999;">TicketService - Ticket Service Platform</p>
                        </div>
                    </body>
                    </html>
                    """.formatted(customer.contactPerson, customer.companyName, activateUrl);

            Mail mail = Mail.withHtml(customer.email, subject, body);
            mailer.send(mail);
            LOG.infof("Uitnodigingsmail verstuurd naar %s (%s)", customer.email, customer.companyName);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van uitnodigingsmail naar %s", customer.email);
            return false;
        }
    }

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

    private String buildEmailBody(TicketOrder order) {
        String eventName = order.event.name;
        String eventDate = order.event.eventDate.format(DATE_FMT);
        String location = order.event.location;
        String companyName = order.event.customer.companyName;

        return """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <h2>Bedankt voor je bestelling, %s!</h2>
                    <p>Je bestelling <strong>%s</strong> is bevestigd.</p>
                    <table style="border-collapse: collapse; margin: 16px 0;">
                        <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Evenement:</td><td>%s</td></tr>
                        <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Datum:</td><td>%s</td></tr>
                        <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Locatie:</td><td>%s</td></tr>
                        <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Aantal tickets:</td><td>%d</td></tr>
                        <tr><td style="padding: 4px 12px 4px 0; font-weight: bold;">Totaalprijs:</td><td>EUR %s</td></tr>
                    </table>
                    <p>Je tickets zijn als PDF bijgevoegd bij deze e-mail.</p>
                    <p>Veel plezier bij het evenement!</p>
                    <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
                    <p style="font-size: 12px; color: #999;">%s - Ticket Service</p>
                </body>
                </html>
                """.formatted(
                order.buyerName,
                order.orderNumber,
                eventName,
                eventDate,
                location,
                order.quantity,
                order.totalPrice.toPlainString(),
                companyName
        );
    }
}
