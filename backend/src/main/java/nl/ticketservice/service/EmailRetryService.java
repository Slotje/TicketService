package nl.ticketservice.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.OrderStatus;
import nl.ticketservice.entity.TicketOrder;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailRetryService {

    private static final Logger LOG = Logger.getLogger(EmailRetryService.class);
    private static final int MAX_RETRIES = 5;

    @Inject
    EmailService emailService;

    @Scheduled(every = "120s")
    @Transactional
    public void retryFailedEmails() {
        List<TicketOrder> pendingEmails = TicketOrder.list(
                "status = ?1 and emailSent = false and emailRetryCount < ?2",
                OrderStatus.CONFIRMED, MAX_RETRIES);

        if (pendingEmails.isEmpty()) {
            return;
        }

        LOG.infof("Email retry: %d bestellingen gevonden om opnieuw te mailen", pendingEmails.size());

        for (TicketOrder order : pendingEmails) {
            order.emailRetryCount++;
            order.lastEmailAttempt = LocalDateTime.now();

            boolean success = emailService.sendOrderConfirmation(order);
            if (success) {
                order.emailSent = true;
                LOG.infof("Email retry geslaagd voor bestelling %s (poging %d)",
                        order.orderNumber, order.emailRetryCount);
            } else {
                LOG.warnf("Email retry mislukt voor bestelling %s (poging %d/%d)",
                        order.orderNumber, order.emailRetryCount, MAX_RETRIES);
            }
        }
    }
}
