package nl.ticketservice.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stored_images")
public class StoredImage extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String filename;

    @Column(nullable = false)
    public String contentType;

    @Lob
    @Column(nullable = false)
    public byte[] data;

    @Column(nullable = false)
    public long fileSize;

    @Column(nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static StoredImage findByFilename(String filename) {
        return find("filename", filename).firstResult();
    }
}
