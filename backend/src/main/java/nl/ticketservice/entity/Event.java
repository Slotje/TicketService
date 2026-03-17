package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event extends PanacheEntity {

    @NotBlank(message = "Evenementnaam is verplicht")
    @Size(min = 2, max = 200)
    @Column(nullable = false)
    public String name;

    @Size(max = 2000)
    public String description;

    @NotNull(message = "Evenementdatum is verplicht")
    @Future(message = "Evenementdatum moet in de toekomst liggen")
    @Column(nullable = false)
    public LocalDateTime eventDate;

    public LocalDateTime endDate;

    @NotBlank(message = "Locatie is verplicht")
    @Size(max = 300)
    @Column(nullable = false)
    public String location;

    @Size(max = 500)
    public String address;

    @NotNull(message = "Maximaal aantal tickets is verplicht")
    @Min(value = 1, message = "Minimaal 1 ticket")
    @Max(value = 100000, message = "Maximaal 100.000 tickets")
    @Column(nullable = false)
    public Integer maxTickets;

    @NotNull(message = "Prijs is verplicht")
    @DecimalMin(value = "0.00", message = "Prijs mag niet negatief zijn")
    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal ticketPrice;

    @NotNull(message = "Servicekosten zijn verplicht")
    @DecimalMin(value = "0.00", message = "Servicekosten mogen niet negatief zijn")
    @Column(nullable = false, precision = 10, scale = 2)
    public BigDecimal serviceFee = BigDecimal.ZERO;

    @Min(value = 1)
    @Max(value = 10)
    @Column(nullable = false)
    public Integer maxTicketsPerOrder = 10;

    @Column(nullable = false)
    public Integer ticketsSold = 0;

    @Column(nullable = false)
    public Integer ticketsReserved = 0;

    @Size(max = 500)
    public String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public EventStatus status = EventStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL)
    public List<TicketOrder> orders = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getAvailableTickets() {
        return maxTickets - ticketsSold - ticketsReserved;
    }

    public boolean hasAvailableTickets(int quantity) {
        return getAvailableTickets() >= quantity;
    }
}
