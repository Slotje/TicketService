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

import java.time.LocalDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class BusinessLogicSecurityTest {

    @Inject
    EntityManager em;

    private static Long publishedEventId;
    private static Long draftEventId;
    private static Long orderId;
    private static Long confirmedOrderId;

    // =========================================================================
    // Helper methods
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
    void expireOrder(Long orderId) {
        TicketOrder order = em.find(TicketOrder.class, orderId);
        order.expiresAt = LocalDateTime.now().minusHours(1);
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
    // Business Logic Abuse Tests (Orders 1–12)
    // =========================================================================

    // Test 1: Negative quantity should be rejected
    @Test
    @Order(1)
    void biz_negativeQuantity() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Neg\",\"buyerLastName\":\"Qty\",\"buyerEmail\":\"neg@test.nl\",\"quantity\":-1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // Test 2: Zero quantity should be rejected
    @Test
    @Order(2)
    void biz_zeroQuantity() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Zero\",\"buyerLastName\":\"Qty\",\"buyerEmail\":\"zero@test.nl\",\"quantity\":0}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // Test 3: Exceeding max tickets per order should be rejected
    @Test
    @Order(3)
    void biz_exceedMaxTickets() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Max\",\"buyerLastName\":\"Exceed\",\"buyerEmail\":\"max@test.nl\",\"quantity\":99}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // Test 4: Ordering on a DRAFT event should be rejected
    @Test
    @Order(4)
    void biz_orderOnDraftEvent() {
        String adminToken = getAdminToken();

        // Get a customer ID from existing customers
        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        // Create a DRAFT event (do NOT publish it)
        draftEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"Draft Event\",\"description\":\"test\",\"eventDate\":\"2027-09-01T20:00:00\",\"location\":\"Test\",\"maxTickets\":100,\"physicalTickets\":0,\"ticketPrice\":10.00,\"serviceFee\":0,\"maxTicketsPerOrder\":10,\"physicalTicketsGenerated\":false,\"showAvailability\":true,\"status\":\"DRAFT\",\"customerId\":" + customerId + ",\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Try to order on the draft event
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + draftEventId + ",\"buyerFirstName\":\"Draft\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"draft@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // Test 5: Confirming an order without setting address should be rejected
    @Test
    @Order(5)
    void biz_confirmWithoutAddress() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        // Create order (RESERVED)
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"NoAddr\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"noaddr@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        orderId = ((Number) response.path("id")).longValue();

        // Try to confirm without setting address
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + orderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // Test 6: Double confirm should be rejected
    @Test
    @Order(6)
    void biz_doubleConfirm() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        // Create order
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Double\",\"buyerLastName\":\"Confirm\",\"buyerEmail\":\"double@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        confirmedOrderId = ((Number) response.path("id")).longValue();

        // Set address and confirm
        setBuyerDetails(confirmedOrderId);
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Try to confirm again
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // Test 7: Cancelling a confirmed order should be rejected (only RESERVED can be cancelled)
    @Test
    @Order(7)
    void biz_cancelConfirmedOrder() {
        // confirmedOrderId was set in test 6
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/cancel")
            .then()
                .statusCode(400);
    }

    // Test 8: Confirming an expired reservation should be rejected
    @Test
    @Order(8)
    void biz_confirmExpiredReservation() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        // Create order
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Expired\",\"buyerLastName\":\"Res\",\"buyerEmail\":\"expired@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        Long expiredOrderId = ((Number) response.path("id")).longValue();

        // Set address first
        setBuyerDetails(expiredOrderId);

        // Expire the order via transactional helper
        expireOrder(expiredOrderId);

        // Try to confirm expired reservation
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + expiredOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    // Test 9: Updating details on a confirmed order should be rejected
    @Test
    @Order(9)
    void biz_updateDetailsOnConfirmed() {
        // confirmedOrderId was set in test 6
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Hacked Street",
                        "buyerHouseNumber", "99",
                        "buyerPostalCode", "9999ZZ",
                        "buyerCity", "Hacktown"))
            .when()
                .put("/api/orders/" + confirmedOrderId + "/details")
            .then()
                .statusCode(400);
    }

    // Test 10: Ordering for a non-existent event should return 404
    @Test
    @Order(10)
    void biz_orderNonExistentEvent() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":999999,\"buyerFirstName\":\"Ghost\",\"buyerLastName\":\"Event\",\"buyerEmail\":\"ghost@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404);
    }

    // Test 11: Mass reservation beyond capacity should be rejected (uitverkocht)
    @Test
    @Order(11)
    void biz_massReservation() {
        String adminToken = getAdminToken();

        // Get a customer ID from existing customers
        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        // Create event with maxTickets=2
        Long smallEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"Small Event\",\"description\":\"test\",\"eventDate\":\"2027-09-01T20:00:00\",\"location\":\"Test\",\"maxTickets\":2,\"physicalTickets\":0,\"ticketPrice\":10.00,\"serviceFee\":0,\"maxTicketsPerOrder\":10,\"physicalTicketsGenerated\":false,\"showAvailability\":true,\"status\":\"DRAFT\",\"customerId\":" + customerId + ",\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Publish the event
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"status\":\"PUBLISHED\"}")
            .when()
                .patch("/api/events/" + smallEventId + "/status")
            .then()
                .statusCode(200);

        // Reserve all 2 tickets
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + smallEventId + ",\"buyerFirstName\":\"Mass\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"mass@test.nl\",\"quantity\":2}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200);

        // Try to reserve more - should fail (uitverkocht)
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + smallEventId + ",\"buyerFirstName\":\"More\",\"buyerLastName\":\"Test\",\"buyerEmail\":\"more@test.nl\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // Test 12: Deleting an event with orders should be rejected
    @Test
    @Order(12)
    void biz_deleteEventWithSales() {
        // publishedEventId has orders from previous tests
        String adminToken = getAdminToken();

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .delete("/api/events/" + publishedEventId)
            .then()
                .statusCode(anyOf(is(409), is(400)));
    }

    // =========================================================================
    // Input Validation Tests (Orders 20–30)
    // =========================================================================

    // Test 20: Extra long event name should be rejected
    @Test
    @Order(20)
    void input_extraLongEventName() {
        String adminToken = getAdminToken();

        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        String longName = "A".repeat(10000);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"" + longName + "\",\"description\":\"test\",\"eventDate\":\"2027-09-01T20:00:00\",\"location\":\"Test\",\"maxTickets\":100,\"physicalTickets\":0,\"ticketPrice\":10.00,\"serviceFee\":0,\"maxTicketsPerOrder\":10,\"physicalTicketsGenerated\":false,\"showAvailability\":true,\"status\":\"DRAFT\",\"customerId\":" + customerId + ",\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(400);
    }

    // Test 21: Missing required fields (name is null) should be rejected
    @Test
    @Order(21)
    void input_missingRequiredFields() {
        String adminToken = getAdminToken();

        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":null,\"description\":\"test\",\"eventDate\":\"2027-09-01T20:00:00\",\"location\":\"Test\",\"maxTickets\":100,\"physicalTickets\":0,\"ticketPrice\":10.00,\"serviceFee\":0,\"maxTicketsPerOrder\":10,\"physicalTicketsGenerated\":false,\"showAvailability\":true,\"status\":\"DRAFT\",\"customerId\":" + customerId + ",\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(400);
    }

    // Test 22: Negative price should be rejected
    @Test
    @Order(22)
    void input_negativePrice() {
        String adminToken = getAdminToken();

        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"Negative Price Event\",\"description\":\"test\",\"eventDate\":\"2027-09-01T20:00:00\",\"location\":\"Test\",\"maxTickets\":100,\"physicalTickets\":0,\"ticketPrice\":-10.00,\"serviceFee\":0,\"maxTicketsPerOrder\":10,\"physicalTicketsGenerated\":false,\"showAvailability\":true,\"status\":\"DRAFT\",\"customerId\":" + customerId + ",\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(400);
    }

    // Test 23: HTML/script in email should be rejected (invalid email format)
    @Test
    @Order(23)
    void input_htmlInEmail() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"<script>@evil.com\",\"password\":\"test123456\",\"firstName\":\"Hack\",\"lastName\":\"Er\",\"phone\":\"+31600000000\"}")
            .when()
                .post("/api/user/auth/register")
            .then()
                .statusCode(400);
    }

    // Test 24: Invalid color format in branding should be silently ignored
    @Test
    @Order(24)
    void input_invalidColorFormat() {
        String adminToken = getAdminToken();

        // Create a customer to test branding
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"companyName\":\"Branding Test BV\",\"contactPerson\":\"Brand Tester\",\"email\":\"brandingtest@example.com\",\"phone\":\"+31600000070\",\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201);

        // Get the invite token and set password
        String inviteToken = getInviteToken("brandingtest@example.com");

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + inviteToken + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200);

        // Login as customer
        String customerToken = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"brandingtest@example.com\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");

        // First set a valid color
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"primaryColor\":\"#FF5733\"}")
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200);

        // Now try to set an invalid color
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"primaryColor\":\"not-a-color\"}")
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200);

        // Verify the color did NOT change - it should still be #FF5733
        Long customerId = ((Number) given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers/" + customerId)
            .then()
                .statusCode(200)
                .body("primaryColor", equalTo("#FF5733"));
    }

    @Transactional
    String getInviteToken(String email) {
        Customer customer = em.createQuery(
                "SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                .setParameter("email", email)
                .getSingleResult();
        return customer.inviteToken;
    }

    // Test 25: Special chars in order number path should return 404 (not 500)
    @Test
    @Order(25)
    void input_specialCharsInOrderNumber() {
        given()
            .when()
                .get("/api/orders/number/<script>")
            .then()
                .statusCode(404);
    }

    // Test 26: Special chars in slug path should return 404 (not 500)
    @Test
    @Order(26)
    void input_specialCharsInSlug() {
        given()
            .when()
                .get("/api/customers/slug/<script>alert(1)</script>")
            .then()
                .statusCode(404);
    }

    // Test 27: Mass assignment of status field in order creation should be ignored
    @Test
    @Order(27)
    void input_massAssignOrderStatus() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        // Include extra "status":"CONFIRMED" field in the body - it should be ignored
        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ",\"buyerFirstName\":\"Mass\",\"buyerLastName\":\"Assign\",\"buyerEmail\":\"massassign@test.nl\",\"quantity\":1,\"status\":\"CONFIRMED\"}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"));
    }

    // Test 28: Error responses should not contain stack traces
    @Test
    @Order(28)
    void input_noStackTrace() {
        // Trigger an error (non-existent order)
        String body = given()
            .when()
                .get("/api/orders/999999")
            .then()
                .statusCode(404)
                .extract()
                .body()
                .asString();

        // Verify no Java class names or stack trace keywords
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("at nl.ticketservice"),
                "Response should not contain stack trace (at nl.ticketservice)");
        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains(".java:"),
                "Response should not contain Java file references (.java:)");
    }

    // Test 29: Customer list should not expose passwordHash
    @Test
    @Order(29)
    void input_noPasswordInResponse() {
        String adminToken = getAdminToken();

        String body = given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .body()
                .asString();

        org.junit.jupiter.api.Assertions.assertFalse(
                body.contains("passwordHash"),
                "Response should not contain passwordHash field");
    }

    // Test 30: Responses should not leak server technology via headers
    @Test
    @Order(30)
    void input_noServerHeader() {
        Response response = given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .response();

        // Verify no Server or X-Powered-By headers that leak the tech stack
        String serverHeader = response.getHeader("Server");
        String poweredByHeader = response.getHeader("X-Powered-By");

        if (serverHeader != null) {
            org.junit.jupiter.api.Assertions.assertFalse(
                    serverHeader.toLowerCase().contains("quarkus"),
                    "Server header should not reveal Quarkus");
            org.junit.jupiter.api.Assertions.assertFalse(
                    serverHeader.toLowerCase().contains("resteasy"),
                    "Server header should not reveal RESTEasy");
        }

        org.junit.jupiter.api.Assertions.assertNull(
                poweredByHeader,
                "X-Powered-By header should not be present");
    }
}
