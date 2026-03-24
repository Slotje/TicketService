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

    /** For multi-day events: date this ticket is valid for. Null = valid for all days. */
    public LocalDate validDate;

    /** Start time for this category/day (e.g. doors open). */
    public LocalDateTime startTime;

    /** End time for this category/day (can be next day for night events). */
    public LocalDateTime endTime;

    @Column(nullable = false)
    public Integer sortOrder = 0;

    @Column(nullable = false)
    public boolean active = true;

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
