package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer extends PanacheEntity {

    @NotBlank(message = "Bedrijfsnaam is verplicht")
    @Size(min = 2, max = 100, message = "Bedrijfsnaam moet tussen 2 en 100 karakters zijn")
    @Column(nullable = false)
    public String companyName;

    @NotBlank(message = "Contactpersoon is verplicht")
    @Size(min = 2, max = 100)
    @Column(nullable = false)
    public String contactPerson;

    @NotBlank(message = "E-mail is verplicht")
    @Email(message = "Ongeldig e-mailadres")
    @Column(nullable = false, unique = true)
    public String email;

    @Size(max = 20)
    public String phone;

    @Size(max = 500)
    public String logoUrl;

    @Size(max = 7, message = "Kleurcode moet een geldige hex waarde zijn (bijv. #FF5733)")
    public String primaryColor;

    @Size(max = 7)
    public String secondaryColor;

    @Size(max = 200)
    public String website;

    @Size(max = 100)
    @Column(unique = true)
    public String slug;

    public String passwordHash;

    public String inviteToken;

    public LocalDateTime inviteTokenExpiry;

    @Column(nullable = false)
    public boolean active = true;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    public LocalDateTime updatedAt;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Event> events = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Customer findByEmail(String email) {
        return find("email", email).firstResult();
    }

    public static Customer findBySlug(String slug) {
        return find("slug", slug).firstResult();
    }

    public static Customer findByInviteToken(String token) {
        return find("inviteToken", token).firstResult();
    }
}
