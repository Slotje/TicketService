package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class AuthResourceTest {

    private static String scannerToken;
    private static Long createdUserId;

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

    @Test
    @Order(1)
    void testLoginSuccess() {
        scannerToken = given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"scanner\",\"password\":\"scanner123\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("displayName", notNullValue())
                .body("username", equalTo("scanner"))
                .extract()
                .path("token");
    }

    @Test
    @Order(2)
    void testLoginWrongPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"scanner\",\"password\":\"wrongpassword\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(3)
    void testLoginWrongUsername() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"username\":\"nonexistent\",\"password\":\"scanner123\"}")
            .when()
                .post("/api/auth/login")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(4)
    void testVerifyWithValidToken() {
        given()
                .header("Authorization", "Bearer " + scannerToken)
            .when()
                .get("/api/auth/verify")
            .then()
                .statusCode(200)
                .body("username", equalTo("scanner"))
                .body("token", notNullValue())
                .body("displayName", notNullValue());
    }

    @Test
    @Order(5)
    void testVerifyWithoutHeader() {
        given()
            .when()
                .get("/api/auth/verify")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(6)
    void testVerifyWithInvalidToken() {
        given()
                .header("Authorization", "Bearer invalidtoken123")
            .when()
                .get("/api/auth/verify")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(7)
    void testGetUsersWithAdminToken() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .get("/api/auth/users")
            .then()
                .statusCode(200)
                .body("$", isA(java.util.List.class));
    }

    @Test
    @Order(8)
    void testGetUsersWithoutAdminToken() {
        given()
            .when()
                .get("/api/auth/users")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(9)
    void testCreateUserWithAdminToken() {
        Number rawId = given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"username\":\"newscanner\",\"password\":\"pass1234\",\"displayName\":\"New Scanner\"}")
            .when()
                .post("/api/auth/users")
            .then()
                .statusCode(200)
                .body("username", equalTo("newscanner"))
                .body("displayName", equalTo("New Scanner"))
                .body("active", equalTo(true))
                .body("id", notNullValue())
                .extract()
                .path("id");

        createdUserId = rawId.longValue();
    }

    @Test
    @Order(10)
    void testCreateUserDuplicateUsername() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
                .body("{\"username\":\"newscanner\",\"password\":\"pass1234\",\"displayName\":\"New Scanner\"}")
            .when()
                .post("/api/auth/users")
            .then()
                .statusCode(400);
    }

    @Test
    @Order(11)
    void testToggleUserActive() {
        given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .patch("/api/auth/users/" + createdUserId + "/toggle")
            .then()
                .statusCode(200)
                .body("active", equalTo(false));
    }

    @Test
    @Order(12)
    void testDeleteUser() {
        given()
                .header("Authorization", "Bearer " + getAdminToken())
            .when()
                .delete("/api/auth/users/" + createdUserId)
            .then()
                .statusCode(204);
    }
}
