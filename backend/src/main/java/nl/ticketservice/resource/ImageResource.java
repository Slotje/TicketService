package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.entity.StoredImage;
import nl.ticketservice.service.AdminAuthService;
import nl.ticketservice.service.CustomerAuthService;
import nl.ticketservice.exception.TicketServiceException;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Path("/api/images")
public class ImageResource {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Map<String, String> TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp"
    );

    @Inject
    AdminAuthService adminAuthService;

    @Inject
    CustomerAuthService customerAuthService;

    @POST
    @Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response upload(@RestForm("file") FileUpload file,
                           @HeaderParam("Authorization") String authHeader) {
        // Require either admin or customer auth
        boolean authorized = false;
        try {
            adminAuthService.requireAdmin(authHeader);
            authorized = true;
        } catch (Exception e) {
            // not admin, try customer
        }
        if (!authorized) {
            try {
                customerAuthService.requireCustomer(authHeader);
                authorized = true;
            } catch (Exception e) {
                // not customer either
            }
        }
        if (!authorized) {
            throw new TicketServiceException("Authenticatie vereist", 401);
        }

        if (file == null || file.filePath() == null) {
            throw new TicketServiceException("Geen bestand geüpload", 400);
        }

        byte[] data;
        try {
            data = Files.readAllBytes(file.filePath());
        } catch (IOException e) {
            throw new TicketServiceException("Fout bij lezen bestand", 500);
        }

        if (data.length > MAX_FILE_SIZE) {
            throw new TicketServiceException("Bestand is te groot (max 5MB)", 400);
        }

        String contentType = file.contentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new TicketServiceException("Ongeldig bestandstype. Toegestaan: JPEG, PNG, GIF, WebP", 400);
        }

        String extension = TYPE_EXTENSIONS.getOrDefault(contentType, ".jpg");
        String filename = UUID.randomUUID() + extension;

        StoredImage image = new StoredImage();
        image.filename = filename;
        image.contentType = contentType;
        image.data = data;
        image.fileSize = data.length;
        image.persist();

        String url = "/api/images/" + filename;
        return Response.ok(Map.of("url", url)).build();
    }

    @GET
    @Path("/{filename}")
    @Transactional
    public Response getImage(@PathParam("filename") String filename) {
        // Prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new TicketServiceException("Ongeldig bestandsnaam", 400);
        }

        StoredImage image = StoredImage.findByFilename(filename);
        if (image == null) {
            throw new TicketServiceException("Bestand niet gevonden", 404);
        }

        return Response.ok(image.data)
                .type(image.contentType)
                .header("Cache-Control", "public, max-age=86400")
                .build();
    }
}
