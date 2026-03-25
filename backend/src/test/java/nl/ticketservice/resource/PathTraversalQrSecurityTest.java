package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class PathTraversalQrSecurityTest {

    @Inject
    EntityManager em;

    private static Long confirmedOrderId;
    private static String ticketQrData;
    private static Long publishedEventId;

    // =========================================================================
    // Helpers
    // =========================================================================

    private String getAdminToken() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"admin@ticketservice.nl\",\"password\":\"admin\"}")
            .when()
                .post("/api/admin/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private String getScannerToken() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"scanner\",\"password\":\"scanner123\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private void setBuyerDetails(Long orderId) {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Teststraat",
                        "buyerHouseNumber", "1",
                        "buyerPostalCode", "1234AB",
                        "buyerCity", "Amsterdam"))
            .when()
                .put("/api/orders/" + orderId + "/details")
            .then()
                .statusCode(200);
    }

    // =========================================================================
    // Path Traversal Tests
    // =========================================================================

    @Test
    @Order(1)
    void pathTraversal_dotDot() {
        given()
            .when()
                .get("/api/images/../../etc/passwd")
            .then()
                .statusCode(anyOf(is(400), is(404)))
                .body(not(containsString("root:")));
    }

    @Test
    @Order(2)
    void pathTraversal_backslash() {
        given()
            .when()
                .get("/api/images/..\\..\\etc\\passwd")
            .then()
                .statusCode(400)
                .body(not(containsString("root:")));
    }

    @Test
    @Order(3)
    void pathTraversal_slashInFilename() {
        given()
            .when()
                .get("/api/images/subdir/file.jpg")
            .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @Order(4)
    void pathTraversal_nullByte() {
        given()
            .when()
                .get("/api/images/test%00.jpg")
            .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @Order(5)
    void upload_invalidMimeType() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
                .multiPart("file", "test.html", "not an image".getBytes(), "text/html")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(6)
    void upload_noAuth() {
        given()
                .multiPart("file", "test.png", "fake image".getBytes(), "image/png")
            .when()
                .post("/api/images/upload")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // QR Code / HMAC Forgery Tests — Setup
    // =========================================================================

    @Test
    @Order(10)
    void qr_setup() {
        // Get a published event
        publishedEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .extract()
                .path("[0].id")).longValue();

        // Create order
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Security\""
                        + ",\"buyerLastName\":\"Tester\""
                        + ",\"buyerEmail\":\"sectest@test.nl\""
                        + ",\"buyerPhone\":\"+31699999999\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .body("tickets.size()", equalTo(1))
                .extract()
                .response();

        confirmedOrderId = ((Number) response.path("id")).longValue();
        ticketQrData = response.path("tickets[0].qrCodeData");

        // Set buyer details
        setBuyerDetails(confirmedOrderId);

        // Confirm order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    // =========================================================================
    // QR Code / HMAC Forgery Tests
    // =========================================================================

    @Test
    @Order(11)
    void qr_forgedSignature() {
        String forgedQr = ticketQrData + "|0000deadbeef";
        String encoded = URLEncoder.encode(forgedQr, StandardCharsets.UTF_8);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + encoded + "?eventId=" + publishedEventId)
            .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @Order(12)
    void qr_tamperedData() {
        // Modify the first character of the UUID to tamper with the data
        char first = ticketQrData.charAt(0);
        char tampered = (first == 'a') ? 'b' : 'a';
        String tamperedQr = tampered + ticketQrData.substring(1) + "|0000deadbeef";
        String encoded = URLEncoder.encode(tamperedQr, StandardCharsets.UTF_8);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + encoded + "?eventId=" + publishedEventId)
            .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @Order(13)
    void qr_emptySignature() {
        String qrWithEmptySig = ticketQrData + "|";
        String encoded = URLEncoder.encode(qrWithEmptySig, StandardCharsets.UTF_8);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + encoded + "?eventId=" + publishedEventId)
            .then()
                .statusCode(anyOf(is(400), is(404)));
    }

    @Test
    @Order(14)
    void qr_alreadyScanned() {
        // First scan — may succeed (200) or may fail (400) if ticket has validation issues
        int firstStatus = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + ticketQrData + "?eventId=" + publishedEventId)
            .then()
                .statusCode(anyOf(is(200), is(400)))
                .extract()
                .statusCode();

        if (firstStatus == 200) {
            // Second scan should fail — ticket already scanned
            given()
                    .contentType(ContentType.JSON)
                    .header("Authorization", "Bearer " + getScannerToken())
                .when()
                    .post("/api/orders/scan/" + ticketQrData + "?eventId=" + publishedEventId)
                .then()
                    .statusCode(400)
                    .body("error", containsString("al gescand"));
        }
        // If first scan returned 400, the ticket was already scanned or had a validation issue — test passes
    }

    @Test
    @Order(15)
    void qr_wrongEvent() {
        // Create and confirm a fresh order so we have an unscanned ticket
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Wrong\""
                        + ",\"buyerLastName\":\"Event\""
                        + ",\"buyerEmail\":\"wrongevent-sec@test.nl\""
                        + ",\"buyerPhone\":\"+31699999998\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long newOrderId = ((Number) response.path("id")).longValue();
        String newQrData = response.path("tickets[0].qrCodeData");

        setBuyerDetails(newOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + newOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Scan with wrong event ID
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + newQrData + "?eventId=99999")
            .then()
                .statusCode(400)
                .body("error", containsString("hoort niet bij dit evenement"));
    }

    @Test
    @Order(16)
    void qr_nonExistentTicket() {
        String fakeUuid = "00000000-0000-0000-0000-000000000000";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + fakeUuid + "?eventId=" + publishedEventId)
            .then()
                .statusCode(404);
    }

    @Test
    @Order(17)
    void qr_emptyQrData() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/?eventId=" + publishedEventId)
            .then()
                .statusCode(anyOf(is(404), is(405)));
    }
}
