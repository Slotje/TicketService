package nl.ticketservice.service;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.ticketservice.entity.TicketOrder;
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

    public boolean sendOrderConfirmation(TicketOrder order) {
        try {
            byte[] pdfBytes = pdfService.generateOrderPdf(order);

            String subject = "Je tickets voor " + order.event.name + " - " + order.orderNumber;
            String body = buildEmailBody(order);

            Mail mail = Mail.withHtml(order.buyerEmail, subject, body)
                    .addAttachment(
                            "tickets-" + order.orderNumber + ".pdf",
                            pdfBytes,
                            "application/pdf"
                    );

            mailer.send(mail);
            LOG.infof("Bevestigingsmail verstuurd naar %s voor bestelling %s", order.buyerEmail, order.orderNumber);
            return true;
        } catch (Exception e) {
            LOG.errorf(e, "Fout bij het versturen van bevestigingsmail naar %s voor bestelling %s",
                    order.buyerEmail, order.orderNumber);
            return false;
        }
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
