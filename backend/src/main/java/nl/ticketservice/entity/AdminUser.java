package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_users")
public class AdminUser extends PanacheEntity {

    @NotBlank
    @Size(min = 3, max = 100)
    @Column(nullable = false, unique = true)
    public String username;

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

    public static AdminUser findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
