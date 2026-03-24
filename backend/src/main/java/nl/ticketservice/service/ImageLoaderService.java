package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Loads images from local upload directory or external URLs for embedding in PDFs.
 */
@ApplicationScoped
public class ImageLoaderService {

    private final Path uploadDir = Paths.get(System.getProperty("ticket.upload.dir", "/tmp/ticketservice-images"));
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Load image bytes from a URL string. Supports:
     * - Local paths like /api/images/filename.png (reads from upload dir)
     * - External http/https URLs
     * Returns null if loading fails (caller should handle gracefully).
     */
    public byte[] loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            // Local image reference
            if (imageUrl.startsWith("/api/images/")) {
                String filename = imageUrl.substring("/api/images/".length());
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return null;
                }
                Path file = uploadDir.resolve(filename);
                if (Files.exists(file)) {
                    return Files.readAllBytes(file);
                }
                return null;
            }

            // External URL
            if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(imageUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                return null;
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
