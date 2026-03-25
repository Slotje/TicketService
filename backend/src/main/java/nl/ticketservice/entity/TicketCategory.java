package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ticket_categories")
public class TicketCategory extends PanacheEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @NotBlank(message = "Naam is verplicht")
    @Size(max = 200)
    @Column(nullable = false)
    public String name;

    @Size(max = 500)
    public String description;

    @NotNull(message = "Prijs is verplicht")
    @DecimalMin(value = "0.00")
    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal price;

    @DecimalMin(value = "0.00")
    @Column(precision = 10, scale = 2)
    public BigDecimal serviceFee;

    @Min(0)
    @Column(nullable = false)
    public Integer maxTickets = 0; // 0 = uses event capacity

    @Column(nullable = false)
    public Integer ticketsSold = 0;

    @Column(nullable = false)
    public Integer ticketsReserved = 0;

    /** For multi-day events: first date this ticket is valid for. Null = valid for all days. */
    public LocalDate validDate;

    /** Last date this ticket is valid for. Null = same as validDate (single day). */
    public LocalDate validEndDate;

    /** Start time (e.g. doors open on first day). */
    public LocalDateTime startTime;

    /** End time (e.g. event ends on last day). */
    public LocalDateTime endTime;

    @Column(nullable = false)
    public Integer sortOrder = 0;

    @Column(nullable = false)
    public boolean active = true;

    @Size(max = 500)
    public String imageUrl;

    @Min(0)
    @Column(nullable = false)
    public Integer physicalTickets = 0;

    @Column(nullable = false)
    public Integer physicalTicketsSold = 0;

    @Column(nullable = false)
    public boolean physicalTicketsGenerated = false;

    @Column(nullable = false)
    public boolean showAvailability = true;

    public int getAvailableTickets() {
        if (maxTickets <= 0) return Integer.MAX_VALUE; // unlimited within event capacity
        return maxTickets - ticketsSold - ticketsReserved;
    }

    public static List<TicketCategory> findByEvent(Long eventId) {
        return list("event.id = ?1 ORDER BY sortOrder, id", eventId);
    }

    public static List<TicketCategory> findActiveByEvent(Long eventId) {
        return list("event.id = ?1 AND active = true ORDER BY sortOrder, id", eventId);
    }
}
