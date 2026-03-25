package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import nl.ticketservice.entity.StoredImage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Loads images from database or external URLs for embedding in PDFs.
 */
@ApplicationScoped
public class ImageLoaderService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Load image bytes from a URL string. Supports:
     * - Local paths like /api/images/filename.png (reads from database)
     * - External http/https URLs
     * Returns null if loading fails (caller should handle gracefully).
     */
    public byte[] loadImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        try {
            // Local image reference — load from database
            if (imageUrl.startsWith("/api/images/")) {
                String filename = imageUrl.substring("/api/images/".length());
                if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                    return null;
                }
                StoredImage image = StoredImage.findByFilename(filename);
                return image != null ? image.data : null;
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
