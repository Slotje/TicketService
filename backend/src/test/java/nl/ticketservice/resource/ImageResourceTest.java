package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ImageResourceTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static String customerToken;
    private static String uploadedFilename;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String getAdminToken() {
        if (adminToken == null) {
            adminToken = given()
                    .contentType(ContentType.JSON)
                    .body("{\"email\":\"admin@ticketservice.nl\",\"password\":\"admin\"}")
                .when()
                    .post("/api/admin/auth/login")
                .then()
                    .statusCode(200)
                    .extract().path("token");
        }
        return adminToken;
    }

    @Transactional
    String getInviteTokenForEmail(String email) {
        Customer customer = em.createQuery(
                "SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                .setParameter("email", email)
                .getSingleResult();
        return customer.inviteToken;
    }

    private String getCustomerToken() {
        if (customerToken == null) {
            String admin = getAdminToken();

            // Create customer via admin API
            given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + admin)
                    .body("{\"companyName\":\"Image Test BV\","
                            + "\"contactPerson\":\"Image Tester\","
                            + "\"email\":\"imagetest@example.com\","
                            + "\"phone\":\"+31 6 99999999\","
                            + "\"active\":true}")
                .when()
                    .post("/api/customers")
                .then()
                    .statusCode(201);

            // Get invite token from DB
            String inviteToken = getInviteTokenForEmail("imagetest@example.com");
            assertNotNull(inviteToken);

            // Set password
            given()
                    .contentType(ContentType.JSON)
                    .body("{\"token\":\"" + inviteToken + "\",\"password\":\"test123\"}")
                .when()
                    .post("/api/customer/auth/set-password")
                .then()
                    .statusCode(200);

            // Login
            customerToken = given()
                    .contentType(ContentType.JSON)
                    .body("{\"email\":\"imagetest@example.com\",\"password\":\"test123\"}")
                .when()
                    .post("/api/customer/auth/login")
                .then()
                    .statusCode(200)
                    .extract().path("token");
        }
        return customerToken;
    }

    private File createTempImage(String suffix, byte[] content) throws IOException {
        File tempFile = File.createTempFile("test-image-", suffix);
        tempFile.deleteOnExit();
        Files.write(tempFile.toPath(), content);
        return tempFile;
    }

    private File createTempImageWithSize(String suffix, int sizeBytes) throws IOException {
        byte[] data = new byte[sizeBytes];
        // Write a minimal valid-looking header (not strictly needed but good practice)
        if (suffix.equals(".jpg") || suffix.equals(".jpeg")) {
            data[0] = (byte) 0xFF;
            data[1] = (byte) 0xD8;
        } else if (suffix.equals(".png")) {
            data[0] = (byte) 0x89;
            data[1] = (byte) 0x50;
        }
        return createTempImage(suffix, data);
    }

    // =========================================================================
    // Upload tests
    // =========================================================================

    @Test
    @Order(1)
    void testUploadWithoutAuth_returns401() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".jpg", 100);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .multiPart("file", tempFile, "image/jpeg")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(2)
    void testUploadWithInvalidToken_returns401() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".jpg", 100);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer invalid-token-here")
                .multiPart("file", tempFile, "image/jpeg")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    void testUploadWithAdminAuth_validJpeg_returns200() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".jpg", 1024);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        String url = given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/jpeg")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .body("url", startsWith("/api/images/"))
                .body("url", endsWith(".jpg"))
                .extract().path("url");

        // Store the filename for later retrieval tests
        uploadedFilename = url.replace("/api/images/", "");
        assertNotNull(uploadedFilename);
        assertFalse(uploadedFilename.isEmpty());
    }

    @Test
    @Order(4)
    void testUploadWithCustomerAuth_validPng_returns200() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".png", 512);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getCustomerToken())
                .multiPart("file", tempFile, "image/png")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .body("url", startsWith("/api/images/"))
                .body("url", endsWith(".png"));
    }

    @Test
    @Order(5)
    void testUploadWithAdminAuth_validGif_returns200() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".gif", 256);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/gif")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .body("url", startsWith("/api/images/"))
                .body("url", endsWith(".gif"));
    }

    @Test
    @Order(6)
    void testUploadWithAdminAuth_validWebp_returns200() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".webp", 256);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/webp")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .body("url", startsWith("/api/images/"))
                .body("url", endsWith(".webp"));
    }

    @Test
    @Order(7)
    void testUploadWithInvalidContentType_returns400() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".txt", 100);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "text/plain")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(8)
    void testUploadWithApplicationPdfContentType_returns400() {
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".pdf", 100);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "application/pdf")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(9)
    void testUploadFileTooLarge_returns400() {
        // Create a file > 5MB
        File tempFile;
        try {
            tempFile = createTempImageWithSize(".jpg", 5 * 1024 * 1024 + 1);
        } catch (IOException e) {
            fail("Failed to create temp file: " + e.getMessage());
            return;
        }

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/jpeg")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(10)
    void testUploadWithoutFile_returns400() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType("multipart/form-data")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // GET image tests
    // =========================================================================

    @Test
    @Order(20)
    void testGetUploadedImage_returns200WithCorrectContentType() {
        // Ensure we have an uploaded file from test order 3
        assertNotNull(uploadedFilename, "Upload test must run first to provide filename");

        given()
            .when()
                .get("/api/images/" + uploadedFilename)
            .then()
                .statusCode(200)
                .contentType("image/jpeg")
                .header("Cache-Control", containsString("public"))
                .header("Cache-Control", containsString("max-age=86400"));
    }

    @Test
    @Order(21)
    void testGetImageWithPathTraversal_dotdot_returns400() {
        given()
            .when()
                .get("/api/images/..%2Fetc%2Fpasswd")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(22)
    void testGetImageWithPathTraversal_backslash_returns400() {
        given()
            .when()
                .get("/api/images/..\\etc\\passwd")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(23)
    void testGetNonexistentImage_returns404() {
        given()
            .when()
                .get("/api/images/nonexistent-image-12345.jpg")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(24)
    void testGetImageWithPngExtension_returnsCorrectContentType() throws IOException {
        // Upload a PNG first
        File tempFile = createTempImageWithSize(".png", 256);

        String url = given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/png")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .extract().path("url");

        String pngFilename = url.replace("/api/images/", "");

        given()
            .when()
                .get("/api/images/" + pngFilename)
            .then()
                .statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", containsString("max-age=86400"));
    }

    @Test
    @Order(25)
    void testGetImageWithGifExtension_returnsCorrectContentType() throws IOException {
        File tempFile = createTempImageWithSize(".gif", 128);

        String url = given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/gif")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .extract().path("url");

        String gifFilename = url.replace("/api/images/", "");

        given()
            .when()
                .get("/api/images/" + gifFilename)
            .then()
                .statusCode(200)
                .contentType("image/gif")
                .header("Cache-Control", containsString("max-age=86400"));
    }

    @Test
    @Order(26)
    void testGetImageWithWebpExtension_returnsCorrectContentType() throws IOException {
        File tempFile = createTempImageWithSize(".webp", 128);

        String url = given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/webp")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .extract().path("url");

        String webpFilename = url.replace("/api/images/", "");

        given()
            .when()
                .get("/api/images/" + webpFilename)
            .then()
                .statusCode(200)
                .contentType("image/webp")
                .header("Cache-Control", containsString("max-age=86400"));
    }

    @Test
    @Order(27)
    void testGetImageReturnsBytes() {
        assertNotNull(uploadedFilename, "Upload test must run first to provide filename");

        byte[] body = given()
            .when()
                .get("/api/images/" + uploadedFilename)
            .then()
                .statusCode(200)
                .extract().asByteArray();

        assertNotNull(body);
        assertTrue(body.length > 0, "Image body should not be empty");
    }

    @Test
    @Order(28)
    void testUploadFileExactly5MB_returns200() throws IOException {
        // Exactly 5MB should be allowed (not exceed the limit)
        File tempFile = createTempImageWithSize(".jpg", 5 * 1024 * 1024);

        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", tempFile, "image/jpeg")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(200)
                .body("url", startsWith("/api/images/"));
    }
}
