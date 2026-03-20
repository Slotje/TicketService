package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.TicketOrder;
import nl.ticketservice.service.EmailRetryService;
import nl.ticketservice.service.EmailService;
import nl.ticketservice.service.PdfService;
import nl.ticketservice.service.ReservationCleanupService;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ScheduledServicesTest {

    @Inject
    ReservationCleanupService reservationCleanupService;

    @Inject
    EmailRetryService emailRetryService;

    @Inject
    PdfService pdfService;

    @Inject
    EmailService emailService;

    @Inject
    EntityManager em;

    static Long eventId;
    static Long orderId;
    static Long confirmedOrderId;
    static Long noPrimaryColorCustomerEventId;

    // =========================================================================
    // Helper methods
    // =========================================================================

    private String getAdminToken() {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "admin@ticketservice.nl", "password", "admin"))
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
                .extract()
                .path("[0].id")).longValue();
    }

    // =========================================================================
    // ReservationCleanupService tests
    // =========================================================================

    @Test
    @Order(1)
    void testCreateOrderForCleanup() {
        eventId = getPublishedEventId();
        assertNotNull(eventId);

        orderId = ((Number) given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", eventId,
                        "buyerName", "Cleanup Test",
                        "buyerEmail", "cleanup@test.nl",
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        assertNotNull(orderId);
    }

    @Test
    @Order(2)
    void testCleanupNoExpiredOrders() {
        // Should run without error when there are no expired orders
        reservationCleanupService.cleanupExpiredReservations();
    }

    @Test
    @Order(3)
    @Transactional
    void testExpireOrder() {
        TicketOrder order = TicketOrder.findById(orderId);
        assertNotNull(order);
        order.expiresAt = LocalDateTime.now().minusMinutes(30);
        order.persist();
    }

    @Test
    @Order(4)
    void testCleanupExpiredReservation() {
        reservationCleanupService.cleanupExpiredReservations();

        // Verify the order is now EXPIRED
        given()
            .when()
                .get("/api/orders/" + orderId)
            .then()
                .statusCode(200)
                .body("status", equalTo("EXPIRED"));
    }

    // =========================================================================
    // EmailRetryService tests
    // =========================================================================

    @Test
    @Order(5)
    void testCreateAndConfirmOrderForEmailRetry() {
        confirmedOrderId = ((Number) given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", eventId,
                        "buyerName", "Email Test",
                        "buyerEmail", "email@test.nl",
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    @Test
    @Order(6)
    void testEmailRetryNoPendingEmails() {
        // All confirmed orders have emailSent=true, so this should return early
        emailRetryService.retryFailedEmails();
    }

    @Test
    @Order(7)
    @Transactional
    void testSetEmailSentFalse() {
        TicketOrder order = TicketOrder.findById(confirmedOrderId);
        assertNotNull(order);
        order.emailSent = false;
        order.emailRetryCount = 0;
        order.persist();
    }

    @Test
    @Order(8)
    void testEmailRetryWithPendingEmail() {
        emailRetryService.retryFailedEmails();

        // After retry, the order should have emailSent=true (mailer is mocked)
        given()
            .when()
                .get("/api/orders/" + confirmedOrderId)
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));
    }

    // =========================================================================
    // PdfService tests
    // =========================================================================

    @Test
    @Order(10)
    @Transactional
    void testGenerateOrderPdf() {
        TicketOrder order = TicketOrder.findById(confirmedOrderId);
        assertNotNull(order);
        Hibernate.initialize(order.event);
        Hibernate.initialize(order.event.customer);
        Hibernate.initialize(order.tickets);

        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(11)
    void testGenerateOrderPdfViaRest() {
        byte[] pdf = given()
            .when()
                .get("/api/orders/" + confirmedOrderId + "/pdf")
            .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(12)
    void testSetupCustomerWithNoPrimaryColor() {
        String adminToken = getAdminToken();

        // Create a customer with no primaryColor set
        Long customerId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "companyName", "No Color Corp",
                        "contactPerson", "Kleurloos Persoon",
                        "email", "nocolor@test.nl",
                        "active", true))
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Create an event with no address and serviceFee = 0 for this customer
        String eventBody = "{" +
                "\"name\":\"No Color Event\"," +
                "\"description\":\"Event for testing default color and no address\"," +
                "\"eventDate\":\"2028-06-01T18:00:00\"," +
                "\"location\":\"Somewhere\"," +
                "\"maxTickets\":100," +
                "\"ticketPrice\":10.00," +
                "\"serviceFee\":0.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        noPrimaryColorCustomerEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(eventBody)
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
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + noPrimaryColorCustomerEventId + "/status")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(13)
    void testPdfWithNoPrimaryColorAndNoAddressAndZeroServiceFee() {
        // Create and confirm an order for the no-color event
        Long noColorOrderId = ((Number) given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", noPrimaryColorCustomerEventId,
                        "buyerName", "Default Color Buyer",
                        "buyerEmail", "defaultcolor@test.nl",
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + noColorOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Generate the PDF - this covers default color path, no address branch, and serviceFee=0 branch
        byte[] pdf = given()
            .when()
                .get("/api/orders/" + noColorOrderId + "/pdf")
            .then()
                .statusCode(200)
                .extract()
                .asByteArray();

        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    // =========================================================================
    // EmailService tests
    // =========================================================================

    @Test
    @Order(20)
    @Transactional
    void testSendOrderConfirmation() {
        TicketOrder order = TicketOrder.findById(confirmedOrderId);
        assertNotNull(order);
        Hibernate.initialize(order.event);
        Hibernate.initialize(order.event.customer);
        Hibernate.initialize(order.tickets);

        boolean result = emailService.sendOrderConfirmation(order);
        assertTrue(result);
    }

    @Test
    @Order(21)
    @Transactional
    void testSendCustomerInvite() {
        Customer customer = Customer.findByEmail("info@festivalevents.nl");
        assertNotNull(customer);

        boolean result = emailService.sendCustomerInvite(customer, "test-invite-token-123");
        assertTrue(result);
    }

    @Test
    @Order(22)
    @Transactional
    void testBuildCustomerFromWithEmptyCompanyName() {
        // Create a customer with an empty-ish company name to test the fallback path
        // The buildCustomerFrom method strips non-alphanumeric chars; if the result is empty, it uses fallbackFrom
        Customer customer = new Customer();
        customer.companyName = "---";
        customer.contactPerson = "Test Persoon";
        customer.email = "emptyname@test.nl";
        customer.active = true;
        customer.persist();

        boolean result = emailService.sendCustomerInvite(customer, "test-fallback-token");
        assertTrue(result);
    }
}
