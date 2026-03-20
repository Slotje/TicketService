package nl.ticketservice;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import nl.ticketservice.dto.RegisterDTO;
import nl.ticketservice.dto.UserLoginDTO;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class AdminAuthResourceTest {

    @Test
    @Order(1)
    void setupCheck_returnsNeedsSetupFalse() {
        given()
            .when()
            .get("/api/admin/auth/setup")
            .then()
            .statusCode(200)
            .body("needsSetup", equalTo(false));
    }

    @Test
    @Order(2)
    void setup_whenAdminAlreadyExists_returnsBadRequest() {
        RegisterDTO register = new RegisterDTO("admin2@ticketservice.nl", "password", "Admin 2");

        given()
            .contentType(ContentType.JSON)
            .body(register)
            .when()
            .post("/api/admin/auth/setup")
            .then()
            .statusCode(400)
            .body(containsString("error"));
    }

    @Test
    @Order(3)
    void login_withValidCredentials_returnsTokenAndUser() {
        UserLoginDTO login = new UserLoginDTO("admin@ticketservice.nl", "admin");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/admin/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("email", equalTo("admin@ticketservice.nl"))
            .body("name", notNullValue());
    }

    @Test
    @Order(4)
    void login_withWrongPassword_returnsUnauthorized() {
        UserLoginDTO login = new UserLoginDTO("admin@ticketservice.nl", "wrongpassword");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/admin/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(5)
    void login_withWrongEmail_returnsUnauthorized() {
        UserLoginDTO login = new UserLoginDTO("unknown@ticketservice.nl", "admin");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/admin/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(6)
    void verify_withValidToken_returnsOk() {
        UserLoginDTO login = new UserLoginDTO("admin@ticketservice.nl", "admin");

        String token = given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/admin/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/admin/auth/verify")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(7)
    void verify_withoutAuthorizationHeader_returnsUnauthorized() {
        given()
            .when()
            .get("/api/admin/auth/verify")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(8)
    void verify_withInvalidToken_returnsUnauthorized() {
        given()
            .header("Authorization", "Bearer invalid.token.value")
            .when()
            .get("/api/admin/auth/verify")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(9)
    void verify_withExpiredOrMalformedToken_returnsUnauthorized() {
        given()
            .header("Authorization", "Bearer invalid-token-value")
            .when()
            .get("/api/admin/auth/verify")
            .then()
            .statusCode(401);
    }
}
