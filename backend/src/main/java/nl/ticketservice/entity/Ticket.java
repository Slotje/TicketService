package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket extends PanacheEntity {

    @Column(nullable = false, unique = true, updatable = false)
    public String ticketCode;

    @Column(nullable = false, unique = true, updatable = false)
    public String qrCodeData;

    @Column(nullable = false)
    public boolean scanned = false;

    public LocalDateTime scannedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    public TicketOrder order;

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
