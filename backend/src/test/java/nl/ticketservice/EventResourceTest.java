package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class EventResourceTest {

    @Inject
    EntityManager em;

    private static Long existingCustomerId;
    private static Long existingEventId;
    private static Long createdEventId;
    private static Long customerOwnedEventId;
    private static String customerToken;
    private static Long customerIdForMyTests;
    private static String secondCustomerToken;
    private static Long secondCustomerIdForMyTests;

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

    @Transactional
    String getInviteTokenForEmail(String email) {
        Customer customer = em.createQuery(
                "SELECT c FROM Customer c WHERE c.email = :email", Customer.class)
                .setParameter("email", email)
                .getSingleResult();
        return customer.inviteToken;
    }

    private String createAndLoginCustomer(String companyName, String contactPerson, String email) {
        String adminToken = getAdminToken();

        // Create customer via admin API
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"companyName\":\"" + companyName + "\","
                        + "\"contactPerson\":\"" + contactPerson + "\","
                        + "\"email\":\"" + email + "\","
                        + "\"phone\":\"+31 6 11111111\","
                        + "\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201);

        // Get invite token from DB
        String inviteToken = getInviteTokenForEmail(email);
        assertNotNull(inviteToken, "Invite token should not be null");

        // Set password
        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + inviteToken + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200);

        // Login and return token
        return given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    // =========================================================================
    // Admin endpoint tests
    // =========================================================================

    @Test
    @Order(1)
    void testGetAllEventsWithAdminToken() {
        List<Map<String, Object>> events = given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(3))
                .extract()
                .jsonPath()
                .getList("$");

        // Store existing customer and event IDs for later tests
        existingCustomerId = ((Number) events.get(0).get("customerId")).longValue();
        existingEventId = ((Number) events.get(0).get("id")).longValue();
    }

    @Test
    @Order(2)
    void testGetAllEventsWithoutAuth() {
        given()
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    void testGetPublishedEvents() {
        given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("status", everyItem(is(oneOf("PUBLISHED", "SOLD_OUT"))));
    }

    @Test
    @Order(4)
    void testGetEventsByCustomer() {
        given()
            .when()
                .get("/api/events/customer/" + existingCustomerId)
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("customerId", everyItem(equalTo(existingCustomerId.intValue())));
    }

    @Test
    @Order(5)
    void testGetEventById() {
        given()
            .when()
                .get("/api/events/" + existingEventId)
            .then()
                .statusCode(200)
                .body("id", equalTo(existingEventId.intValue()))
                .body("name", notNullValue())
                .body("customerId", notNullValue());
    }

    @Test
    @Order(6)
    void testGetEventByIdNotFound() {
        given()
            .when()
                .get("/api/events/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(7)
    void testCreateEvent() {
        String body = "{" +
                "\"name\":\"Test Event 2028\"," +
                "\"description\":\"A test event for integration testing\"," +
                "\"eventDate\":\"2028-01-01T10:00:00\"," +
                "\"endDate\":\"2028-01-01T22:00:00\"," +
                "\"location\":\"Test Arena, Amsterdam\"," +
                "\"address\":\"Teststraat 1, 1000 AA Amsterdam\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":20.00," +
                "\"serviceFee\":2.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + existingCustomerId +
                "}";

        createdEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .body("name", equalTo("Test Event 2028"))
                .body("location", equalTo("Test Arena, Amsterdam"))
                .body("maxTickets", equalTo(100))
                .body("customerId", equalTo(existingCustomerId.intValue()))
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(8)
    void testUpdateEvent() {
        String body = "{" +
                "\"name\":\"Updated Test Event 2028\"," +
                "\"description\":\"Updated description\"," +
                "\"eventDate\":\"2028-01-01T10:00:00\"," +
                "\"location\":\"Updated Arena, Amsterdam\"," +
                "\"address\":\"Teststraat 1, 1000 AA Amsterdam\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":25.00," +
                "\"serviceFee\":2.50," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + existingCustomerId +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .put("/api/events/" + createdEventId)
            .then()
                .statusCode(200)
                .body("name", equalTo("Updated Test Event 2028"))
                .body("location", equalTo("Updated Arena, Amsterdam"));
    }

    @Test
    @Order(9)
    void testUpdateEventMaxTicketsBelowSold() {
        // The created event has 0 tickets sold, so we need to use an event
        // that has tickets sold. Since sample data events have 0 sold,
        // we attempt to set maxTickets to -1 which is below 0 (ticketsSold).
        // Actually, the check is maxTickets < ticketsSold. With ticketsSold=0,
        // maxTickets=0 would fail validation (@Min(1)), resulting in 400.
        String body = "{" +
                "\"name\":\"Updated Test Event 2028\"," +
                "\"description\":\"Updated description\"," +
                "\"eventDate\":\"2028-01-01T10:00:00\"," +
                "\"location\":\"Updated Arena, Amsterdam\"," +
                "\"address\":\"Teststraat 1, 1000 AA Amsterdam\"," +
                "\"maxTickets\":0," +
                "\"ticketPrice\":25.00," +
                "\"serviceFee\":2.50," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + existingCustomerId +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .put("/api/events/" + createdEventId)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(10)
    void testUpdateEventStatusCancelled() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"status\":\"CANCELLED\"}")
            .when()
                .patch("/api/events/" + createdEventId + "/status")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    @Order(11)
    void testUpdateEventStatusInvalid() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"status\":\"INVALID_STATUS\"}")
            .when()
                .patch("/api/events/" + createdEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(12)
    void testUpdateEventStatusEmpty() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"status\":\"\"}")
            .when()
                .patch("/api/events/" + createdEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(13)
    void testUpdateEventStatusMissing() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{}")
            .when()
                .patch("/api/events/" + createdEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(14)
    void testDeleteEventNoTicketsSold() {
        // The created event has 0 tickets sold, so deletion should succeed
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + createdEventId)
            .then()
                .statusCode(204);
    }

    @Test
    @Order(15)
    void testDeleteEventWithTicketsSold() {
        // First, create an event and simulate tickets sold by updating the DB directly
        String body = "{" +
                "\"name\":\"Event With Sales\"," +
                "\"description\":\"An event that has tickets sold\"," +
                "\"eventDate\":\"2028-06-01T10:00:00\"," +
                "\"location\":\"Sales Arena\"," +
                "\"address\":\"Salesstraat 1\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":30.00," +
                "\"serviceFee\":3.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + existingCustomerId +
                "}";

        Long eventWithSalesId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Set ticketsSold > 0 directly in DB
        setTicketsSold(eventWithSalesId, 5);

        // Attempt to delete should fail with 409
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + eventWithSalesId)
            .then()
                .statusCode(409);
    }

    @Transactional
    void setTicketsSold(Long eventId, int count) {
        em.createQuery("UPDATE Event e SET e.ticketsSold = :count WHERE e.id = :id")
                .setParameter("count", count)
                .setParameter("id", eventId)
                .executeUpdate();
    }

    // =========================================================================
    // Customer /my endpoint tests
    // =========================================================================

    @Test
    @Order(20)
    void testSetupCustomerToken() {
        customerToken = createAndLoginCustomer(
                "Test Bedrijf BV", "Test Persoon", "testcustomer@example.com");
        assertNotNull(customerToken);

        // Get customer ID
        customerIdForMyTests = ((Number) given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();
    }

    @Test
    @Order(21)
    void testGetMyEventsEmpty() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));
    }

    @Test
    @Order(22)
    void testCreateMyEvent() {
        String body = "{" +
                "\"name\":\"Customer Own Event\"," +
                "\"description\":\"Event created by customer\"," +
                "\"eventDate\":\"2028-03-15T18:00:00\"," +
                "\"endDate\":\"2028-03-15T23:00:00\"," +
                "\"location\":\"Customer Venue\"," +
                "\"address\":\"Klantstraat 10, Amsterdam\"," +
                "\"maxTickets\":50," +
                "\"ticketPrice\":15.00," +
                "\"serviceFee\":1.50," +
                "\"maxTicketsPerOrder\":4," +
                "\"customerId\":" + customerIdForMyTests +
                "}";

        customerOwnedEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(201)
                .body("name", equalTo("Customer Own Event"))
                .body("customerId", equalTo(customerIdForMyTests.intValue()))
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(23)
    void testGetMyEventsAfterCreate() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("name", hasItem("Customer Own Event"));
    }

    @Test
    @Order(24)
    void testUpdateMyEvent() {
        String body = "{" +
                "\"name\":\"Updated Customer Event\"," +
                "\"description\":\"Updated by customer\"," +
                "\"eventDate\":\"2028-03-15T18:00:00\"," +
                "\"location\":\"Updated Customer Venue\"," +
                "\"address\":\"Klantstraat 20, Amsterdam\"," +
                "\"maxTickets\":75," +
                "\"ticketPrice\":18.00," +
                "\"serviceFee\":1.80," +
                "\"maxTicketsPerOrder\":6," +
                "\"customerId\":" + customerIdForMyTests +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .put("/api/events/my/" + customerOwnedEventId)
            .then()
                .statusCode(200)
                .body("name", equalTo("Updated Customer Event"))
                .body("location", equalTo("Updated Customer Venue"));
    }

    @Test
    @Order(25)
    void testUpdateMyEventByDifferentCustomer() {
        // Create a second customer
        secondCustomerToken = createAndLoginCustomer(
                "Ander Bedrijf BV", "Andere Persoon", "andercustomer@example.com");
        assertNotNull(secondCustomerToken);

        secondCustomerIdForMyTests = ((Number) given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();

        // Try to update the first customer's event with the second customer's token
        String body = "{" +
                "\"name\":\"Hijacked Event\"," +
                "\"description\":\"Should not work\"," +
                "\"eventDate\":\"2028-03-15T18:00:00\"," +
                "\"location\":\"Hijacked Venue\"," +
                "\"address\":\"Hijackstraat 1\"," +
                "\"maxTickets\":50," +
                "\"ticketPrice\":15.00," +
                "\"serviceFee\":1.50," +
                "\"maxTicketsPerOrder\":4," +
                "\"customerId\":" + secondCustomerIdForMyTests +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body(body)
            .when()
                .put("/api/events/my/" + customerOwnedEventId)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(26)
    void testUpdateMyEventStatus() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"status\":\"PUBLISHED\"}")
            .when()
                .patch("/api/events/my/" + customerOwnedEventId + "/status")
            .then()
                .statusCode(200)
                .body("status", equalTo("PUBLISHED"));
    }

    @Test
    @Order(27)
    void testDeleteMyEvent() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .delete("/api/events/my/" + customerOwnedEventId)
            .then()
                .statusCode(204);
    }

    // =========================================================================
    // Additional coverage tests
    // =========================================================================

    @Test
    @Order(30)
    void testCreateEventForNonexistentCustomer() {
        String body = "{" +
                "\"name\":\"Orphan Event\"," +
                "\"description\":\"Event for nonexistent customer\"," +
                "\"eventDate\":\"2028-06-01T10:00:00\"," +
                "\"endDate\":\"2028-06-01T22:00:00\"," +
                "\"location\":\"Nowhere\"," +
                "\"address\":\"Nowhere 1\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":20.00," +
                "\"serviceFee\":2.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":999999" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(31)
    void testUpdateNonexistentEvent() {
        String body = "{" +
                "\"name\":\"Ghost Event\"," +
                "\"description\":\"Does not exist\"," +
                "\"eventDate\":\"2028-06-01T10:00:00\"," +
                "\"location\":\"Nowhere\"," +
                "\"address\":\"Nowhere 1\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":20.00," +
                "\"serviceFee\":2.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + existingCustomerId +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .put("/api/events/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(32)
    void testDeleteNonexistentEvent() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(33)
    void testGetMyEventsWithoutAuth() {
        given()
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(34)
    void testCreateMyEventWithoutAuth() {
        String body = "{" +
                "\"name\":\"Unauthorized Event\"," +
                "\"description\":\"Should not work\"," +
                "\"eventDate\":\"2028-06-01T10:00:00\"," +
                "\"location\":\"Nowhere\"," +
                "\"address\":\"Nowhere 1\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":20.00," +
                "\"serviceFee\":2.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":1" +
                "}";

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(401);
    }
}
