package nl.ticketservice.service;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ServiceCoverageTest {

    @Inject
    ImageLoaderService imageLoaderService;

    @Inject
    PhysicalTicketPdfService physicalTicketPdfService;

    @Inject
    EntityManager em;

    private static String adminToken;
    private static Long publishedEventId;
    private static Long testEventId;
    private static Long testCategoryId;
    private static Long categoryOrderId;
    private static Long cancelTestOrderId;
    private static String cancelTestOrderQr;

    // =========================================================================
    // Helper methods
    // =========================================================================

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
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", "scanner", "password", "scanner123"))
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private Long getPublishedEventId() {
        if (publishedEventId == null) {
            publishedEventId = ((Number) given()
                .when()
                    .get("/api/events/published")
                .then()
                    .statusCode(200)
                    .body("$.size()", greaterThan(0))
                    .extract()
                    .path("[0].id")).longValue();
        }
        return publishedEventId;
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
    // 1. ImageLoaderService - direct injection tests
    // =========================================================================

    @Test
    @Order(1)
    void imageLoader_nullUrl_returnsNull() {
        byte[] result = imageLoaderService.loadImage(null);
        assertNull(result, "loadImage(null) should return null");
    }

    @Test
    @Order(2)
    void imageLoader_emptyUrl_returnsNull() {
        byte[] result = imageLoaderService.loadImage("");
        assertNull(result, "loadImage(\"\") should return null");
    }

    @Test
    @Order(3)
    void imageLoader_blankUrl_returnsNull() {
        byte[] result = imageLoaderService.loadImage("   ");
        assertNull(result, "loadImage(\"   \") should return null");
    }

    @Test
    @Order(4)
    void imageLoader_localFileThatDoesNotExist_returnsNull() {
        byte[] result = imageLoaderService.loadImage("/api/images/nonexistent-file-xyz.png");
        assertNull(result, "loadImage for nonexistent local file should return null");
    }

    @Test
    @Order(5)
    void imageLoader_pathTraversal_returnsNull() {
        byte[] result = imageLoaderService.loadImage("/api/images/../etc/passwd");
        assertNull(result, "loadImage with path traversal should return null");
    }

    @Test
    @Order(6)
    void imageLoader_filenameWithSlash_returnsNull() {
        byte[] result = imageLoaderService.loadImage("/api/images/file/sub");
        assertNull(result, "loadImage with slash in filename should return null");
    }

    @Test
    @Order(7)
    void imageLoader_invalidHttpUrl_returnsNull() {
        byte[] result = imageLoaderService.loadImage("http://invalid-url-that-does-not-exist.test/img.png");
        assertNull(result, "loadImage with unreachable HTTP URL should return null");
    }

    @Test
    @Order(8)
    void imageLoader_ftpUrl_returnsNull() {
        byte[] result = imageLoaderService.loadImage("ftp://something");
        assertNull(result, "loadImage with ftp:// URL should return null (not http/https, not /api/images/)");
    }

    @Test
    @Order(9)
    void imageLoader_randomString_returnsNull() {
        byte[] result = imageLoaderService.loadImage("random-string");
        assertNull(result, "loadImage with random string should return null");
    }

    @Test
    @Order(10)
    void imageLoader_backslashInFilename_returnsNull() {
        byte[] result = imageLoaderService.loadImage("/api/images/file\\sub");
        assertNull(result, "loadImage with backslash in filename should return null");
    }

    // =========================================================================
    // 2. PhysicalTicketPdfService - generate PDF with various color configs
    // =========================================================================

    @Test
    @Order(20)
    void physicalTicketPdf_nullColors_generatesSuccessfully() {
        Event event = createTestEvent(null, null);
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF should be generated");
        assertTrue(pdf.length > 0, "PDF should not be empty");
    }

    @Test
    @Order(21)
    void physicalTicketPdf_emptyColors_generatesSuccessfully() {
        Event event = createTestEvent("", "");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with empty colors should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(22)
    void physicalTicketPdf_lightPrimaryColor_useDarkContrast() {
        // Light color (#FFFFFF) -> contrastColor should return dark text
        Event event = createTestEvent("#FFFFFF", "#000000");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with light primary color should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(23)
    void physicalTicketPdf_darkPrimaryColor_useLightContrast() {
        // Dark color (#000000) -> contrastColor should return white text
        Event event = createTestEvent("#000000", "#FFFFFF");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with dark primary color should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(24)
    void physicalTicketPdf_invalidHexColor_usesDefault() {
        // Invalid hex color -> parseColor should fall back to default
        Event event = createTestEvent("#ZZZZZZ", "#ZZZZZZ");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with invalid colors should use defaults and generate");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(25)
    void physicalTicketPdf_colorWithoutHash_parsedCorrectly() {
        // Color without # prefix should also be parsed
        Event event = createTestEvent("FF5733", "1A2B3C");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with hex colors without # should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(26)
    void physicalTicketPdf_multipleTickets_generatesMultiplePages() {
        // 5 tickets should require 2 pages (4 per page)
        Event event = createTestEvent("#D4A853", "#0F172A");
        List<Ticket> tickets = createTestTickets(event, 5);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with multiple pages should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(27)
    void physicalTicketPdf_eventWithEndDate_includesTimeRange() {
        Event event = createTestEvent("#AABBCC", "#112233");
        event.endDate = event.eventDate.plusHours(4);
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with end date should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(28)
    void physicalTicketPdf_eventWithAddress_includesAddress() {
        Event event = createTestEvent("#336699", "#996633");
        event.address = "Teststraat 123, 1000AA Amsterdam";
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with address should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(29)
    void physicalTicketPdf_customerWithWebsite_includesWebsite() {
        Event event = createTestEvent("#445566", "#665544");
        event.customer.website = "https://www.test-events.nl";
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with customer website should be generated");
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(30)
    void physicalTicketPdf_shortHexColor_usesDefault() {
        // Too short hex color string -> substring will throw -> catch -> default
        Event event = createTestEvent("#AB", "#C");
        List<Ticket> tickets = createTestTickets(event, 1);

        byte[] pdf = physicalTicketPdfService.generatePhysicalTicketsPdf(event, tickets);
        assertNotNull(pdf, "PDF with too-short hex colors should use defaults and generate");
        assertTrue(pdf.length > 0);
    }

    @Transactional
    Event createTestEvent(String primaryColor, String secondaryColor) {
        Customer customer = new Customer();
        customer.companyName = "PDF Test Bedrijf";
        customer.contactPerson = "Test Contact";
        customer.email = "pdftest-" + System.nanoTime() + "@test.nl";
        customer.primaryColor = primaryColor;
        customer.secondaryColor = secondaryColor;
        customer.active = true;
        em.persist(customer);

        Event event = new Event();
        event.name = "PDF Test Event";
        event.description = "Test event for PDF generation";
        event.eventDate = LocalDateTime.of(2028, 6, 15, 20, 0);
        event.location = "Test Venue Amsterdam";
        event.maxTickets = 100;
        event.ticketPrice = new BigDecimal("25.00");
        event.serviceFee = BigDecimal.ZERO;
        event.status = EventStatus.PUBLISHED;
        event.customer = customer;
        em.persist(event);

        em.flush();
        return event;
    }

    @Transactional
    List<Ticket> createTestTickets(Event event, int count) {
        // Re-attach the event entity to the current persistence context
        Event managedEvent = em.find(Event.class, event.id);

        java.util.ArrayList<Ticket> tickets = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Ticket ticket = new Ticket();
            ticket.event = managedEvent;
            ticket.ticketType = TicketType.PHYSICAL;
            em.persist(ticket);
            tickets.add(ticket);
        }
        em.flush();
        return tickets;
    }

    // =========================================================================
    // 3. EventService branches - ticket sales, categories, status
    // =========================================================================

    @Test
    @Order(40)
    void ticketSales_forPublishedEvent_returnsCorrectData() {
        Long eventId = getPublishedEventId();

        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + eventId + "/sales")
            .then()
                .statusCode(200)
                .body("eventId", equalTo(eventId.intValue()))
                .body("eventName", notNullValue())
                .body("maxTickets", greaterThan(0))
                .body("totalRevenue", notNullValue())
                .body("ticketsScanned", greaterThanOrEqualTo(0))
                .body("ticketsNotScanned", greaterThanOrEqualTo(0));
    }

    @Test
    @Order(41)
    void ticketSales_forNonexistentEvent_returns404() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/999999/sales")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(42)
    void ticketSales_withoutAuth_returns401() {
        given()
            .when()
                .get("/api/events/" + getPublishedEventId() + "/sales")
            .then()
                .statusCode(401);
    }

    // --- Ticket Category CRUD ---

    @Test
    @Order(50)
    void createTestEventForCategories() {
        Long customerId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].customerId")).longValue();

        String eventBody = "{" +
                "\"name\":\"Category Test Event\"," +
                "\"description\":\"Event for testing ticket categories\"," +
                "\"eventDate\":\"2028-09-01T18:00:00\"," +
                "\"endDate\":\"2028-09-01T23:00:00\"," +
                "\"location\":\"Category Test Venue\"," +
                "\"address\":\"Categoriestraat 1, Amsterdam\"," +
                "\"maxTickets\":200," +
                "\"ticketPrice\":30.00," +
                "\"serviceFee\":3.00," +
                "\"maxTicketsPerOrder\":5," +
                "\"customerId\":" + customerId +
                "}";

        testEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(eventBody)
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Publish the event so we can create orders for it later
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "PUBLISHED"))
            .when()
                .patch("/api/events/" + testEventId + "/status")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(51)
    void createTicketCategory_success() {
        String categoryBody = "{" +
                "\"name\":\"VIP Ticket\"," +
                "\"description\":\"VIP area access\"," +
                "\"price\":50.00," +
                "\"serviceFee\":5.00," +
                "\"maxTickets\":50," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        testCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(categoryBody)
            .when()
                .post("/api/events/" + testEventId + "/categories")
            .then()
                .statusCode(200)
                .body("name", equalTo("VIP Ticket"))
                .body("price", equalTo(50.0f))
                .body("active", equalTo(true))
                .extract()
                .path("id")).longValue();
    }

    @Test
    @Order(52)
    void getTicketCategories_returnsCreatedCategory() {
        given()
            .when()
                .get("/api/events/" + testEventId + "/categories")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("name", hasItem("VIP Ticket"));
    }

    @Test
    @Order(53)
    void updateTicketCategory_success() {
        String updateBody = "{" +
                "\"name\":\"VIP Gold Ticket\"," +
                "\"description\":\"Gold VIP area access\"," +
                "\"price\":75.00," +
                "\"serviceFee\":7.50," +
                "\"maxTickets\":30," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(updateBody)
            .when()
                .put("/api/events/" + testEventId + "/categories/" + testCategoryId)
            .then()
                .statusCode(200)
                .body("name", equalTo("VIP Gold Ticket"))
                .body("price", equalTo(75.0f));
    }

    @Test
    @Order(54)
    void updateTicketCategory_nonexistentCategory_returns404() {
        String updateBody = "{" +
                "\"name\":\"Ghost Category\"," +
                "\"description\":\"Does not exist\"," +
                "\"price\":10.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(updateBody)
            .when()
                .put("/api/events/" + testEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(55)
    void deleteTicketCategory_nonexistent_returns404() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + testEventId + "/categories/999999")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(56)
    void deleteTicketCategory_noTicketsSold_success() {
        // Create a second category to delete
        String categoryBody = "{" +
                "\"name\":\"Disposable Category\"," +
                "\"description\":\"To be deleted\"," +
                "\"price\":10.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":2," +
                "\"active\":true" +
                "}";

        Long disposableCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(categoryBody)
            .when()
                .post("/api/events/" + testEventId + "/categories")
            .then()
                .statusCode(200)
                .extract()
                .path("id")).longValue();

        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + testEventId + "/categories/" + disposableCategoryId)
            .then()
                .statusCode(204);
    }

    @Test
    @Order(57)
    void createTicketCategory_forNonexistentEvent_returns404() {
        String categoryBody = "{" +
                "\"name\":\"Orphan Category\"," +
                "\"description\":\"Event does not exist\"," +
                "\"price\":10.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(categoryBody)
            .when()
                .post("/api/events/999999/categories")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(58)
    void updateEventStatus_invalidStatus_returns400() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(Map.of("status", "TOTALLY_INVALID_STATUS"))
            .when()
                .patch("/api/events/" + testEventId + "/status")
            .then()
                .statusCode(400);
    }

    // =========================================================================
    // 4. OrderService branches - category orders, scan, email lookup, cancel
    // =========================================================================

    @Test
    @Order(60)
    void createOrder_withTicketCategory() {
        // Create an order with the VIP Gold category
        String orderBody = "{" +
                "\"eventId\":" + testEventId + "," +
                "\"ticketCategoryId\":" + testCategoryId + "," +
                "\"buyerFirstName\":\"Category\"," +
                "\"buyerLastName\":\"Buyer\"," +
                "\"buyerEmail\":\"category-buyer@test.nl\"," +
                "\"buyerPhone\":\"+31612345001\"," +
                "\"quantity\":2" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .body(orderBody)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .body("quantity", equalTo(2))
                .body("ticketCategoryName", equalTo("VIP Gold Ticket"))
                .extract()
                .response();

        categoryOrderId = ((Number) response.path("id")).longValue();
    }

    @Test
    @Order(61)
    void createOrder_withNonexistentCategory_returns404() {
        String orderBody = "{" +
                "\"eventId\":" + testEventId + "," +
                "\"ticketCategoryId\":999999," +
                "\"buyerFirstName\":\"Ghost\"," +
                "\"buyerLastName\":\"Category\"," +
                "\"buyerEmail\":\"ghost-cat@test.nl\"," +
                "\"buyerPhone\":\"+31612345002\"," +
                "\"quantity\":1" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(orderBody)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(62)
    void confirmOrder_withCategory_updatesCounters() {
        setBuyerDetails(categoryOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + categoryOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"))
                .body("confirmedAt", notNullValue());
    }

    @Test
    @Order(63)
    void scanTicket_fromCategoryOrder_succeeds() {
        // Get the ticket QR code from the confirmed category order
        String qrCode = given()
            .when()
                .get("/api/orders/" + categoryOrderId)
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
                .body("scanned", equalTo(true))
                .body("scannedAt", notNullValue());
    }

    @Test
    @Order(64)
    void getOrdersByEmail_returnsOrders() {
        given()
            .when()
                .get("/api/orders/email/category-buyer@test.nl")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThanOrEqualTo(1))
                .body("buyerEmail", hasItem("category-buyer@test.nl"));
    }

    @Test
    @Order(65)
    void getOrdersByEmail_noResults_returnsEmptyList() {
        given()
            .when()
                .get("/api/orders/email/nobody-ever-ordered@nonexistent.test")
            .then()
                .statusCode(200)
                .body("$.size()", equalTo(0));
    }

    // --- Cancel order tests ---

    @Test
    @Order(70)
    void cancelOrder_reservedOrder_succeeds() {
        // Create a new order to cancel
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + testEventId + "," +
                        "\"buyerFirstName\":\"Cancel\"," +
                        "\"buyerLastName\":\"Test\"," +
                        "\"buyerEmail\":\"cancel@test.nl\"," +
                        "\"buyerPhone\":\"+31600000099\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        cancelTestOrderId = ((Number) response.path("id")).longValue();
        cancelTestOrderQr = response.path("tickets[0].qrCodeData");

        // Cancel the reserved order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelTestOrderId + "/cancel")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    @Test
    @Order(71)
    void cancelOrder_alreadyCancelled_returns400() {
        // Try to cancel the already cancelled order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelTestOrderId + "/cancel")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(72)
    void cancelOrder_nonexistentOrder_returns404() {
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/999999/cancel")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(73)
    void cancelOrder_confirmedOrder_returns400() {
        // Create and confirm an order, then try to cancel
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + testEventId + "," +
                        "\"buyerFirstName\":\"Confirmed\"," +
                        "\"buyerLastName\":\"NoCancelTest\"," +
                        "\"buyerEmail\":\"nocancel@test.nl\"," +
                        "\"buyerPhone\":\"+31600000098\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long confirmedOrderId = ((Number) response.path("id")).longValue();
        setBuyerDetails(confirmedOrderId);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Now try to cancel the confirmed order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + confirmedOrderId + "/cancel")
            .then()
                .statusCode(400);
    }

    // --- Scan ticket edge cases ---

    @Test
    @Order(80)
    void scanTicket_cancelledOrderTicket_returns400() {
        // The ticket from the cancelled order should not be scannable
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getScannerToken())
            .when()
                .post("/api/orders/scan/" + cancelTestOrderQr)
            .then()
                .statusCode(400);
    }

    // --- Update buyer details edge cases ---

    @Test
    @Order(85)
    void updateBuyerDetails_nonexistentOrder_returns404() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Teststraat",
                        "buyerHouseNumber", "1",
                        "buyerPostalCode", "1234AB",
                        "buyerCity", "Amsterdam"))
            .when()
                .put("/api/orders/999999/details")
            .then()
                .statusCode(404);
    }

    @Test
    @Order(86)
    void confirmOrder_withoutBuyerDetails_returns400() {
        // Create a new order without setting buyer details, then try to confirm
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + testEventId + "," +
                        "\"buyerFirstName\":\"NoAddress\"," +
                        "\"buyerLastName\":\"Test\"," +
                        "\"buyerEmail\":\"noaddress@test.nl\"," +
                        "\"buyerPhone\":\"+31600000097\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long noAddressOrderId = ((Number) response.path("id")).longValue();

        // Try to confirm without address details
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + noAddressOrderId + "/confirm")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(87)
    void updateBuyerDetails_expiredOrder_returns400() {
        // Create an order, expire it, then try to update details
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{" +
                        "\"eventId\":" + testEventId + "," +
                        "\"buyerFirstName\":\"Expired\"," +
                        "\"buyerLastName\":\"Details\"," +
                        "\"buyerEmail\":\"expireddetails@test.nl\"," +
                        "\"buyerPhone\":\"+31600000096\"," +
                        "\"quantity\":1" +
                        "}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long expiredDetailsOrderId = ((Number) response.path("id")).longValue();

        // Expire the order
        expireOrderInDb(expiredDetailsOrderId);

        // Try to update buyer details on expired order
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "buyerStreet", "Teststraat",
                        "buyerHouseNumber", "1",
                        "buyerPostalCode", "1234AB",
                        "buyerCity", "Amsterdam"))
            .when()
                .put("/api/orders/" + expiredDetailsOrderId + "/details")
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

    // --- Cancel order with category ---

    @Test
    @Order(90)
    void cancelOrder_withCategory_updatesCounters() {
        // Create a category order and then cancel it
        String orderBody = "{" +
                "\"eventId\":" + testEventId + "," +
                "\"ticketCategoryId\":" + testCategoryId + "," +
                "\"buyerFirstName\":\"CatCancel\"," +
                "\"buyerLastName\":\"Test\"," +
                "\"buyerEmail\":\"catcancel@test.nl\"," +
                "\"buyerPhone\":\"+31600000095\"," +
                "\"quantity\":1" +
                "}";

        Response response = given()
                .contentType(ContentType.JSON)
                .body(orderBody)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .body("status", equalTo("RESERVED"))
                .extract()
                .response();

        Long catCancelOrderId = ((Number) response.path("id")).longValue();

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + catCancelOrderId + "/cancel")
            .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
    }

    // --- Ticket sales after orders ---

    @Test
    @Order(95)
    void ticketSales_afterOrders_showsUpdatedCounts() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/events/" + testEventId + "/sales")
            .then()
                .statusCode(200)
                .body("eventId", equalTo(testEventId.intValue()))
                .body("onlineSold", greaterThan(0))
                .body("ticketsScanned", greaterThanOrEqualTo(0));
    }

    // --- Create order with inactive category ---

    @Test
    @Order(96)
    void createOrder_withInactiveCategory_returns400() {
        // Create an inactive category
        String categoryBody = "{" +
                "\"name\":\"Inactive Category\"," +
                "\"description\":\"Not available\"," +
                "\"price\":20.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":3," +
                "\"active\":false" +
                "}";

        Long inactiveCategoryId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(categoryBody)
            .when()
                .post("/api/events/" + testEventId + "/categories")
            .then()
                .statusCode(200)
                .body("active", equalTo(false))
                .extract()
                .path("id")).longValue();

        // Try to create an order with the inactive category
        String orderBody = "{" +
                "\"eventId\":" + testEventId + "," +
                "\"ticketCategoryId\":" + inactiveCategoryId + "," +
                "\"buyerFirstName\":\"Inactive\"," +
                "\"buyerLastName\":\"CatTest\"," +
                "\"buyerEmail\":\"inactive-cat@test.nl\"," +
                "\"buyerPhone\":\"+31600000094\"," +
                "\"quantity\":1" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(orderBody)
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400);
    }

    // --- Delete category with tickets sold ---

    @Test
    @Order(97)
    void deleteTicketCategory_withTicketsSold_returns409() {
        // The VIP Gold category (testCategoryId) has had tickets sold through it
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + testEventId + "/categories/" + testCategoryId)
            .then()
                .statusCode(409);
    }

    // --- Update category belonging to wrong event ---

    @Test
    @Order(98)
    void updateTicketCategory_wrongEvent_returns404() {
        // Try to update testCategoryId as if it belongs to a different event
        Long otherEventId = getPublishedEventId();

        String updateBody = "{" +
                "\"name\":\"Wrong Event Category\"," +
                "\"description\":\"Should not work\"," +
                "\"price\":10.00," +
                "\"maxTickets\":10," +
                "\"sortOrder\":1," +
                "\"active\":true" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body(updateBody)
            .when()
                .put("/api/events/" + otherEventId + "/categories/" + testCategoryId)
            .then()
                .statusCode(404);
    }

    @Test
    @Order(99)
    void deleteTicketCategory_wrongEvent_returns404() {
        Long otherEventId = getPublishedEventId();

        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/events/" + otherEventId + "/categories/" + testCategoryId)
            .then()
                .statusCode(404);
    }
}
