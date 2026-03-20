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

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class CustomerAuthResourceTest {

    @Inject
    EntityManager em;

    private static String adminToken;
    private static String inviteToken;
    private static String customerAuthToken;
    private static Long customerId;
    private static final String CUSTOMER_EMAIL = "auth-test@example.com";
    private static final String CUSTOMER_PASSWORD = "SecurePass123!";

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

    @Transactional
    String getInviteToken(String email) {
        Customer c = (Customer) em.createQuery("SELECT c FROM Customer c WHERE c.email = :email")
                .setParameter("email", email)
                .getSingleResult();
        return c.inviteToken;
    }

    @Test
    @Order(1)
    public void testCreateCustomerForAuthTests() {
        Response response = given()
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "companyName", "Auth Test BV",
                        "contactPerson", "Auth Tester",
                        "email", CUSTOMER_EMAIL,
                        "phone", "+31600000099",
                        "active", true
                ))
                .when()
                .post("/api/customers")
                .then()
                .statusCode(201)
                .extract()
                .response();

        customerId = ((Number) response.jsonPath().get("id")).longValue();

        // Retrieve the invite token from the database
        inviteToken = getInviteToken(CUSTOMER_EMAIL);
        org.junit.jupiter.api.Assertions.assertNotNull(inviteToken, "Invite token should have been generated");
    }

    @Test
    @Order(2)
    public void testVerifyInviteWithValidToken() {
        given()
                .when()
                .get("/api/customer/auth/invite/" + inviteToken)
                .then()
                .statusCode(200)
                .body("companyName", equalTo("Auth Test BV"))
                .body("email", equalTo(CUSTOMER_EMAIL));
    }

    @Test
    @Order(3)
    public void testVerifyInviteWithInvalidToken() {
        given()
                .when()
                .get("/api/customer/auth/invite/invalid-token-12345")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(4)
    public void testLoginWithoutPasswordSet() {
        // Customer has no password yet (not activated), should get 401
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", CUSTOMER_EMAIL, "password", "anything"))
                .when()
                .post("/api/customer/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(5)
    public void testSetPasswordWithValidToken() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", inviteToken, "password", CUSTOMER_PASSWORD))
                .when()
                .post("/api/customer/auth/set-password")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("customerId", notNullValue())
                .body("companyName", equalTo("Auth Test BV"))
                .body("email", equalTo(CUSTOMER_EMAIL))
                .extract()
                .response();

        customerAuthToken = response.jsonPath().getString("token");
    }

    @Test
    @Order(6)
    public void testSetPasswordWithInvalidToken() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("token", "invalid-token-xyz", "password", "SomePass123"))
                .when()
                .post("/api/customer/auth/set-password")
                .then()
                .statusCode(400);
    }

    @Test
    @Order(7)
    public void testLoginWithCorrectCredentials() {
        Response response = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", CUSTOMER_EMAIL, "password", CUSTOMER_PASSWORD))
                .when()
                .post("/api/customer/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("customerId", notNullValue())
                .body("companyName", equalTo("Auth Test BV"))
                .body("email", equalTo(CUSTOMER_EMAIL))
                .extract()
                .response();

        customerAuthToken = response.jsonPath().getString("token");
    }

    @Test
    @Order(8)
    public void testLoginWithWrongPassword() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", CUSTOMER_EMAIL, "password", "WrongPassword123"))
                .when()
                .post("/api/customer/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(9)
    public void testLoginWithNonexistentEmail() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "nonexistent@example.com", "password", "SomePass123"))
                .when()
                .post("/api/customer/auth/login")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(10)
    public void testVerifyWithValidToken() {
        given()
                .header("Authorization", "Bearer " + customerAuthToken)
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(200)
                .body("customerId", notNullValue())
                .body("companyName", equalTo("Auth Test BV"))
                .body("email", equalTo(CUSTOMER_EMAIL));
    }

    @Test
    @Order(11)
    public void testVerifyWithoutToken() {
        given()
                .when()
                .get("/api/customer/auth/verify")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(12)
    public void testResendInviteForCustomerWithPasswordSet() {
        // This customer already has a password set (from testSetPasswordWithValidToken),
        // so resend-invite should return 400
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .when()
                .post("/api/customers/" + customerId + "/resend-invite")
                .then()
                .statusCode(400);
    }
}
