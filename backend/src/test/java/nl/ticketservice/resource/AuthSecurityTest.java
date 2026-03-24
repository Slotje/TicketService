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

import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class AuthSecurityTest {

    @Inject
    EntityManager em;

    private static String customerTokenA;
    private static String customerTokenB;
    private static Long eventIdA;
    private static Long eventIdB;
    private static Long customerIdA;
    private static Long customerIdB;

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
    // Auth Bypass Tests (Orders 1–12)
    // =========================================================================

    @Test
    @Order(1)
    void auth_noTokenOnAdminEndpoint() {
        given()
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(2)
    void auth_noTokenOnCustomerEndpoint() {
        given()
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    void auth_malformedBearer() {
        given()
                .header("Authorization", "Bearer garbage123xyz")
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(4)
    void auth_emptyBearer() {
        given()
                .header("Authorization", "Bearer ")
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(5)
    void auth_wrongRoleCustomerOnAdmin() {
        // Setup: create customer A and get token
        customerTokenA = createAndLoginCustomer(
                "Security Test A BV", "Contact A", "securitytest-a@example.com");
        assertNotNull(customerTokenA);

        customerIdA = ((Number) given()
                .header("Authorization", "Bearer " + customerTokenA)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();

        // Customer token should NOT have access to admin endpoint
        given()
                .header("Authorization", "Bearer " + customerTokenA)
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(6)
    void auth_wrongRoleUserOnCustomer() {
        // Register a regular user
        Response response = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"securityuser@example.com\","
                        + "\"password\":\"test123456\","
                        + "\"firstName\":\"Security\","
                        + "\"lastName\":\"User\","
                        + "\"phone\":\"+31600000099\"}")
            .when()
                .post("/api/user/auth/register")
            .then()
                .statusCode(200)
                .extract()
                .response();

        String userToken = response.path("token");
        assertNotNull(userToken);

        // User token should NOT have access to customer endpoint
        given()
                .header("Authorization", "Bearer " + userToken)
            .when()
                .get("/api/events/my")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(7)
    void auth_scannerTokenOnAdmin() {
        String scannerToken = getScannerToken();

        // Scanner token should NOT have access to admin endpoint
        given()
                .header("Authorization", "Bearer " + scannerToken)
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(8)
    void auth_expiredToken() {
        // Create a Base64-encoded token with an expired timestamp
        // Token format: Base64(userId|expiryTimestamp|hmac)
        String fakePayload = "1|1000000000000|fakehmacsignature";
        String expiredToken = Base64.getEncoder().encodeToString(fakePayload.getBytes());

        given()
                .header("Authorization", "Bearer " + expiredToken)
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(9)
    void auth_tamperedToken() {
        // Get a valid admin token
        String validToken = getAdminToken();

        // Tamper with the last 4 characters
        String tampered = validToken.substring(0, validToken.length() - 4) + "XXXX";

        given()
                .header("Authorization", "Bearer " + tampered)
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(10)
    void auth_sqlInToken() {
        given()
                .header("Authorization", "Bearer ' OR 1=1 --")
            .when()
                .get("/api/events")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(11)
    void auth_inactiveCustomerLogin() {
        // Create a new customer
        String adminToken = getAdminToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"companyName\":\"Inactive Customer BV\","
                        + "\"contactPerson\":\"Inactive Person\","
                        + "\"email\":\"inactive-customer@example.com\","
                        + "\"phone\":\"+31 6 22222222\","
                        + "\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201);

        // Set password
        String inviteToken = getInviteTokenForEmail("inactive-customer@example.com");
        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + inviteToken + "\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200);

        // Deactivate customer via EntityManager
        setCustomerActive("inactive-customer@example.com", false);

        // Try to login - should fail with 401
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"inactive-customer@example.com\",\"password\":\"test123\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(401);
    }

    @Transactional
    void setCustomerActive(String email, boolean active) {
        em.createQuery("UPDATE Customer c SET c.active = :active WHERE c.email = :email")
                .setParameter("active", active)
                .setParameter("email", email)
                .executeUpdate();
    }

    @Test
    @Order(12)
    void auth_inactiveScanner() {
        // Deactivate scanner via EntityManager
        setScannerActive("scanner", false);

        // Try to login - should fail with 401
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"scanner\",\"password\":\"scanner123\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(401);

        // Reactivate scanner so other tests are not affected
        setScannerActive("scanner", true);
    }

    @Transactional
    void setScannerActive(String username, boolean active) {
        em.createQuery("UPDATE ScannerUser s SET s.active = :active WHERE s.username = :username")
                .setParameter("active", active)
                .setParameter("username", username)
                .executeUpdate();
    }

    // =========================================================================
    // IDOR Tests (Orders 20–32)
    // =========================================================================

    @Test
    @Order(20)
    void idor_setupCustomers() {
        // Ensure customer A is set up (may already be from test 5)
        if (customerTokenA == null) {
            customerTokenA = createAndLoginCustomer(
                    "Security Test A BV", "Contact A", "securitytest-a@example.com");
            customerIdA = ((Number) given()
                    .header("Authorization", "Bearer " + customerTokenA)
                .when()
                    .get("/api/customer/auth/verify")
                .then()
                    .statusCode(200)
                    .extract()
                    .path("customerId")).longValue();
        }

        // Create customer B
        customerTokenB = createAndLoginCustomer(
                "Security Test B BV", "Contact B", "securitytest-b@example.com");
        assertNotNull(customerTokenB);

        customerIdB = ((Number) given()
                .header("Authorization", "Bearer " + customerTokenB)
            .when()
                .get("/api/customer/auth/verify")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();

        // Customer A creates event
        String eventBodyA = "{\"name\":\"Event A\",\"description\":\"test\","
                + "\"eventDate\":\"2027-06-15T14:00:00\",\"location\":\"Amsterdam\","
                + "\"maxTickets\":100,\"physicalTickets\":0,\"ticketPrice\":25.00,"
                + "\"serviceFee\":0,\"maxTicketsPerOrder\":10,"
                + "\"physicalTicketsGenerated\":false,\"showAvailability\":true,"
                + "\"status\":\"DRAFT\",\"ticketCategories\":null}";

        eventIdA = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerTokenA)
                .body(eventBodyA)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        // Customer B creates event
        String eventBodyB = "{\"name\":\"Event B\",\"description\":\"test\","
                + "\"eventDate\":\"2027-07-20T18:00:00\",\"location\":\"Rotterdam\","
                + "\"maxTickets\":200,\"physicalTickets\":0,\"ticketPrice\":30.00,"
                + "\"serviceFee\":0,\"maxTicketsPerOrder\":10,"
                + "\"physicalTicketsGenerated\":false,\"showAvailability\":true,"
                + "\"status\":\"DRAFT\",\"ticketCategories\":null}";

        eventIdB = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerTokenB)
                .body(eventBodyB)
            .when()
                .post("/api/events/my")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        assertNotNull(eventIdA);
        assertNotNull(eventIdB);
        assertNotEquals(eventIdA, eventIdB);
    }

    @Test
    @Order(21)
    void idor_customerAccessOtherEvent() {
        // Customer A tries to update Customer B's event
        String updateBody = "{\"name\":\"Hijacked Event\",\"description\":\"hijacked\","
                + "\"eventDate\":\"2027-07-20T18:00:00\",\"location\":\"Hijacked\","
                + "\"maxTickets\":200,\"physicalTickets\":0,\"ticketPrice\":30.00,"
                + "\"serviceFee\":0,\"maxTicketsPerOrder\":10,"
                + "\"physicalTicketsGenerated\":false,\"showAvailability\":true,"
                + "\"status\":\"DRAFT\",\"ticketCategories\":null}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerTokenA)
                .body(updateBody)
            .when()
                .put("/api/events/my/" + eventIdB)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(22)
    void idor_customerDeleteOtherEvent() {
        // Customer A tries to delete Customer B's event
        given()
                .header("Authorization", "Bearer " + customerTokenA)
            .when()
                .delete("/api/events/my/" + eventIdB)
            .then()
                .statusCode(403);
    }

    @Test
    @Order(23)
    void idor_customerCategoryOnOtherEvent() {
        // Customer A tries to create a ticket category on Customer B's event
        String categoryBody = "{\"name\":\"VIP\",\"description\":\"VIP category\","
                + "\"price\":50.00,\"serviceFee\":5.00,\"maxTickets\":50,"
                + "\"sortOrder\":1,\"active\":true}";

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + customerTokenA)
                .body(categoryBody)
            .when()
                .post("/api/events/my/" + eventIdB + "/categories")
            .then()
                .statusCode(403);
    }

    @Test
    @Order(24)
    void idor_publicOrderAccess() {
        // First, get a published event to create an order on
        Long publishedEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .extract()
                .path("[0].id")).longValue();

        // Create an order (no auth needed for ordering)
        Response orderResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Public\",\"buyerLastName\":\"Access\","
                        + "\"buyerEmail\":\"publicaccess@test.nl\","
                        + "\"buyerPhone\":\"+31600000050\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long publicOrderId = ((Number) orderResponse.path("id")).longValue();

        // Verify GET /api/orders/{id} works without auth
        // (document this as a known design choice: order lookup is public)
        given()
            .when()
                .get("/api/orders/" + publicOrderId)
            .then()
                .statusCode(200)
                .body("id", equalTo(publicOrderId.intValue()));
    }

    @Test
    @Order(25)
    void idor_publicPdfAccess() {
        // Get a published event and create + confirm an order
        Long publishedEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        Response orderResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Pdf\",\"buyerLastName\":\"Test\","
                        + "\"buyerEmail\":\"pdftest@test.nl\","
                        + "\"buyerPhone\":\"+31600000051\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long pdfOrderId = ((Number) orderResponse.path("id")).longValue();

        // Set buyer details
        given()
                .contentType(ContentType.JSON)
                .body("{\"buyerStreet\":\"Teststraat\",\"buyerHouseNumber\":\"1\","
                        + "\"buyerPostalCode\":\"1234AB\",\"buyerCity\":\"Amsterdam\"}")
            .when()
                .put("/api/orders/" + pdfOrderId + "/details")
            .then()
                .statusCode(200);

        // Confirm the order
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + pdfOrderId + "/confirm")
            .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // Verify GET /api/orders/{id}/pdf works without auth for confirmed orders
        // (document this as a known design choice)
        given()
            .when()
                .get("/api/orders/" + pdfOrderId + "/pdf")
            .then()
                .statusCode(200)
                .contentType("application/pdf");
    }

    @Test
    @Order(26)
    void idor_publicCancelOrder() {
        // Create an order
        Long publishedEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .path("[0].id")).longValue();

        Response orderResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Cancel\",\"buyerLastName\":\"Test\","
                        + "\"buyerEmail\":\"canceltest@test.nl\","
                        + "\"buyerPhone\":\"+31600000052\",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .response();

        Long cancelOrderId = ((Number) orderResponse.path("id")).longValue();

        // Set buyer details and confirm
        given()
                .contentType(ContentType.JSON)
                .body("{\"buyerStreet\":\"Teststraat\",\"buyerHouseNumber\":\"1\","
                        + "\"buyerPostalCode\":\"1234AB\",\"buyerCity\":\"Amsterdam\"}")
            .when()
                .put("/api/orders/" + cancelOrderId + "/details")
            .then()
                .statusCode(200);

        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelOrderId + "/confirm")
            .then()
                .statusCode(200);

        // Verify POST /api/orders/{id}/cancel works without auth
        // (document this as a security risk: anyone with orderId can cancel)
        given()
                .contentType(ContentType.JSON)
            .when()
                .post("/api/orders/" + cancelOrderId + "/cancel")
            .then()
                .statusCode(200);
    }

    @Test
    @Order(27)
    void idor_brandingWithoutAuth() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"primaryColor\":\"#FF0000\"}")
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(28)
    void idor_adminSetupBlocked() {
        // POST /api/admin/auth/setup should be blocked since admin already exists
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"hacker@evil.com\","
                        + "\"password\":\"hacker123\","
                        + "\"firstName\":\"Hacker\","
                        + "\"lastName\":\"Evil\"}")
            .when()
                .post("/api/admin/auth/setup")
            .then()
                .statusCode(anyOf(is(400), is(409)));
    }

    @Test
    @Order(29)
    void idor_forgotPasswordNoEnumeration() {
        // POST with existing email
        String existingResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"securitytest-a@example.com\"}")
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .extract()
                .path("message");

        // POST with non-existing email
        String nonExistingResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"nonexistent-user-xyz@doesnotexist.com\"}")
            .when()
                .post("/api/customer/auth/forgot-password")
            .then()
                .statusCode(200)
                .extract()
                .path("message");

        // Both responses must have the same message to prevent email enumeration
        assertEquals(existingResponse, nonExistingResponse,
                "Forgot password responses must be identical to prevent email enumeration");
    }

    @Test
    @Order(30)
    void idor_forgotPasswordUserNoEnumeration() {
        // POST with existing user email
        String existingResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"securityuser@example.com\"}")
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .extract()
                .path("message");

        // POST with non-existing email
        String nonExistingResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"totally-fake-user@doesnotexist.com\"}")
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .extract()
                .path("message");

        // Both responses must have the same message to prevent email enumeration
        assertEquals(existingResponse, nonExistingResponse,
                "Forgot password responses must be identical to prevent email enumeration");
    }

    @Test
    @Order(31)
    void idor_resendInviteForActiveCustomer() {
        // Customer A already has a password set (created in test 5/20)
        // Resending invite for a customer with a password set should fail
        String adminToken = getAdminToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .post("/api/customers/" + customerIdA + "/resend-invite")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(32)
    void idor_deleteCustomerWithEvents() {
        // Customer A has events (created in idor_setupCustomers)
        // Deleting a customer with events should fail with 409
        String adminToken = getAdminToken();

        given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .delete("/api/customers/" + customerIdA)
            .then()
                .statusCode(409);
    }
}
