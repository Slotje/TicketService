package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket extends PanacheEntity {

    @Column(nullable = false, unique = true, updatable = false)
    public String ticketCode;

    @Column(nullable = false, unique = true, updatable = false)
    public String qrCodeData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TicketType ticketType = TicketType.ONLINE;

    @Column(nullable = false)
    public boolean scanned = false;

    public LocalDateTime scannedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    public TicketOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    public Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_category_id")
    public TicketCategory ticketCategory;

    /** For day tickets: the first date this ticket is valid for. Null = all days. */
    public LocalDate validDate;

    /** Last date this ticket is valid for. Null = same as validDate. */
    public LocalDate validEndDate;

    public String categoryName;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (ticketCode == null) {
            ticketCode = "TKT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }
        if (qrCodeData == null) {
            qrCodeData = UUID.randomUUID().toString();
        }
    }
}
