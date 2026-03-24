package nl.ticketservice.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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

/**
 * Additional coverage tests for EventResource endpoints not covered by EventResourceTest.
 * Focuses on: physical tickets, sales, customer-specific CRUD, category management, and error paths.
 */
@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class EventResourceCoverageTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static String customerToken;
    private static Long customerId;
    private static String secondCustomerToken;
    private static Long secondCustomerId;

    // Events created during tests
    private static Long physicalEventId;         // event with physicalTickets > 0
    private static Long customerPhysicalEventId; // customer-owned event with physicalTickets > 0
    private static Long categoryEventId;         // event for category tests
    private static Long adminCategoryId;
    private static Long customerCategoryId;

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

    @Transactional
    void setPhysicalTicketsGenerated(Long eventId, boolean generated) {
        em.createQuery("UPDATE Event e SET e.physicalTicketsGenerated = :gen WHERE e.id = :id")
                .setParameter("gen", generated)
                .setParameter("id", eventId)
                .executeUpdate();
    }

    @Transactional
    void setPhysicalTicketsSold(Long eventId, int count) {
        em.createQuery("UPDATE Event e SET e.physicalTicketsSold = :count WHERE e.id = :id")
                .setParameter("count", count)
                .setParameter("id", eventId)
                .executeUpdate();
    }

    // =========================================================================
    // Setup: create customers and events
    // =========================================================================

    @Test
    @Order(1)
    void testSetupCustomers() {
        customerToken = createAndLoginCustomer(
                "Coverage BV", "Coverage Tester", "coverage@example.com");
        assertNotNull(customerToken);

        customerId = ((Number) given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract().path("customerId")).longValue();

        secondCustomerToken = createAndLoginCustomer(
                "Other BV", "Other Tester", "other-coverage@example.com");
        assertNotNull(secondCustomerToken);

        secondCustomerId = ((Number) given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract().path("customerId")).longValue();
    }

    @Test
    @Order(2)
    void testCreateEventWithPhysicalTickets_admin() {
        String body = "{" +
                "\"name\":\"Physical Ticket Event\"," +
                "\"description\":\"Event with physical tickets for testing\"," +
                "\"eventDate\":\"2028-06-15T14:00:00\"," +
                "\"endDate\":\"2028-06-15T22:00:00\"," +
                "\"location\":\"Stadium X\"," +
                "\"address\":\"Stadionweg 1, Amsterdam\"," +
                "\"maxTickets\":200," +
                "\"physicalTickets\":50," +
                "\"ticketPrice\":25.00," +
                "\"serviceFee\":2.50," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        physicalEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .body("physicalTickets", equalTo(50))
                .body("maxTickets", equalTo(200))
                .extract().path("id")).longValue();
    }

    // =========================================================================
    // Admin: Physical ticket endpoints
    // =========================================================================

    @Test
    @Order(10)
    void testGeneratePhysicalTickets_admin_returns200Pdf() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("fysieke-tickets-"));
    }

    @Test
    @Order(11)
    void testGeneratePhysicalTickets_alreadyGenerated_returns400() {
        // Physical tickets were already generated in Order(10)
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(12)
    void testDownloadPhysicalTicketsPdf_admin_returns200() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + physicalEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("fysieke-tickets-"));
    }

    @Test
    @Order(13)
    void testGeneratePhysicalTickets_noPhysicalTicketsConfigured_returns400() {
        // Create event with 0 physical tickets
        String body = "{" +
                "\"name\":\"No Physical Event\"," +
                "\"description\":\"No physical tickets\"," +
                "\"eventDate\":\"2028-07-01T10:00:00\"," +
                "\"location\":\"Venue Y\"," +
                "\"address\":\"Straat 1\"," +
                "\"maxTickets\":100," +
                "\"physicalTickets\":0," +
                "\"ticketPrice\":10.00," +
                "\"serviceFee\":1.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        Long noPhysicalEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract().path("id")).longValue();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + noPhysicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(14)
    void testGeneratePhysicalTickets_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/999999/physical-tickets/generate")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(15)
    void testGeneratePhysicalTickets_withoutAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(16)
    void testDownloadPhysicalTicketsPdf_notGenerated_returns400() {
        // Create a new event with physical tickets but don't generate them
        String body = "{" +
                "\"name\":\"Ungenerated Physical Event\"," +
                "\"description\":\"Physical tickets not yet generated\"," +
                "\"eventDate\":\"2028-08-01T10:00:00\"," +
                "\"location\":\"Venue Z\"," +
                "\"address\":\"Straat 2\"," +
                "\"maxTickets\":100," +
                "\"physicalTickets\":20," +
                "\"ticketPrice\":15.00," +
                "\"serviceFee\":1.50," +
                "\"maxTicketsPerOrder\":4," +
                "\"customerId\":" + customerId +
                "}";

        Long ungeneratedEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract().path("id")).longValue();

        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + ungeneratedEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Admin: Mark physical tickets sold
    // =========================================================================

    @Test
    @Order(20)
    void testMarkPhysicalTicketsSold_validQuantity_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"quantity\":5}")
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(200)
                .body("id", equalTo(physicalEventId.intValue()));
    }

    @Test
    @Order(21)
    void testMarkPhysicalTicketsSold_nullQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{}")
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(22)
    void testMarkPhysicalTicketsSold_zeroQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"quantity\":0}")
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(23)
    void testMarkPhysicalTicketsSold_negativeQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"quantity\":-1}")
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(24)
    void testMarkPhysicalTicketsSold_exceedsAvailable_returns400() {
        // physicalTickets=50, some already sold; try to sell way more than available
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"quantity\":9999}")
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(25)
    void testMarkPhysicalTicketsSold_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"quantity\":1}")
            .when()
                .post("/api/events/999999/physical-tickets/sell")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Admin: Adjust physical tickets sold count
    // =========================================================================

    @Test
    @Order(30)
    void testAdjustPhysicalTicketsSold_validCount_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"count\":10}")
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(200)
                .body("id", equalTo(physicalEventId.intValue()));
    }

    @Test
    @Order(31)
    void testAdjustPhysicalTicketsSold_zeroCount_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"count\":0}")
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(32)
    void testAdjustPhysicalTicketsSold_nullCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{}")
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(33)
    void testAdjustPhysicalTicketsSold_negativeCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"count\":-5}")
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(34)
    void testAdjustPhysicalTicketsSold_exceedsTotal_returns400() {
        // physicalTickets = 50, try to set sold to 999
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"count\":999}")
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(35)
    void testAdjustPhysicalTicketsSold_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"count\":1}")
            .when()
                .put("/api/events/999999/physical-tickets/sold-count")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Admin: Sales endpoint
    // =========================================================================

    @Test
    @Order(40)
    void testGetTicketSales_admin_returns200() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + physicalEventId + "/sales")
            .then()
                .statusCode(200)
                .body("eventId", equalTo(physicalEventId.intValue()))
                .body("eventName", equalTo("Physical Ticket Event"))
                .body("maxTickets", equalTo(200))
                .body("physicalTickets", equalTo(50))
                .body("ticketPrice", notNullValue())
                .body("totalRevenue", notNullValue());
    }

    @Test
    @Order(41)
    void testGetTicketSales_nonexistentEvent_returns404() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/999999/sales")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(42)
    void testGetTicketSales_withoutAuth_returns401() {
        given()
            .when()
                .get("/api/events/" + physicalEventId + "/sales")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // Customer: Create own event with physical tickets
    // =========================================================================

    @Test
    @Order(50)
    void testCreateMyEventWithPhysicalTickets() {
        String body = "{" +
                "\"name\":\"Customer Physical Event\"," +
                "\"description\":\"Customer event with physical tickets\"," +
                "\"eventDate\":\"2028-09-01T16:00:00\"," +
                "\"endDate\":\"2028-09-01T23:00:00\"," +
                "\"location\":\"Customer Arena\"," +
                "\"address\":\"Klantweg 5, Rotterdam\"," +
                "\"maxTickets\":100," +
                "\"physicalTickets\":30," +
                "\"ticketPrice\":20.00," +
                "\"serviceFee\":2.00," +
                "\"maxTicketsPerOrder\":4," +
                "\"customerId\":" + customerId +
                "}";

        customerPhysicalEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(201)
                .body("customerId", equalTo(customerId.intValue()))
                .body("physicalTickets", equalTo(30))
                .extract().path("id")).longValue();
    }

    @Test
    @Order(51)
    void testGetMyEvents_containsCreatedEvent() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("name", hasItem("Customer Physical Event"));
    }

    // =========================================================================
    // Customer: Physical ticket endpoints (my)
    // =========================================================================

    @Test
    @Order(52)
    void testGenerateMyPhysicalTickets_returns200Pdf() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("fysieke-tickets-"));
    }

    @Test
    @Order(53)
    void testGenerateMyPhysicalTickets_alreadyGenerated_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(54)
    void testDownloadMyPhysicalTicketsPdf_returns200() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", containsString("fysieke-tickets-"));
    }

    @Test
    @Order(55)
    void testGenerateMyPhysicalTickets_otherCustomer_returns403() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(56)
    void testDownloadMyPhysicalTicketsPdf_otherCustomer_returns403() {
        given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .get("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(57)
    void testGenerateMyPhysicalTickets_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .post("/api/events/my/999999/physical-tickets/generate")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Customer: Mark my physical tickets sold
    // =========================================================================

    @Test
    @Order(60)
    void testMarkMyPhysicalTicketsSold_validQuantity_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"quantity\":3}")
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(200)
                .body("id", equalTo(customerPhysicalEventId.intValue()));
    }

    @Test
    @Order(61)
    void testMarkMyPhysicalTicketsSold_nullQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{}")
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(62)
    void testMarkMyPhysicalTicketsSold_zeroQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"quantity\":0}")
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(63)
    void testMarkMyPhysicalTicketsSold_otherCustomer_returns403() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body("{\"quantity\":1}")
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(403);
    }

    // =========================================================================
    // Customer: Adjust my physical tickets sold count
    // =========================================================================

    @Test
    @Order(65)
    void testAdjustMyPhysicalTicketsSold_validCount_returns200() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"count\":5}")
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(200)
                .body("id", equalTo(customerPhysicalEventId.intValue()));
    }

    @Test
    @Order(66)
    void testAdjustMyPhysicalTicketsSold_nullCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{}")
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(67)
    void testAdjustMyPhysicalTicketsSold_negativeCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"count\":-1}")
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(68)
    void testAdjustMyPhysicalTicketsSold_otherCustomer_returns403() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body("{\"count\":1}")
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(403);
    }

    // =========================================================================
    // Customer: My sales endpoint
    // =========================================================================

    @Test
    @Order(70)
    void testGetMyTicketSales_returns200() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my/" + customerPhysicalEventId + "/sales")
            .then()
                .statusCode(200)
                .body("eventId", equalTo(customerPhysicalEventId.intValue()))
                .body("eventName", equalTo("Customer Physical Event"))
                .body("maxTickets", equalTo(100))
                .body("physicalTickets", equalTo(30));
    }

    @Test
    @Order(71)
    void testGetMyTicketSales_otherCustomer_returns403() {
        given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .get("/api/events/my/" + customerPhysicalEventId + "/sales")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(72)
    void testGetMyTicketSales_nonexistentEvent_returns404() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my/999999/sales")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Customer: Update/Delete my event status - error paths
    // =========================================================================

    @Test
    @Order(75)
    void testUpdateMyEventStatus_nullStatus_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{}")
            .when()
                .patch("/api/events/my/" + customerPhysicalEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(76)
    void testUpdateMyEventStatus_emptyStatus_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"status\":\"\"}")
            .when()
                .patch("/api/events/my/" + customerPhysicalEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(77)
    void testUpdateMyEventStatus_otherCustomer_returns403() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body("{\"status\":\"PUBLISHED\"}")
            .when()
                .patch("/api/events/my/" + customerPhysicalEventId + "/status")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(78)
    void testDeleteMyEvent_otherCustomer_returns403() {
        given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .delete("/api/events/my/" + customerPhysicalEventId)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(79)
    void testUpdateMyEvent_nonexistentEvent_returns404() {
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
                "\"customerId\":" + customerId +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .put("/api/events/my/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(80)
    void testDeleteMyEvent_nonexistentEvent_returns404() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .delete("/api/events/my/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(81)
    void testUpdateMyEventStatus_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"status\":\"PUBLISHED\"}")
            .when()
                .patch("/api/events/my/999999/status")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Categories: public GET
    // =========================================================================

    @Test
    @Order(90)
    void testSetupCategoryEvent() {
        String body = "{" +
                "\"name\":\"Category Test Event\"," +
                "\"description\":\"Event for category tests\"," +
                "\"eventDate\":\"2028-10-01T10:00:00\"," +
                "\"location\":\"Category Venue\"," +
                "\"address\":\"Categoriestraat 1\"," +
                "\"maxTickets\":200," +
                "\"ticketPrice\":30.00," +
                "\"serviceFee\":3.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        categoryEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract().path("id")).longValue();
    }

    @Test
    @Order(91)
    void testGetCategories_empty_returns200() {
        given()
            .when()
                .get("/api/events/" + categoryEventId + "/categories")
            .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));
    }

    // =========================================================================
    // Categories: Admin create/update/delete
    // =========================================================================

    @Test
    @Order(92)
    void testAdminCreateCategory_returns200() {
        String body = "{" +
                "\"name\":\"VIP\"," +
                "\"description\":\"VIP seating\"," +
                "\"price\":50.00," +
                "\"serviceFee\":5.00," +
                "\"maxTickets\":50," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        adminCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events/" + categoryEventId + "/categories")
            .then()
                .statusCode(200)
                .body("name", equalTo("VIP"))
                .body("price", equalTo(50.0f))
                .body("active", equalTo(true))
                .extract().path("id")).longValue();
    }

    @Test
    @Order(93)
    void testGetCategories_afterCreate_returns1() {
        given()
            .when()
                .get("/api/events/" + categoryEventId + "/categories")
            .then()
                .statusCode(200)
                .body("$.size()", equalTo(1))
                .body("name", hasItem("VIP"));
    }

    @Test
    @Order(94)
    void testAdminUpdateCategory_returns200() {
        String body = "{" +
                "\"name\":\"VIP Updated\"," +
                "\"description\":\"Updated VIP seating\"," +
                "\"price\":60.00," +
                "\"serviceFee\":6.00," +
                "\"maxTickets\":40," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .put("/api/events/" + categoryEventId + "/categories/" + adminCategoryId)
            .then()
                .statusCode(200)
                .body("name", equalTo("VIP Updated"))
                .body("price", equalTo(60.0f));
    }

    @Test
    @Order(95)
    void testAdminUpdateCategory_nonexistent_returns404() {
        String body = "{" +
                "\"name\":\"Ghost\"," +
                "\"description\":\"Does not exist\"," +
                "\"price\":10.00," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .put("/api/events/" + categoryEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(96)
    void testAdminDeleteCategory_nonexistent_returns404() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + categoryEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(97)
    void testAdminDeleteCategory_returns204() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + categoryEventId + "/categories/" + adminCategoryId)
            .then()
                .statusCode(204);
    }

    @Test
    @Order(98)
    void testAdminCreateCategory_withoutAuth_returns401() {
        String body = "{" +
                "\"name\":\"Unauthorized\"," +
                "\"price\":10.00," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(body)
            .when()
                .post("/api/events/" + categoryEventId + "/categories")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // Categories: Customer create/update/delete (my)
    // =========================================================================

    @Test
    @Order(100)
    void testCustomerCreateMyCategory_returns200() {
        String body = "{" +
                "\"name\":\"Early Bird\"," +
                "\"description\":\"Early bird discount\"," +
                "\"price\":15.00," +
                "\"serviceFee\":1.50," +
                "\"maxTickets\":20," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        customerCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/categories")
            .then()
                .statusCode(200)
                .body("name", equalTo("Early Bird"))
                .body("active", equalTo(true))
                .extract().path("id")).longValue();
    }

    @Test
    @Order(101)
    void testCustomerUpdateMyCategory_returns200() {
        String body = "{" +
                "\"name\":\"Early Bird Updated\"," +
                "\"description\":\"Updated early bird\"," +
                "\"price\":18.00," +
                "\"serviceFee\":1.80," +
                "\"maxTickets\":25," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/categories/" + customerCategoryId)
            .then()
                .statusCode(200)
                .body("name", equalTo("Early Bird Updated"))
                .body("price", equalTo(18.0f));
    }

    @Test
    @Order(102)
    void testCustomerCreateMyCategory_otherCustomer_returns403() {
        String body = "{" +
                "\"name\":\"Hijack Category\"," +
                "\"price\":10.00," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body(body)
            .when()
                .post("/api/events/my/" + customerPhysicalEventId + "/categories")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(103)
    void testCustomerUpdateMyCategory_otherCustomer_returns403() {
        String body = "{" +
                "\"name\":\"Hijack Update\"," +
                "\"price\":10.00," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + secondCustomerToken)
                .body(body)
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/categories/" + customerCategoryId)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(104)
    void testCustomerDeleteMyCategory_otherCustomer_returns403() {
        given()
                .header("Authorization", "Bearer " + secondCustomerToken)
            .when()
                .delete("/api/events/my/" + customerPhysicalEventId + "/categories/" + customerCategoryId)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(105)
    void testCustomerUpdateMyCategory_nonexistent_returns404() {
        String body = "{" +
                "\"name\":\"Ghost\"," +
                "\"price\":10.00," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .put("/api/events/my/" + customerPhysicalEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(106)
    void testCustomerDeleteMyCategory_nonexistent_returns404() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .delete("/api/events/my/" + customerPhysicalEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(107)
    void testCustomerDeleteMyCategory_returns204() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .delete("/api/events/my/" + customerPhysicalEventId + "/categories/" + customerCategoryId)
            .then()
                .statusCode(204);
    }

    // =========================================================================
    // Physical tickets: customer ownership for download PDF
    // =========================================================================

    @Test
    @Order(110)
    void testDownloadMyPhysicalTicketsPdf_nonexistentEvent_returns404() {
        given()
                .header("Authorization", "Bearer " + customerToken)
            .when()
                .get("/api/events/my/999999/physical-tickets/pdf")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(111)
    void testMarkMyPhysicalTicketsSold_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"quantity\":1}")
            .when()
                .post("/api/events/my/999999/physical-tickets/sell")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(112)
    void testAdjustMyPhysicalTicketsSold_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body("{\"count\":1}")
            .when()
                .put("/api/events/my/999999/physical-tickets/sold-count")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // Edge case: physicalTickets exceeds maxTickets
    // =========================================================================

    @Test
    @Order(120)
    void testCreateEventPhysicalTicketsExceedsMax_returns400() {
        String body = "{" +
                "\"name\":\"Bad Physical Event\"," +
                "\"description\":\"physicalTickets > maxTickets\"," +
                "\"eventDate\":\"2028-11-01T10:00:00\"," +
                "\"location\":\"Venue\"," +
                "\"address\":\"Straat 1\"," +
                "\"maxTickets\":50," +
                "\"physicalTickets\":100," +
                "\"ticketPrice\":10.00," +
                "\"serviceFee\":1.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(body)
            .when()
                .post("/api/events")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // Customer forced ownership: createMyEvent forces customerId
    // =========================================================================

    @Test
    @Order(121)
    void testCreateMyEvent_forcesCustomerIdToLoggedInCustomer() {
        // Attempt to create event with a different customerId; should be overridden
        String body = "{" +
                "\"name\":\"Forced Owner Event\"," +
                "\"description\":\"customerId should be forced\"," +
                "\"eventDate\":\"2028-12-01T10:00:00\"," +
                "\"location\":\"Venue\"," +
                "\"address\":\"Straat 1\"," +
                "\"maxTickets\":50," +
                "\"ticketPrice\":10.00," +
                "\"serviceFee\":1.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":999999" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerToken)
                .body(body)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(201)
                .body("customerId", equalTo(customerId.intValue()));
    }
}
