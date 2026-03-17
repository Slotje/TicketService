package nl.ticketservice.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.OrderStatus;
import nl.ticketservice.entity.TicketOrder;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ReservationCleanupService {

    private static final Logger LOG = Logger.getLogger(ReservationCleanupService.class);

    @Scheduled(every = "60s")
    @Transactional
    public void cleanupExpiredReservations() {
        List<TicketOrder> expiredOrders = TicketOrder.list(
                "status = ?1 and expiresAt < ?2",
                OrderStatus.RESERVED,
                LocalDateTime.now()
        );

        for (TicketOrder order : expiredOrders) {
            LOG.infof("Reservering %s is verlopen, tickets worden vrijgegeven", order.orderNumber);
            order.event.ticketsReserved -= order.quantity;
            order.status = OrderStatus.EXPIRED;
        }

        if (!expiredOrders.isEmpty()) {
            LOG.infof("%d verlopen reserveringen opgeschoond", expiredOrders.size());
        }
    }
}
