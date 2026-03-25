package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.TicketOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class OrderResourceCoverageTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static Long customerId;

    // IDs created during tests
    private static Long publishedEventId;
    private static Long cancelOrderId;
    private static Long confirmedOrderId;
    private static Long categorySoldOutEventId;
    private static Long categorySoldOutCategoryId;
    private static Long eventSoldOutEventId;
    private static Long draftEventId;
    private static Long exceedMaxEventId;
    private static Long expireConfirmOrderId;
    private static Long missingDetailsOrderId;
    private static Long confirmNonReservedOrderId;
    private static Long expireUpdateOrderId;
    private static Long updateNotReservedOrderId;
    private static Long scanAlreadyScannedOrderId;
    private static String scanAlreadyScannedQr;
    private static Long scanMismatchOrderId;
    private static String scanMismatchQr;
    private static Long soldOutTriggerEventId;
    private static Long cancelCategoryEventId;
    private static Long cancelCategoryCategoryId;
    private static Long cancelCategoryOrderId;

    // =========================================================================
    // Helpers
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

    private String getScannerToken() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"scanner\",\"password\":\"scanner123\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(200)
                .extract().path("token");
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

    @Transactional
    String getInviteTokenForEmail(String email) {
        Customer customer = em.createQuery(
                "SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                .setParameter("email", email)
                .getSingleResult();
        return customer.inviteToken;
    }

    private String createAndLoginCustomer(String companyName, String contactPerson, String email) {
        String admin = getAdminToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + admin)
                .body("{\"companyName\":\"" + companyName + "\","
                        + "\"contactPerson\":\"" + contactPerson + "\","
                        + "\"email\":\"" + email + "\","
                        + "\"phone\":\"+31 6 12345678\","
                        + "\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201);

        String inviteToken = getInviteTokenForEmail(email);
        assertNotNull(inviteToken);

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + inviteToken + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200);

        return given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract().path("token");
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

    private Long createEvent(String name, int maxTickets, int maxTicketsPerOrder, double ticketPrice) {
        String body = "{" +
                "\"name\":\"" + name + "\"," +
                "\"description\":\"Test event\"," +
                "\"eventDate\":\"2028-06-15T14:00:00\"," +
                "\"endDate\":\"2028-06-15T22:00:00\"," +
                "\"location\":\"Test Location\"," +
                "\"address\":\"Test Address 1\"," +
                "\"maxTickets\":" + maxTickets + "," +
                "\"ticketPrice\":" + ticketPrice + "," +
                "\"serviceFee\":1.00," +
                "\"maxTicketsPerOrder\":" + maxTicketsPerOrder + "," +
                "\"customerId\":" + customerId +
                "}";

        return ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract().path("id")).longValue();
    }

    private void publishEvent(Long eventId) {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + eventId + "/status")
            .then()
                .statusCode(200);
    }

    private Long createOrder(Long eventId, int quantity, String email) {
        return createOrder(eventId, quantity, email, null);
    }

    private Long createOrder(Long eventId, int quantity, String email, Long categoryId) {
        String categoryField = categoryId != null ? ",\"ticketCategoryId\":" + categoryId : "";
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + eventId
                        + ",\"buyerFirstName\":\"Test\""
                        + ",\"buyerLastName\":\"Buyer\""
                        + ",\"buyerEmail\":\"" + email + "\""
                        + ",\"buyerPhone\":\"+31600000099\""
                        + ",\"quantity\":" + quantity
                        + categoryField + "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract().response();

        return ((Number) response.path("id")).longValue();
    }

    @Transactional
    void expireOrder(Long orderId) {
        TicketOrder order = TicketOrder.findById(orderId);
        order.expiresAt = LocalDateTime.now().minusMinutes(1);
    }

    // =========================================================================
    // Setup
    // =========================================================================

    @Test
    @Order(1)
    void setup_createCustomer() {
        createAndLoginCustomer("OrderCovBV", "Order Tester", "ordercov@example.com");

        // Get the customer ID
        customerId = ((Number) given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract().path("find { it.email == 'ordercov@example.com' }.id")).longValue();

        publishedEventId = getPublishedEventId();
    }

    // =========================================================================
    // 1. Cancel order - RESERVED -> 200 CANCELLED
    // =========================================================================

    @Test
    @Order(10)
    void testCancelReservedOrder_returns200() {
        cancelOrderId = createOrder(publishedEventId, 1, "cancel-reserved@test.nl");

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelOrderId + "/cancel")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // =========================================================================
    // 1b. Cancel order - CONFIRMED -> 400
    // =========================================================================

    @Test
    @Order(11)
    void testCancelConfirmedOrder_returns400() {
        confirmedOrderId = createOrder(publishedEventId, 1, "cancel-confirmed@test.nl");
        setBuyerDetails(confirmedOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/cancel")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 1c. Cancel nonexistent order -> 404
    // =========================================================================

    @Test
    @Order(12)
    void testCancelNonexistentOrder_returns404() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/999999/cancel")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // 2. Category sold out
    // =========================================================================

    @Test
    @Order(20)
    void testCreateOrderCategorySoldOut_returns400() {
        categorySoldOutEventId = createEvent("Category Sold Out Event", 100, 5, 10.00);
        publishEvent(categorySoldOutEventId);

        // Create category with maxTickets=2
        String catBody = "{" +
                "\"name\":\"Limited Cat\"," +
                "\"price\":15.00," +
                "\"maxTickets\":2," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        categorySoldOutCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(catBody)
            .when()
                .post("/api/events/" + categorySoldOutEventId + "/categories")
            .then()
                .statusCode(200)
                .extract().path("id")).longValue();

        // Order 2 tickets (fills category)
        createOrder(categorySoldOutEventId, 2, "catsold1@test.nl", categorySoldOutCategoryId);

        // Try to order 1 more -> 400 uitverkocht
        String categoryField = ",\"ticketCategoryId\":" + categorySoldOutCategoryId;
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + categorySoldOutEventId
                        + ",\"buyerFirstName\":\"Test\""
                        + ",\"buyerLastName\":\"Buyer\""
                        + ",\"buyerEmail\":\"catsold2@test.nl\""
                        + ",\"buyerPhone\":\"+31600000099\""
                        + ",\"quantity\":1"
                        + categoryField + "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .body("error", containsString("uitverkocht"));
    }

    // =========================================================================
    // 3. Event sold out
    // =========================================================================

    @Test
    @Order(30)
    void testCreateOrderEventSoldOut_returns400() {
        eventSoldOutEventId = createEvent("Event Sold Out Test", 1, 1, 10.00);
        publishEvent(eventSoldOutEventId);

        // Order 1 ticket
        Long ordId = createOrder(eventSoldOutEventId, 1, "eventsold1@test.nl");
        setBuyerDetails(ordId);

        // Confirm to move to ticketsSold
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + ordId + "/confirm")
            .then()
                .statusCode(200);

        // Try to order another -> 400 uitverkocht
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + eventSoldOutEventId
                        + ",\"buyerFirstName\":\"Test\""
                        + ",\"buyerLastName\":\"Buyer\""
                        + ",\"buyerEmail\":\"eventsold2@test.nl\""
                        + ",\"buyerPhone\":\"+31600000099\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .body("error", containsString("niet beschikbaar"));
    }

    // =========================================================================
    // 4. Event not published (DRAFT)
    // =========================================================================

    @Test
    @Order(40)
    void testCreateOrderDraftEvent_returns400() {
        draftEventId = createEvent("Draft Event Test", 100, 5, 10.00);
        // Do NOT publish

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + draftEventId
                        + ",\"buyerFirstName\":\"Test\""
                        + ",\"buyerLastName\":\"Buyer\""
                        + ",\"buyerEmail\":\"draft@test.nl\""
                        + ",\"buyerPhone\":\"+31600000099\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 5. Exceed max per order
    // =========================================================================

    @Test
    @Order(50)
    void testCreateOrderExceedMax_returns400() {
        exceedMaxEventId = createEvent("Exceed Max Event", 100, 2, 10.00);
        publishEvent(exceedMaxEventId);

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + exceedMaxEventId
                        + ",\"buyerFirstName\":\"Test\""
                        + ",\"buyerLastName\":\"Buyer\""
                        + ",\"buyerEmail\":\"exceed@test.nl\""
                        + ",\"buyerPhone\":\"+31600000099\""
                        + ",\"quantity\":5}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 6. Confirm order - expired
    // =========================================================================

    @Test
    @Order(60)
    void testConfirmExpiredOrder_returns400() {
        expireConfirmOrderId = createOrder(publishedEventId, 1, "expire-confirm@test.nl");
        setBuyerDetails(expireConfirmOrderId);
        expireOrder(expireConfirmOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + expireConfirmOrderId + "/confirm")
            .then()
                .statusCode(400)
                .body("error", containsString("verlopen"));
    }

    // =========================================================================
    // 7. Confirm order - missing buyer details
    // =========================================================================

    @Test
    @Order(70)
    void testConfirmOrderMissingDetails_returns400() {
        missingDetailsOrderId = createOrder(publishedEventId, 1, "missing-details@test.nl");
        // Do NOT set buyer details

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + missingDetailsOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 8. Confirm non-RESERVED order
    // =========================================================================

    @Test
    @Order(80)
    void testConfirmNonReservedOrder_returns400() {
        confirmNonReservedOrderId = createOrder(publishedEventId, 1, "non-reserved@test.nl");
        setBuyerDetails(confirmNonReservedOrderId);

        // Confirm first time
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmNonReservedOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Try to confirm again -> 400
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmNonReservedOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 9. Update buyer details - expired
    // =========================================================================

    @Test
    @Order(90)
    void testUpdateDetailsExpired_returns400() {
        expireUpdateOrderId = createOrder(publishedEventId, 1, "expire-update@test.nl");
        expireOrder(expireUpdateOrderId);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Expiredstraat",
                        "buyerHouseNumber", "99",
                        "buyerPostalCode", "9999ZZ",
                        "buyerCity", "Expired"))
            .when()
                .put("/api/orders/" + expireUpdateOrderId + "/details")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 10. Update buyer details - not RESERVED
    // =========================================================================

    @Test
    @Order(100)
    void testUpdateDetailsNotReserved_returns400() {
        updateNotReservedOrderId = createOrder(publishedEventId, 1, "update-notres@test.nl");
        setBuyerDetails(updateNotReservedOrderId);

        // Confirm
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + updateNotReservedOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Try to update details on confirmed order -> 400
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Newstraat",
                        "buyerHouseNumber", "2",
                        "buyerPostalCode", "5678CD",
                        "buyerCity", "Rotterdam"))
            .when()
                .put("/api/orders/" + updateNotReservedOrderId + "/details")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 11. Scan ticket - no auth
    // =========================================================================

    @Test
    @Order(110)
    void testScanTicketNoAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/scan/some-qr-code")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // 12. Scan ticket - invalid token
    // =========================================================================

    @Test
    @Order(120)
    void testScanTicketInvalidToken_returns401() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer invalid-token-here")
            .when()
                .post("/api/orders/scan/some-qr-code")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // 13. Scan ticket - already scanned
    // =========================================================================

    @Test
    @Order(130)
    void testScanTicketAlreadyScanned_returns400() {
        // Create and confirm an order
        scanAlreadyScannedOrderId = createOrder(publishedEventId, 1, "scan-twice@test.nl");
        setBuyerDetails(scanAlreadyScannedOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + scanAlreadyScannedOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Get the QR code
        scanAlreadyScannedQr = given()
            .when()
                .get("/api/orders/" + scanAlreadyScannedOrderId)
            .then()
                .statusCode(200)
                .extract().path("tickets[0].qrCodeData");

        String scannerToken = getScannerToken();

        // First scan -> 200
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + scannerToken)
            .when()
                .post("/api/orders/scan/" + scanAlreadyScannedQr)
            .then()
                .statusCode(200);

        // Second scan -> 400
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + scannerToken)
            .when()
                .post("/api/orders/scan/" + scanAlreadyScannedQr)
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 14. Scan ticket with eventId mismatch
    // =========================================================================

    @Test
    @Order(140)
    void testScanTicketEventIdMismatch_returns400() {
        scanMismatchOrderId = createOrder(publishedEventId, 1, "scan-mismatch@test.nl");
        setBuyerDetails(scanMismatchOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + scanMismatchOrderId + "/confirm")
            .then()
                .statusCode(200);

        scanMismatchQr = given()
            .when()
                .get("/api/orders/" + scanMismatchOrderId)
            .then()
                .statusCode(200)
                .extract().path("tickets[0].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + scanMismatchQr + "?eventId=999999")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 15. Scan ticket - not found
    // =========================================================================

    @Test
    @Order(150)
    void testScanTicketNotFound_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/nonexistent-qr-code-xyz")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // 16. Download PDF - nonexistent order
    // =========================================================================

    @Test
    @Order(160)
    void testDownloadPdfNonexistent_returns404() {
        given()
            .when()
                .get("/api/orders/999999/pdf")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // 17. Get order by email - no results
    // =========================================================================

    @Test
    @Order(170)
    void testGetOrderByEmailNoResults_returns200Empty() {
        given()
            .when()
                .get("/api/orders/email/nobody@test.nl")
            .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));
    }

    // =========================================================================
    // 18. Confirm triggers SOLD_OUT status on event
    // =========================================================================

    @Test
    @Order(180)
    void testConfirmOrderTriggersSoldOut() {
        soldOutTriggerEventId = createEvent("Sold Out Trigger Event", 1, 1, 10.00);
        publishEvent(soldOutTriggerEventId);

        Long ordId = createOrder(soldOutTriggerEventId, 1, "soldout-trigger@test.nl");
        setBuyerDetails(ordId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + ordId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Verify event status is now SOLD_OUT
        given()
            .when()
                .get("/api/events/" + soldOutTriggerEventId)
            .then()
                .statusCode(200)
                .body("status", equalTo("SOLD_OUT"));
    }

    // =========================================================================
    // 19. Cancel order with category -> ticketsReserved decremented
    // =========================================================================

    @Test
    @Order(190)
    void testCancelOrderWithCategory() {
        cancelCategoryEventId = createEvent("Cancel Category Event", 100, 5, 10.00);
        publishEvent(cancelCategoryEventId);

        // Create category
        String catBody = "{" +
                "\"name\":\"Cancel Cat\"," +
                "\"price\":20.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        cancelCategoryCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(catBody)
            .when()
                .post("/api/events/" + cancelCategoryEventId + "/categories")
            .then()
                .statusCode(200)
                .extract().path("id")).longValue();

        // Create order with category
        cancelCategoryOrderId = createOrder(cancelCategoryEventId, 2, "cancel-cat@test.nl", cancelCategoryCategoryId);

        // Cancel order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelCategoryOrderId + "/cancel")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));

        // Verify the event ticketsReserved went back (should be 0 now since we cancelled the only order)
        given()
            .when()
                .get("/api/events/" + cancelCategoryEventId)
            .then()
                .statusCode(200)
                .body("ticketsReserved", equalTo(0));
    }
}
