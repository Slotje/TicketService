package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User extends PanacheEntity {

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    public String email;

    @NotBlank
    @Column(nullable = false)
    public String passwordHash;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    public String firstName;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false)
    public String lastName;

    @Size(max = 20)
    public String phone;

    @Size(max = 200)
    public String street;

    @Size(max = 10)
    public String houseNumber;

    @Size(max = 10)
    public String postalCode;

    @Size(max = 100)
    public String city;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public static User findByEmail(String email) {
        return find("email", email).firstResult();
    }
}
