package nl.ticketservice.resource;

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
public class XssSecurityTest {

    @Inject
    EntityManager em;

    private static Long xssCustomerId;
    private static String xssCustomerToken;
    private static Long xssEventId;

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

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"companyName\":\"" + companyName + "\","
                        + "\"contactPerson\":\"" + contactPerson + "\","
                        + "\"email\":\"" + email + "\","
                        + "\"phone\":\"+31611111111\","
                        + "\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201);

        String inviteToken = getInviteTokenForEmail(email);

        given()
                .contentType(ContentType.JSON)
                .body("{\"token\":\"" + inviteToken + "\",\"password\":\"Test1234\"}")
            .when()
                .post("/api/customer/auth/set-password")
            .then()
                .statusCode(200);

        return given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"" + email + "\",\"password\":\"Test1234\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private Long getCustomerId(String adminToken) {
        return ((Number) given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .extract()
                .path("[0].id")).longValue();
    }

    // =========================================================================
    // Test methods
    // =========================================================================

    @Test
    @Order(1)
    void xss_eventName() {
        String adminToken = getAdminToken();
        Long customerId = getCustomerId(adminToken);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"<script>alert('xss')</script>\","
                        + "\"description\":\"test\","
                        + "\"eventDate\":\"2027-06-15T14:00:00\","
                        + "\"endDate\":null,"
                        + "\"location\":\"Amsterdam\","
                        + "\"address\":\"Street 1\","
                        + "\"maxTickets\":100,"
                        + "\"physicalTickets\":0,"
                        + "\"ticketPrice\":25.00,"
                        + "\"serviceFee\":2.50,"
                        + "\"effectiveOnlineServiceFee\":null,"
                        + "\"maxTicketsPerOrder\":10,"
                        + "\"onlineTickets\":null,"
                        + "\"ticketsSold\":null,"
                        + "\"ticketsReserved\":null,"
                        + "\"availableTickets\":null,"
                        + "\"physicalTicketsSold\":null,"
                        + "\"availablePhysicalTickets\":null,"
                        + "\"totalSold\":null,"
                        + "\"physicalTicketsGenerated\":false,"
                        + "\"showAvailability\":true,"
                        + "\"imageUrl\":null,"
                        + "\"status\":\"DRAFT\","
                        + "\"customerId\":" + customerId + ","
                        + "\"customerName\":null,"
                        + "\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("name", equalTo("<script>alert('xss')</script>"));
    }

    @Test
    @Order(2)
    void xss_eventDescription() {
        String adminToken = getAdminToken();
        Long customerId = getCustomerId(adminToken);

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"XSS Desc Test Event\","
                        + "\"description\":\"<img src=x onerror=alert(1)>\","
                        + "\"eventDate\":\"2027-06-15T14:00:00\","
                        + "\"endDate\":null,"
                        + "\"location\":\"Amsterdam\","
                        + "\"address\":\"Street 1\","
                        + "\"maxTickets\":100,"
                        + "\"physicalTickets\":0,"
                        + "\"ticketPrice\":25.00,"
                        + "\"serviceFee\":2.50,"
                        + "\"effectiveOnlineServiceFee\":null,"
                        + "\"maxTicketsPerOrder\":10,"
                        + "\"onlineTickets\":null,"
                        + "\"ticketsSold\":null,"
                        + "\"ticketsReserved\":null,"
                        + "\"availableTickets\":null,"
                        + "\"physicalTicketsSold\":null,"
                        + "\"availablePhysicalTickets\":null,"
                        + "\"totalSold\":null,"
                        + "\"physicalTicketsGenerated\":false,"
                        + "\"showAvailability\":true,"
                        + "\"imageUrl\":null,"
                        + "\"status\":\"DRAFT\","
                        + "\"customerId\":" + customerId + ","
                        + "\"customerName\":null,"
                        + "\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("description", equalTo("<img src=x onerror=alert(1)>"));
    }

    @Test
    @Order(3)
    void xss_customerCompanyName() {
        String adminToken = getAdminToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"companyName\":\"<script>alert(1)</script>\","
                        + "\"contactPerson\":\"XSS Test\","
                        + "\"email\":\"xsscustomer@test.nl\","
                        + "\"phone\":\"+31611111111\","
                        + "\"active\":true}")
            .when()
                .post("/api/customers")
            .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("companyName", equalTo("<script>alert(1)</script>"));

        // Login the XSS customer for use in later tests
        xssCustomerToken = createAndLoginCustomer(
                "XSS Cust Corp", "XSS Contact", "xsscustcorp@test.nl");

        // Get the customer ID for later use
        xssCustomerId = ((Number) given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"xsscustcorp@test.nl\",\"password\":\"Test1234\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .path("customerId")).longValue();
    }

    @Test
    @Order(4)
    void xss_orderBuyerName() {
        Long publishedEventId = ((Number) given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .body("$.size()", greaterThan(0))
                .extract()
                .path("[0].id")).longValue();

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId + ","
                        + "\"buyerFirstName\":\"<script>alert(1)</script>\","
                        + "\"buyerLastName\":\"Test\","
                        + "\"buyerEmail\":\"xss@test.nl\","
                        + "\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("buyerFirstName", equalTo("<script>alert(1)</script>"));
    }

    @Test
    @Order(5)
    void xss_userRegistration() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"xssuser@test.nl\","
                        + "\"password\":\"Test1234\","
                        + "\"firstName\":\"<script>alert(1)</script>\","
                        + "\"lastName\":\"Test\","
                        + "\"phone\":null}")
            .when()
                .post("/api/user/auth/register")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("firstName", equalTo("<script>alert(1)</script>"));
    }

    @Test
    @Order(6)
    void xss_brandingWebsite() {
        assertNotNull(xssCustomerToken, "Customer token should have been set in test 3");

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + xssCustomerToken)
                .body("{\"website\":\"javascript:alert(1)\"}")
            .when()
                .put("/api/customer/auth/branding")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true));
    }

    @Test
    @Order(7)
    void xss_categoryName() {
        assertNotNull(xssCustomerToken, "Customer token should have been set in test 3");
        assertNotNull(xssCustomerId, "Customer ID should have been set in test 3");

        // Create an event owned by the XSS customer via admin
        String adminToken = getAdminToken();

        xssEventId = ((Number) given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"XSS Category Test Event\","
                        + "\"description\":\"test\","
                        + "\"eventDate\":\"2027-07-15T14:00:00\","
                        + "\"endDate\":null,"
                        + "\"location\":\"Amsterdam\","
                        + "\"address\":\"Street 1\","
                        + "\"maxTickets\":100,"
                        + "\"physicalTickets\":0,"
                        + "\"ticketPrice\":25.00,"
                        + "\"serviceFee\":2.50,"
                        + "\"effectiveOnlineServiceFee\":null,"
                        + "\"maxTicketsPerOrder\":10,"
                        + "\"onlineTickets\":null,"
                        + "\"ticketsSold\":null,"
                        + "\"ticketsReserved\":null,"
                        + "\"availableTickets\":null,"
                        + "\"physicalTicketsSold\":null,"
                        + "\"availablePhysicalTickets\":null,"
                        + "\"totalSold\":null,"
                        + "\"physicalTicketsGenerated\":false,"
                        + "\"showAvailability\":true,"
                        + "\"imageUrl\":null,"
                        + "\"status\":\"DRAFT\","
                        + "\"customerId\":" + xssCustomerId + ","
                        + "\"customerName\":null,"
                        + "\"ticketCategories\":null}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(201)
                .extract()
                .path("id")).longValue();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + xssCustomerToken)
                .body("{\"name\":\"<script>alert(1)</script>\","
                        + "\"price\":10.00,"
                        + "\"maxTickets\":50,"
                        + "\"sortOrder\":0,"
                        + "\"active\":true}")
            .when()
                .post("/api/events/my/" + xssEventId + "/categories")
            .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("name", equalTo("<script>alert(1)</script>"));
    }

    @Test
    @Order(8)
    void xss_allJsonEndpointsReturnJsonContentType() {
        // GET /api/events/published
        Response publishedResponse = given()
            .when()
                .get("/api/events/published")
            .then()
                .statusCode(200)
                .extract()
                .response();
        assertTrue(publishedResponse.contentType().contains("application/json"),
                "GET /api/events/published should return application/json but was: " + publishedResponse.contentType());

        // GET /api/customers (admin auth)
        String adminToken = getAdminToken();
        Response customersResponse = given()
                .header("Authorization", "Bearer " + adminToken)
            .when()
                .get("/api/customers")
            .then()
                .statusCode(200)
                .extract()
                .response();
        assertTrue(customersResponse.contentType().contains("application/json"),
                "GET /api/customers should return application/json but was: " + customersResponse.contentType());

        // POST /api/customer/auth/login (with valid creds)
        assertNotNull(xssCustomerToken, "Customer should have been created in earlier tests");
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"xsscustcorp@test.nl\",\"password\":\"Test1234\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(200)
                .extract()
                .response();
        assertTrue(loginResponse.contentType().contains("application/json"),
                "POST /api/customer/auth/login should return application/json but was: " + loginResponse.contentType());
    }
}
