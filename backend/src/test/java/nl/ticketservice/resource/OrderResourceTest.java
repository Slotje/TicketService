package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class OrderResourceTest {

    @Inject
    EntityManager em;

    private static Long orderId;
    private static String orderNumber;
    private static String ticketQrCodeData;
    private static Long publishedEventId;
    private static Long expiredOrderId;

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

    private Long getPublishedEventId() {
        return ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .extract()
                .path("[0].id")).longValue();
    }

    // =========================================================================
    // Test 1: Create order
    // =========================================================================
    @Test
    @Order(1)
    void testCreateOrder() {
        if (publishedEventId == null) {
            publishedEventId = ((Number) getPublishedEventId()).longValue();
        }

        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Jan\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"jan@test.nl\",\"buyerPhone\":\"+31612345678\",\"quantity\":2}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .body("orderNumber", startsWith("ORD-"))
                .body("quantity", equalTo(2))
                .body("tickets.size()", equalTo(2))
                .body("buyerFirstName", equalTo("Jan"))
                .body("buyerLastName", equalTo("Test"))
                .body("buyerEmail", equalTo("jan@test.nl"))
                .body("buyerPhone", equalTo("+31612345678"))
                .body("eventId", equalTo(publishedEventId.intValue()))
                .extract()
                .response();

        orderId = ((Number) response.path("id")).longValue();
        orderNumber = response.path("orderNumber");
        ticketQrCodeData = response.path("tickets[0].qrCodeData");
    }

    // =========================================================================
    // Test 2: Get order by ID
    // =========================================================================
    @Test
    @Order(2)
    void testGetOrderById() {
        given()
            .when()
                .get("/api/orders/" + orderId)
            .then()
                .statusCode(200)
                .body("id", equalTo(orderId.intValue()))
                .body("orderNumber", equalTo(orderNumber))
                .body("buyerFirstName", equalTo("Jan"))
                .body("buyerLastName", equalTo("Test"))
                .body("buyerEmail", equalTo("jan@test.nl"))
                .body("status", equalTo("RESERVED"))
                .body("quantity", equalTo(2))
                .body("tickets.size()", equalTo(2));
    }

    // =========================================================================
    // Test 3: Get order by order number
    // =========================================================================
    @Test
    @Order(3)
    void testGetOrderByNumber() {
        given()
            .when()
                .get("/api/orders/number/" + orderNumber)
            .then()
                .statusCode(200)
                .body("id", equalTo(orderId.intValue()))
                .body("orderNumber", equalTo(orderNumber))
                .body("buyerFirstName", equalTo("Jan"))
                .body("buyerLastName", equalTo("Test"));
    }

    // =========================================================================
    // Test 4: Get orders by email
    // =========================================================================
    @Test
    @Order(4)
    void testGetOrdersByEmail() {
        given()
            .when()
                .get("/api/orders/email/jan@test.nl")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1));
    }

    // =========================================================================
    // Test 5: Get orders by event with admin token
    // =========================================================================
    @Test
    @Order(5)
    void testGetOrdersByEventWithAdminToken() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/orders/event/" + publishedEventId)
            .then()
                .statusCode(200);
    }

    // =========================================================================
    // Test 6: Get orders by event without admin token → 401
    // =========================================================================
    @Test
    @Order(6)
    void testGetOrdersByEventWithoutAdminToken() {
        given()
            .when()
                .get("/api/orders/event/" + publishedEventId)
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // Test 7: Confirm order
    // =========================================================================
    @Test
    @Order(7)
    void testConfirmOrder() {
        setBuyerDetails(orderId);
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + orderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"))
                .body("confirmedAt", notNullValue());
    }

    // =========================================================================
    // Test 8: Confirm order again → 400
    // =========================================================================
    @Test
    @Order(8)
    void testConfirmOrderAgain() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + orderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 9: Download PDF
    // =========================================================================
    @Test
    @Order(9)
    void testDownloadPdf() {
        given()
            .when()
                .get("/api/orders/" + orderId + "/pdf")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .body(notNullValue());
    }

    // =========================================================================
    // Test 10: Get QR code image
    // =========================================================================
    @Test
    @Order(10)
    void testGetQrCodeImage() {
        given()
            .when()
                .get("/api/orders/ticket/" + ticketQrCodeData + "/qr")
            .then()
                .statusCode(200)
                .contentType("image/png");
    }

    // =========================================================================
    // Test 11: Scan ticket with scanner token
    // =========================================================================
    @Test
    @Order(11)
    void testScanTicket() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + ticketQrCodeData)
            .then()
                .statusCode(200)
                .body("scanned", equalTo(true))
                .body("scannedAt", notNullValue())
                .body("qrCodeData", equalTo(ticketQrCodeData));
    }

    // =========================================================================
    // Test 12: Scan ticket again → 400
    // =========================================================================
    @Test
    @Order(12)
    void testScanTicketAgain() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + ticketQrCodeData)
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 13: Scan nonexistent ticket → 404
    // =========================================================================
    @Test
    @Order(13)
    void testScanNonexistentTicket() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/nonexistent-qr")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 14: Scan ticket without auth → 401
    // =========================================================================
    @Test
    @Order(14)
    void testScanTicketWithoutAuth() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/scan/" + ticketQrCodeData)
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // Test 17: Create order for nonexistent event → 404
    // =========================================================================
    @Test
    @Order(17)
    void testCreateOrderNonexistentEvent() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":999999,\"buyerFirstName\":\"Nobody\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"nobody@test.nl\",\"buyerPhone\":\"+31600000001\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 18: (merged with 17 - same scenario) Create order with nonexistent eventId
    // =========================================================================
    @Test
    @Order(18)
    void testCreateOrderNonexistentEventId() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":888888,\"buyerFirstName\":\"Test\",\"buyerLastName\":\"User\",\"buyerEmail\":\"test@test.nl\",\"buyerPhone\":\"+31600000002\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 19: Confirm nonexistent order → 404
    // =========================================================================
    @Test
    @Order(19)
    void testConfirmNonexistentOrder() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/999999/confirm")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 21: Get order by nonexistent order number → 404
    // =========================================================================
    @Test
    @Order(21)
    void testGetOrderByNonexistentNumber() {
        given()
            .when()
                .get("/api/orders/number/NONEXISTENT")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 22: Get nonexistent order by ID → 404
    // =========================================================================
    @Test
    @Order(22)
    void testGetNonexistentOrderById() {
        given()
            .when()
                .get("/api/orders/999999")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 23: Scan with invalid signed QR code → 400
    // =========================================================================
    @Test
    @Order(23)
    void testScanInvalidSignedQrCode() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/fake-ticket-data|invalidsignature")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 26: Create order for expiration test
    // =========================================================================
    @Test
    @Order(26)
    void testCreateOrderForExpiration() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Expired\",\"buyerLastName\":\"User\",\"buyerEmail\":\"expired@test.nl\",\"buyerPhone\":\"+31600000004\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        expiredOrderId = ((Number) response.path("id")).longValue();
    }

    // =========================================================================
    // Test 27: Expire the order
    // =========================================================================
    @Test
    @Order(27)
    @Transactional
    void testExpireOrder() {
        em.createQuery("UPDATE TicketOrder o SET o.expiresAt = :past WHERE o.id = :id")
                .setParameter("past", java.time.LocalDateTime.now().minusHours(1))
                .setParameter("id", expiredOrderId)
                .executeUpdate();
    }

    // =========================================================================
    // Test 28: Confirm expired reservation → 400
    // =========================================================================
    @Test
    @Order(28)
    void testConfirmExpiredReservation() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + expiredOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 30: Create order for a CANCELLED event → 400
    // =========================================================================
    @Test
    @Order(30)
    void testCreateOrderForCancelledEvent() {
        String adminToken = getAdminToken();

        // Get a customer ID from published events
        Number customerId = given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].customerId");

        // Create a new event
        String eventBody = "{" +
                "\"name\":\"Cancelled Event Test\"," +
                "\"description\":\"Event that will be cancelled\"," +
                "\"eventDate\":\"2027-06-01T20:00:00\"," +
                "\"endDate\":\"2027-06-01T23:00:00\"," +
                "\"location\":\"Test Location\"," +
                "\"address\":\"Test Address 1\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":15.00," +
                "\"serviceFee\":1.50," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        Long cancelledEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Set event status to CANCELLED
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"status\":\"CANCELLED\"}")
            .when()
                .patch("/api/events/" + cancelledEventId + "/status")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));

        // Try to create order for cancelled event → 400
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + cancelledEventId + ",\"buyerFirstName\":\"Test\",\"buyerLastName\":\"Buyer\",\"buyerEmail\":\"cancelled@test.nl\",\"buyerPhone\":\"+31600000010\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 31: Create order exceeding maxTicketsPerOrder → 400
    // =========================================================================
    @Test
    @Order(31)
    void testCreateOrderExceedingMaxTicketsPerOrder() {
        // Published events have maxTicketsPerOrder between 5-10
        // Request quantity > 10 to exceed both the event and global max
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Greedy\",\"buyerLastName\":\"Buyer\",\"buyerEmail\":\"greedy@test.nl\",\"buyerPhone\":\"+31600000011\",\"quantity\":50}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 32: Scan ticket for unconfirmed (RESERVED) order → 400
    // =========================================================================
    private static String unconfirmedTicketQrCodeData;

    @Test
    @Order(32)
    void testScanTicketForUnconfirmedOrder() {
        // Create a new order (will be RESERVED)
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Unconfirmed\",\"buyerLastName\":\"Buyer\",\"buyerEmail\":\"unconfirmed@test.nl\",\"buyerPhone\":\"+31600000012\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        unconfirmedTicketQrCodeData = response.path("tickets[0].qrCodeData");

        // Try to scan ticket without confirming the order → 400
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + unconfirmedTicketQrCodeData)
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Test 33: Get QR code for nonexistent ticket data → 200
    // =========================================================================
    @Test
    @Order(33)
    void testGetQrCodeForNonexistentTicketData() {
        given()
            .when()
                .get("/api/orders/ticket/nonexistent-data/qr")
            .then()
                .statusCode(200)
                .contentType("image/png");
    }

    // =========================================================================
    // Test 34: Download PDF for nonexistent order → 404
    // =========================================================================
    @Test
    @Order(34)
    void testDownloadPdfForNonexistentOrder() {
        given()
            .when()
                .get("/api/orders/999999/pdf")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Test 35: Scan ticket with correct eventId → 200
    // =========================================================================
    @Test
    @Order(35)
    void testScanTicketWithCorrectEventId() {
        // Create and confirm a new order to get a fresh unscanend ticket
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Event\",\"buyerLastName\":\"Scantest\",\"buyerEmail\":\"eventscan@test.nl\",\"buyerPhone\":\"+31600000020\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long newOrderId = ((Number) response.path("id")).longValue();
        String newQrCode = response.path("tickets[0].qrCodeData");

        setBuyerDetails(newOrderId);
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + newOrderId + "/confirm")
            .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + newQrCode + "?eventId=" + publishedEventId)
            .then()
                .statusCode(200)
                .body("scanned", equalTo(true));
    }

    // =========================================================================
    // Test 36: Scan ticket with wrong eventId → 400
    // =========================================================================
    @Test
    @Order(36)
    void testScanTicketWithWrongEventId() {
        // Create and confirm a new order
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Wrong\",\"buyerLastName\":\"Eventtest\",\"buyerEmail\":\"wrongevent@test.nl\",\"buyerPhone\":\"+31600000021\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long newOrderId = ((Number) response.path("id")).longValue();
        String newQrCode = response.path("tickets[0].qrCodeData");

        setBuyerDetails(newOrderId);
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + newOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Scan with a different eventId → should fail
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + newQrCode + "?eventId=999999")
            .then()
                .statusCode(400)
                .body("error", containsString("hoort niet bij dit evenement"));
    }
}
