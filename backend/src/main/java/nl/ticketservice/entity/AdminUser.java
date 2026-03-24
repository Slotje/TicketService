package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
public class AdminUser extends PanacheEntity {

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    public String email;

    @NotBlank
    @Column(nullable = false)
    public String passwordHash;

    @Size(max = 200)
    public String displayName;

    @Column(nullable = false)
    public boolean active = true;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static AdminUser findByEmail(String email) {
        return find("LOWER(email) = ?1", email.toLowerCase().trim()).firstResult();
    }
}
