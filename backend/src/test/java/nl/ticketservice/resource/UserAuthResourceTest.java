package nl.ticketservice.resource;

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
class UserAuthResourceTest {

    @Test
    @Order(1)
    void register_withValidData_returnsTokenAndUser() {
        RegisterDTO register = new RegisterDTO("test@example.com", "test123456", "Test User");

        given()
            .contentType(ContentType.JSON)
            .body(register)
            .when()
            .post("/api/user/auth/register")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("email", equalTo("test@example.com"))
            .body("name", equalTo("Test User"));
    }

    @Test
    @Order(2)
    void register_withDuplicateEmail_returnsBadRequest() {
        RegisterDTO register = new RegisterDTO("test@example.com", "test123456", "Test User");

        given()
            .contentType(ContentType.JSON)
            .body(register)
            .when()
            .post("/api/user/auth/register")
            .then()
            .statusCode(400);
    }

    @Test
    @Order(3)
    void login_withValidCredentials_returnsTokenAndUser() {
        UserLoginDTO login = new UserLoginDTO("test@example.com", "test123456");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/user/auth/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .body("email", equalTo("test@example.com"))
            .body("name", equalTo("Test User"));
    }

    @Test
    @Order(4)
    void login_withWrongPassword_returnsUnauthorized() {
        UserLoginDTO login = new UserLoginDTO("test@example.com", "wrongpassword");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/user/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(5)
    void login_withUnknownEmail_returnsUnauthorized() {
        UserLoginDTO login = new UserLoginDTO("unknown@example.com", "test123456");

        given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/user/auth/login")
            .then()
            .statusCode(401);
    }

    @Test
    @Order(6)
    void verify_withValidToken_returnsOk() {
        UserLoginDTO login = new UserLoginDTO("test@example.com", "test123456");

        String token = given()
            .contentType(ContentType.JSON)
            .body(login)
            .when()
            .post("/api/user/auth/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token");

        given()
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/user/auth/verify")
            .then()
            .statusCode(200);
    }

    @Test
    @Order(7)
    void verify_withoutAuthorizationHeader_returnsUnauthorized() {
        given()
            .when()
            .get("/api/user/auth/verify")
            .then()
            .statusCode(401);
    }
}
