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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class SqlInjectionSecurityTest {

    @Inject
    EntityManager em;

    private static Long publishedEventId;
    private static Long orderId;

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
    // Test 1: SQL injection in customer login email field
    // =========================================================================
    @Test
    @Order(1)
    void testSqlInjectionInCustomerLoginEmail() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"' OR 1=1 --\",\"password\":\"test\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(anyOf(is(400), is(401), is(404)))
                .body("token", nullValue());
    }

    // =========================================================================
    // Test 2: SQL injection in customer login password field
    // =========================================================================
    @Test
    @Order(2)
    void testSqlInjectionInCustomerLoginPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"test@example.com\",\"password\":\"' OR 1=1 --\"}")
            .when()
                .post("/api/customer/auth/login")
            .then()
                .statusCode(anyOf(is(400), is(401), is(404)))
                .body("token", nullValue());
    }

    // =========================================================================
    // Test 3: SQL injection in order lookup by email
    // =========================================================================
    @Test
    @Order(3)
    void testSqlInjectionInOrderLookupByEmail() {
        given()
            .when()
                .get("/api/orders/email/' OR 1=1 --")
            .then()
                .statusCode(anyOf(is(200), is(400)))
                .body("$.size()", anyOf(is(0), nullValue()));
    }

    // =========================================================================
    // Test 4: SQL injection in order lookup by number
    // =========================================================================
    @Test
    @Order(4)
    void testSqlInjectionInOrderLookupByNumber() {
        given()
            .when()
                .get("/api/orders/number/' UNION SELECT * --")
            .then()
                .statusCode(anyOf(is(400), is(404)))
                .body(not(containsString("SQLException")));
    }

    // =========================================================================
    // Test 5: SQL injection in customer slug lookup
    // =========================================================================
    @Test
    @Order(5)
    void testSqlInjectionInCustomerSlugLookup() {
        given()
            .when()
                .get("/api/customers/slug/' OR '1'='1")
            .then()
                .statusCode(anyOf(is(400), is(404)))
                .body(not(containsString("SQLException")));
    }

    // =========================================================================
    // Test 6: SQL injection in user forgot-password email
    // =========================================================================
    @Test
    @Order(6)
    void testSqlInjectionInUserForgotPasswordEmail() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"email\":\"test@x.com' OR 1=1 --\"}")
            .when()
                .post("/api/user/auth/forgot-password")
            .then()
                .statusCode(200)
                .body("message", notNullValue());
    }

    // =========================================================================
    // Test 7: SQL injection in order creation buyer fields
    // =========================================================================
    @Test
    @Order(7)
    void testSqlInjectionInOrderCreationBuyerFields() {
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"'; DROP TABLE tickets;--\""
                        + ",\"buyerLastName\":\"Test\""
                        + ",\"buyerEmail\":\"sqlitest@test.nl\""
                        + ",\"buyerPhone\":\"+31600000000\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // =========================================================================
    // Test 8: SQL injection in buyer details update
    // =========================================================================
    @Test
    @Order(8)
    void testSqlInjectionInBuyerDetailsUpdate() {
        // First create an order to get a valid ID
        if (publishedEventId == null) {
            publishedEventId = getPublishedEventId();
        }

        Number id = given()
                .contentType(ContentType.JSON)
                .body("{\"eventId\":" + publishedEventId
                        + ",\"buyerFirstName\":\"Jan\""
                        + ",\"buyerLastName\":\"Tester\""
                        + ",\"buyerEmail\":\"details-sqli@test.nl\""
                        + ",\"buyerPhone\":\"+31600000001\""
                        + ",\"quantity\":1}")
            .when()
                .post("/api/orders")
            .then()
                .statusCode(200)
                .extract()
                .path("id");

        orderId = id.longValue();

        given()
                .contentType(ContentType.JSON)
                .body("{\"buyerStreet\":\"' UNION SELECT * FROM users--\""
                        + ",\"buyerHouseNumber\":\"1\""
                        + ",\"buyerPostalCode\":\"1234AB\""
                        + ",\"buyerCity\":\"'; DROP TABLE orders;--\"}")
            .when()
                .put("/api/orders/" + orderId + "/details")
            .then()
                .statusCode(anyOf(is(200), is(400)));
    }

    // =========================================================================
    // Test 9: SQL injection in event creation (admin)
    // =========================================================================
    @Test
    @Order(9)
    void testSqlInjectionInEventCreation() {
        String adminToken = getAdminToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("{\"name\":\"'; DROP TABLE events;--\""
                        + ",\"description\":\"' UNION SELECT * FROM users--\""
                        + ",\"eventDate\":\"2026-12-31T20:00:00\""
                        + ",\"location\":\"Test Location\""
                        + ",\"maxTickets\":100"
                        + ",\"ticketPrice\":10.00"
                        + ",\"serviceFee\":0.50}")
            .when()
                .post("/api/events")
            .then()
                .statusCode(anyOf(is(200), is(201), is(400)));
    }

    // =========================================================================
    // Test 10: SQL injection in scan ticket QR data
    // =========================================================================
    @Test
    @Order(10)
    void testSqlInjectionInScanTicketQrData() {
        String scannerToken = getScannerToken();

        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + scannerToken)
            .when()
                .post("/api/orders/scan/' OR 1=1 --?eventId=1")
            .then()
                .statusCode(anyOf(is(400), is(404)))
                .body(not(containsString("SQLException")));
    }
}
