package nl.ticketservice.exception;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class ExceptionHandlerTest {

    // =========================================================================
    // GlobalExceptionHandler - TicketServiceException path
    // =========================================================================

    @Test
    @Order(1)
    void testTicketServiceExceptionPath() {
        // Requesting a non-existent order triggers TicketServiceException(404)
        given()
            .when()
                .get("/api/orders/999999")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    @Test
    @Order(2)
    void testTicketServiceExceptionWithBadRequest() {
        // Creating an order for a non-existent event triggers TicketServiceException(404)
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", 999999,
                        "buyerFirstName", "Test",
                        "buyerLastName", "User",
                        "buyerEmail", "test@test.nl",
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    // =========================================================================
    // GlobalExceptionHandler - WebApplicationException path
    // =========================================================================

    @Test
    @Order(10)
    void testWebApplicationExceptionPath() {
        // Accessing a protected admin endpoint without auth triggers 401
        given()
            .when()
                .get("/api/customers")
            .then()
                .statusCode(401);
    }

    @Test
    @Order(11)
    void testWebApplicationException405() {
        // Using an unsupported HTTP method on a known endpoint triggers 405
        given()
                .contentType(ContentType.JSON)
            .when()
                .delete("/api/orders")
            .then()
                .statusCode(405);
    }

    // =========================================================================
    // ConstraintViolationExceptionMapper path
    // =========================================================================

    @Test
    @Order(20)
    void testConstraintViolationWithMissingFields() {
        // Sending an order with missing required fields triggers ConstraintViolationException
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", 1,
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    @Test
    @Order(21)
    void testConstraintViolationWithInvalidEmail() {
        // Sending an order with an invalid email triggers validation error
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", 1,
                        "buyerFirstName", "Test",
                        "buyerLastName", "User",
                        "buyerEmail", "not-an-email",
                        "quantity", 1))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    @Test
    @Order(22)
    void testConstraintViolationWithQuantityTooHigh() {
        // Quantity > 10 should trigger @Max constraint
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", 1,
                        "buyerFirstName", "Test",
                        "buyerLastName", "User",
                        "buyerEmail", "test@test.nl",
                        "quantity", 99))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    @Test
    @Order(23)
    void testConstraintViolationWithQuantityZero() {
        // Quantity = 0 should trigger @Min(1) constraint
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "eventId", 1,
                        "buyerFirstName", "Test",
                        "buyerLastName", "User",
                        "buyerEmail", "test@test.nl",
                        "quantity", 0))
            .when()
                .post("/api/orders")
            .then()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("error", notNullValue());
    }

    // =========================================================================
    // GlobalExceptionHandler unit tests (non-integration, direct invocation)
    // =========================================================================

    @Test
    @Order(30)
    void testGlobalHandlerTicketServiceExceptionDirect() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        TicketServiceException ex = new TicketServiceException("Test fout", 422);

        jakarta.ws.rs.core.Response response = handler.toResponse(ex);

        assertEquals(422, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Test fout", body.get("error"));
    }

    @Test
    @Order(31)
    void testGlobalHandlerWebApplicationExceptionDirect() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        jakarta.ws.rs.WebApplicationException ex =
                new jakarta.ws.rs.WebApplicationException("Niet toegestaan", 403);

        jakarta.ws.rs.core.Response response = handler.toResponse(ex);

        assertEquals(403, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Niet toegestaan", body.get("error"));
    }

    @Test
    @Order(32)
    void testGlobalHandlerGenericExceptionDirect() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Exception ex = new Exception("Iets ging fout");

        jakarta.ws.rs.core.Response response = handler.toResponse(ex);

        assertEquals(500, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Er is een interne fout opgetreden", body.get("error"));
    }

    @Test
    @Order(33)
    void testGlobalHandlerRuntimeExceptionDirect() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException ex = new RuntimeException("Onverwachte runtime fout");

        jakarta.ws.rs.core.Response response = handler.toResponse(ex);

        assertEquals(500, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Er is een interne fout opgetreden", body.get("error"));
    }

    @Test
    @Order(34)
    void testGlobalHandlerNullPointerExceptionDirect() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        NullPointerException ex = new NullPointerException("null ref");

        jakarta.ws.rs.core.Response response = handler.toResponse(ex);

        assertEquals(500, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Er is een interne fout opgetreden", body.get("error"));
    }

    // =========================================================================
    // ConstraintViolationExceptionMapper unit test (direct invocation)
    // =========================================================================

    @Test
    @Order(40)
    @SuppressWarnings("unchecked")
    void testConstraintViolationMapperDirect() {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        jakarta.validation.ConstraintViolation<?> v1 = org.mockito.Mockito.mock(jakarta.validation.ConstraintViolation.class);
        org.mockito.Mockito.when(v1.getMessage()).thenReturn("Naam is verplicht");

        jakarta.validation.ConstraintViolation<?> v2 = org.mockito.Mockito.mock(jakarta.validation.ConstraintViolation.class);
        org.mockito.Mockito.when(v2.getMessage()).thenReturn("E-mail is ongeldig");

        @SuppressWarnings("rawtypes")
        java.util.Set violations = java.util.Set.of(v1, v2);
        @SuppressWarnings("rawtypes")
        jakarta.validation.ConstraintViolationException ex = new jakarta.validation.ConstraintViolationException(violations);

        jakarta.ws.rs.core.Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
        Map<String, String> body = (Map<String, String>) response.getEntity();
        String error = body.get("error");
        assertTrue(error.contains("Naam is verplicht"));
        assertTrue(error.contains("E-mail is ongeldig"));
    }

    @Test
    @Order(41)
    @SuppressWarnings("unchecked")
    void testConstraintViolationMapperSingleViolation() {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        jakarta.validation.ConstraintViolation<?> v = org.mockito.Mockito.mock(jakarta.validation.ConstraintViolation.class);
        org.mockito.Mockito.when(v.getMessage()).thenReturn("Veld is verplicht");

        @SuppressWarnings("rawtypes")
        java.util.Set violations = java.util.Set.of(v);
        @SuppressWarnings("rawtypes")
        jakarta.validation.ConstraintViolationException ex = new jakarta.validation.ConstraintViolationException(violations);

        jakarta.ws.rs.core.Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Veld is verplicht", body.get("error"));
    }

    // =========================================================================
    // TicketServiceException coverage
    // =========================================================================

    @Test
    @Order(50)
    void testTicketServiceExceptionFields() {
        TicketServiceException ex = new TicketServiceException("Niet gevonden", 404);
        assertEquals("Niet gevonden", ex.getMessage());
        assertEquals(404, ex.getStatusCode());

        TicketServiceException ex2 = new TicketServiceException("Server fout", 500);
        assertEquals("Server fout", ex2.getMessage());
        assertEquals(500, ex2.getStatusCode());
    }
}
