package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ticket_orders")
public class TicketOrder extends PanacheEntity {

    @Column(nullable = false, unique = true, updatable = false)
    public String orderNumber;

    @NotBlank(message = "Naam koper is verplicht")
    @Size(min = 2, max = 100)
    @Column(nullable = false)
    public String buyerName;

    @NotBlank(message = "E-mail koper is verplicht")
    @Email(message = "Ongeldig e-mailadres")
    @Column(nullable = false)
    public String buyerEmail;

    @Size(max = 20)
    public String buyerPhone;

    @NotNull
    @Min(value = 1, message = "Minimaal 1 ticket per bestelling")
    @Max(value = 10, message = "Maximaal 10 tickets per bestelling")
    @Column(nullable = false)
    public Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal serviceFeePerTicket = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal totalServiceFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public OrderStatus status = OrderStatus.RESERVED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    public Event event;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Ticket> tickets = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime confirmedAt;

    public LocalDateTime expiresAt;

    @Column(nullable = false)
    public boolean emailSent = false;

    @Column(nullable = false)
    public int emailRetryCount = 0;

    public LocalDateTime lastEmailAttempt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (orderNumber == null) {
            orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}
