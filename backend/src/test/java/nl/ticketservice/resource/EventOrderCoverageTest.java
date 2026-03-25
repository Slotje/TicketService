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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class EventOrderCoverageTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static String scannerToken;
    private static Long draftEventId;
    private static Long soldOutEventId;
    private static Long physicalEventId;
    private static Long scanTestEventId;
    private static Long scanTestOrderId;
    private static Long categoryWithLimitId;
    private static Long categoryTestEventId;

    private String getAdminToken() {
        if (adminToken == null) {
            adminToken = given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("email", "admin@ticketservice.nl", "password", "admin"))
                .when()
                    .post("/api/admin/auth/login")
                .then()
                    .statusCode(200)
                    .extract()
                    .path("token");
        }
        return adminToken;
    }

    private String getScannerToken() {
        if (scannerToken == null) {
            scannerToken = given()
                    .contentType(ContentType.JSON)
                    .body(Map.of("username", "scanner", "password", "scanner123"))
                .when()
                    .post("/api/auth/login")
                .then()
                    .statusCode(200)
                    .extract()
                    .path("token");
        }
        return scannerToken;
    }

    private Long getCustomerId() {
        return ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].customerId")).longValue();
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
    // EventService: DRAFT event - cannot create orders
    // =========================================================================

    @Test
    @Order(1)
    void setup_createDraftEvent() {
        Long customerId = getCustomerId();

        String eventBody = "{" +
                "\"name\":\"Draft Test Event\"," +
                "\"description\":\"Event in draft status\"," +
                "\"eventDate\":\"2028-09-01T18:00:00\"," +
                "\"location\":\"Draft Venue\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":20.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        draftEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(2)
    void createOrder_draftEvent_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + draftEventId + "," +
                        "\"buyerFirstName\":\"Draft\"," +
                        "\"buyerLastName\":\"Test\"," +
                        "\"buyerEmail\":\"draft@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // EventService: update status edge cases
    // =========================================================================

    @Test
    @Order(5)
    void updateEventStatus_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/999999/status")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(6)
    void updateEventStatus_blankStatus_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", ""))
            .when()
                .patch("/api/events/" + draftEventId + "/status")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(7)
    void getEvent_nonexistent_returns404() {
        given()
            .when()
                .get("/api/events/999999")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // EventService: delete event with tickets sold
    // =========================================================================

    @Test
    @Order(10)
    void deleteEvent_noTicketsSold_succeeds() {
        // The draft event has no tickets sold, so it can be deleted
        // But first test delete of an event that has sales - create one
        Long customerId = getCustomerId();

        String eventBody = "{" +
                "\"name\":\"Deletable Event\"," +
                "\"description\":\"Will be deleted\"," +
                "\"eventDate\":\"2028-10-01T18:00:00\"," +
                "\"location\":\"Delete Venue\"," +
                "\"maxTickets\":50," +
                "\"ticketPrice\":15.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        Long deletableEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + deletableEventId)
            .then()
                .statusCode(204);
    }

    @Test
    @Order(11)
    void deleteEvent_nonexistent_returns404() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/999999")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // EventService: sold out event
    // =========================================================================

    @Test
    @Order(15)
    void setup_createSoldOutEvent() {
        Long customerId = getCustomerId();

        String eventBody = "{" +
                "\"name\":\"Sold Out Event\"," +
                "\"description\":\"Tiny event to sell out\"," +
                "\"eventDate\":\"2028-08-01T18:00:00\"," +
                "\"location\":\"Small Venue\"," +
                "\"maxTickets\":1," +
                "\"ticketPrice\":10.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        soldOutEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Publish it
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + soldOutEventId + "/status")
            .then()
                .statusCode(200);

        // Reserve the only ticket
        given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + soldOutEventId + "," +
                        "\"buyerFirstName\":\"First\"," +
                        "\"buyerLastName\":\"Buyer\"," +
                        "\"buyerEmail\":\"first@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(16)
    void createOrder_soldOutEvent_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + soldOutEventId + "," +
                        "\"buyerFirstName\":\"Late\"," +
                        "\"buyerLastName\":\"Buyer\"," +
                        "\"buyerEmail\":\"late@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // EventService: updateEvent - maxTickets < ticketsSold
    // =========================================================================

    @Test
    @Order(20)
    void setup_createEventAndSellTickets() {
        Long customerId = getCustomerId();

        String eventBody = "{" +
                "\"name\":\"Update Max Test\"," +
                "\"description\":\"For testing maxTickets update\"," +
                "\"eventDate\":\"2028-07-01T18:00:00\"," +
                "\"location\":\"Update Venue\"," +
                "\"maxTickets\":10," +
                "\"ticketPrice\":10.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        scanTestEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Publish
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + scanTestEventId + "/status")
            .then()
                .statusCode(200);

        // Create and confirm an order
        scanTestOrderId = ((Number) given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + scanTestEventId + "," +
                        "\"buyerFirstName\":\"Scan\"," +
                        "\"buyerLastName\":\"Test\"," +
                        "\"buyerEmail\":\"scantest@test.nl\"," +
                        "\"quantity\":2" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        setBuyerDetails(scanTestOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + scanTestOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    @Test
    @Order(21)
    void updateEvent_maxTicketsLessThanSold_returns400() {
        // Event has 2 tickets sold, try to set maxTickets to 1
        String eventBody = "{" +
                "\"name\":\"Update Max Test\"," +
                "\"description\":\"Updated\"," +
                "\"eventDate\":\"2028-07-01T18:00:00\"," +
                "\"location\":\"Update Venue\"," +
                "\"maxTickets\":1," +
                "\"ticketPrice\":10.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + getCustomerId() +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .put("/api/events/" + scanTestEventId)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(22)
    void updateEvent_physicalTicketsExceedsMax_returns400() {
        String eventBody = "{" +
                "\"name\":\"Update Max Test\"," +
                "\"description\":\"Updated\"," +
                "\"eventDate\":\"2028-07-01T18:00:00\"," +
                "\"location\":\"Update Venue\"," +
                "\"maxTickets\":10," +
                "\"physicalTickets\":20," +
                "\"ticketPrice\":10.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + getCustomerId() +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .put("/api/events/" + scanTestEventId)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(23)
    void deleteEvent_withTicketsSold_returns409() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + scanTestEventId)
            .then()
                .statusCode(409);
    }

    // =========================================================================
    // OrderService: scan ticket edge cases
    // =========================================================================

    @Test
    @Order(30)
    void scanTicket_success() {
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[0].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(200)
                .body("scanned", equalTo(true));
    }

    @Test
    @Order(31)
    void scanTicket_alreadyScanned_returns400() {
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[0].qrCodeData");

        // First scan — may already be scanned by prior test, both 200 and 400 are OK
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(anyOf(is(200), is(400)));

        // Second scan must be 400 (already scanned)
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(32)
    void scanTicket_wrongEventId_returns400() {
        // Use the second ticket (not yet scanned)
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[1].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
                .queryParam("eventId", 999999)
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(33)
    void scanTicket_nonexistentTicket_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/nonexistent-qr-data-xyz")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(34)
    void scanTicket_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/scan/some-qr-data")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(35)
    void scanTicket_invalidAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer invalidtoken")
            .when()
                .post("/api/orders/scan/some-qr-data")
            .then()
                .statusCode(401);
    }

    // =========================================================================
    // OrderService: confirm order edge cases
    // =========================================================================

    @Test
    @Order(40)
    void confirmOrder_nonexistent_returns404() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/999999/confirm")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(41)
    void confirmOrder_alreadyConfirmed_returns400() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + scanTestOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(42)
    void confirmOrder_expiredReservation_returns400() {
        // Create a new order and expire it
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + scanTestEventId + "," +
                        "\"buyerFirstName\":\"Expired\"," +
                        "\"buyerLastName\":\"Confirm\"," +
                        "\"buyerEmail\":\"expired-confirm@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long expiredOrderId = ((Number) response.path("id")).longValue();

        setBuyerDetails(expiredOrderId);
        expireOrderInDb(expiredOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + expiredOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    @Transactional
    void expireOrderInDb(Long orderId) {
        em.createQuery("UPDATE TicketOrder o SET o.expiresAt = :past WHERE o.id = :id")
                .setParameter("past", LocalDateTime.now().minusHours(1))
                .setParameter("id", orderId)
                .executeUpdate();
    }

    // =========================================================================
    // OrderService: buyer details edge cases
    // =========================================================================

    @Test
    @Order(45)
    void updateBuyerDetails_confirmedOrder_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "NewStreet",
                        "buyerHouseNumber", "99",
                        "buyerPostalCode", "5678CD",
                        "buyerCity", "Rotterdam"))
            .when()
                .put("/api/orders/" + scanTestOrderId + "/details")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // OrderService: getOrderByNumber
    // =========================================================================

    @Test
    @Order(50)
    void getOrderByNumber_nonexistent_returns404() {
        given()
            .when()
                .get("/api/orders/number/NONEXISTENT-ORDER-123")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(51)
    void getOrder_nonexistent_returns404() {
        given()
            .when()
                .get("/api/orders/999999")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // PhysicalTicketService: error paths
    // =========================================================================

    @Test
    @Order(60)
    void setup_createEventWithPhysicalTickets() {
        Long customerId = getCustomerId();

        String eventBody = "{" +
                "\"name\":\"Physical Ticket Event\"," +
                "\"description\":\"Event with physical tickets\"," +
                "\"eventDate\":\"2028-11-01T18:00:00\"," +
                "\"location\":\"Physical Venue\"," +
                "\"maxTickets\":100," +
                "\"physicalTickets\":10," +
                "\"ticketPrice\":20.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        physicalEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Publish
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + physicalEventId + "/status")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(61)
    void generatePhysicalTickets_nonexistentEvent_returns404() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/999999/physical-tickets/generate")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(62)
    void generatePhysicalTickets_noPhysicalTicketsConfigured_returns400() {
        // Draft event has 0 physical tickets
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + draftEventId + "/physical-tickets/generate")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(63)
    void getPhysicalTicketsPdf_notYetGenerated_returns400() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + physicalEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(64)
    void generatePhysicalTickets_success() {
        byte[] pdf = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(65)
    void generatePhysicalTickets_alreadyGenerated_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/generate")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(66)
    void getPhysicalTicketsPdf_afterGeneration_returnsPdf() {
        byte[] pdf = given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + physicalEventId + "/physical-tickets/pdf")
            .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        assertTrue(pdf.length > 0);
    }

    // =========================================================================
    // PhysicalTicketService: sell and adjust
    // =========================================================================

    @Test
    @Order(70)
    void markPhysicalTicketsSold_nullQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of())
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(71)
    void markPhysicalTicketsSold_zeroQuantity_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("quantity", 0))
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(72)
    void markPhysicalTicketsSold_exceedsAvailable_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("quantity", 999))
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(73)
    void markPhysicalTicketsSold_validQuantity_succeeds() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("quantity", 3))
            .when()
                .post("/api/events/" + physicalEventId + "/physical-tickets/sell")
            .then()
                .statusCode(200)
                .body("physicalTicketsSold", equalTo(3));
    }

    @Test
    @Order(74)
    void adjustPhysicalTicketsSold_nullCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of())
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(75)
    void adjustPhysicalTicketsSold_negativeCount_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("count", -1))
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(76)
    void adjustPhysicalTicketsSold_exceedsPhysicalTickets_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("count", 999))
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(77)
    void adjustPhysicalTicketsSold_validCount_succeeds() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("count", 5))
            .when()
                .put("/api/events/" + physicalEventId + "/physical-tickets/sold-count")
            .then()
                .statusCode(200)
                .body("physicalTicketsSold", equalTo(5));
    }

    // =========================================================================
    // OrderService: scan ticket with date validation
    // =========================================================================

    @Test
    @Order(80)
    @Transactional
    void setup_setTicketValidDateInFuture() {
        // Set the second ticket's validDate to far in the future
        TicketOrder order = TicketOrder.findById(scanTestOrderId);
        if (order != null && order.tickets.size() > 1) {
            Ticket ticket = order.tickets.get(1);
            ticket.validDate = LocalDate.now().plusMonths(6);
            ticket.validEndDate = null;
            em.merge(ticket);
            em.flush();
        }
    }

    @Test
    @Order(81)
    void scanTicket_validDateInFuture_returns400() {
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[1].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(82)
    @Transactional
    void setup_setTicketValidDateInPast() {
        TicketOrder order = TicketOrder.findById(scanTestOrderId);
        if (order != null && order.tickets.size() > 1) {
            Ticket ticket = order.tickets.get(1);
            ticket.validDate = LocalDate.now().minusMonths(3);
            ticket.validEndDate = LocalDate.now().minusMonths(2);
            em.merge(ticket);
            em.flush();
        }
    }

    @Test
    @Order(83)
    void scanTicket_validDateRangeInPast_returns400() {
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[1].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(400);
    }

    @Test
    @Order(84)
    @Transactional
    void setup_setTicketExpiredEvent() {
        // Set the event's endDate to the past to trigger "ticket is expired"
        Event event = Event.findById(scanTestEventId);
        if (event != null) {
            event.endDate = LocalDateTime.now().minusDays(1);
            em.merge(event);
        }
        // Reset ticket validDate so it doesn't hit that check
        TicketOrder order = TicketOrder.findById(scanTestOrderId);
        if (order != null && order.tickets.size() > 1) {
            Ticket ticket = order.tickets.get(1);
            ticket.validDate = null;
            ticket.validEndDate = null;
            em.merge(ticket);
        }
        em.flush();
    }

    @Test
    @Order(85)
    void scanTicket_expiredEventEndDate_returns400() {
        String qrCode = given()
            .when()
                .get("/api/orders/" + scanTestOrderId)
            .then()
                .statusCode(200)
                .extract()
                .path("tickets[1].qrCodeData");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + qrCode)
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // OrderService: category sold out
    // =========================================================================

    @Test
    @Order(90)
    void setup_createCategoryWithLimit() {
        // Create a category with maxTickets=1 on a published event
        categoryTestEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        String categoryBody = "{" +
                "\"name\":\"Limited Category\"," +
                "\"description\":\"Only 1 ticket\"," +
                "\"price\":50.00," +
                "\"maxTickets\":1," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        categoryWithLimitId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(categoryBody)
            .when()
                .post("/api/events/" + categoryTestEventId + "/categories")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        // Reserve the only ticket in this category
        given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + categoryTestEventId + "," +
                        "\"ticketCategoryId\":" + categoryWithLimitId + "," +
                        "\"buyerFirstName\":\"Cat\"," +
                        "\"buyerLastName\":\"Buyer\"," +
                        "\"buyerEmail\":\"catbuyer@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(91)
    void createOrder_categorySoldOut_returns400() {
        given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + categoryTestEventId + "," +
                        "\"ticketCategoryId\":" + categoryWithLimitId + "," +
                        "\"buyerFirstName\":\"Too\"," +
                        "\"buyerLastName\":\"Late\"," +
                        "\"buyerEmail\":\"toolate@test.nl\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // OrderResource: PDF download for nonexistent order
    // =========================================================================

    @Test
    @Order(95)
    void downloadPdf_nonexistentOrder_returns404() {
        given()
            .when()
                .get("/api/orders/999999/pdf")
            .then()
                .statusCode(404);
    }

    // =========================================================================
    // OrderResource: getByEvent without auth
    // =========================================================================

    @Test
    @Order(96)
    void getByEvent_noAuth_returns401() {
        given()
            .when()
                .get("/api/orders/event/" + scanTestEventId)
            .then()
                .statusCode(401);
    }

    @Test
    @Order(97)
    void getByEvent_withAuth_returns200() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/orders/event/" + scanTestEventId)
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0));
    }
}
