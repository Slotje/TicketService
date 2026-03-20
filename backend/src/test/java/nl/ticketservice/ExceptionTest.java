package nl.ticketservice;

import nl.ticketservice.exception.ConstraintViolationExceptionMapper;
import nl.ticketservice.exception.GlobalExceptionHandler;
import nl.ticketservice.exception.TicketServiceException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    // --- TicketServiceException tests ---

    @Test
    void testTicketServiceExceptionConstructor() {
        TicketServiceException ex = new TicketServiceException("Niet gevonden", 404);

        assertEquals("Niet gevonden", ex.getMessage());
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void testTicketServiceExceptionGetStatusCode() {
        TicketServiceException ex = new TicketServiceException("Verboden", 403);

        assertEquals(403, ex.getStatusCode());
    }

    @Test
    void testTicketServiceExceptionGetMessage() {
        TicketServiceException ex = new TicketServiceException("Fout opgetreden", 500);

        assertEquals("Fout opgetreden", ex.getMessage());
    }

    // --- GlobalExceptionHandler tests ---

    @Test
    void testGlobalExceptionHandlerWithTicketServiceException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        TicketServiceException ex = new TicketServiceException("Evenement niet gevonden", 404);

        Response response = handler.toResponse(ex);

        assertEquals(404, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Evenement niet gevonden", body.get("error"));
    }

    @Test
    void testGlobalExceptionHandlerWithRuntimeException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        RuntimeException ex = new RuntimeException("Onverwachte fout");

        Response response = handler.toResponse(ex);

        assertEquals(500, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, String> body = (Map<String, String>) response.getEntity();
        assertEquals("Er is een interne fout opgetreden", body.get("error"));
    }

    // --- ConstraintViolationExceptionMapper tests ---

    @Test
    @SuppressWarnings("unchecked")
    void testConstraintViolationExceptionMapper() {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        ConstraintViolation<?> v1 = Mockito.mock(ConstraintViolation.class);
        Mockito.when(v1.getMessage()).thenReturn("Veld 1 is verplicht");
        ConstraintViolation<?> v2 = Mockito.mock(ConstraintViolation.class);
        Mockito.when(v2.getMessage()).thenReturn("Veld 2 is ongeldig");

        @SuppressWarnings("rawtypes")
        Set violations = Set.of(v1, v2);
        @SuppressWarnings("rawtypes")
        ConstraintViolationException ex = new ConstraintViolationException(violations);

        Response response = mapper.toResponse(ex);

        assertEquals(400, response.getStatus());
        Map<String, String> body = (Map<String, String>) response.getEntity();
        String error = body.get("error");
        // The order of violations in a Set is not guaranteed
        assertTrue(error.contains("Veld 1 is verplicht"));
        assertTrue(error.contains("Veld 2 is ongeldig"));
    }
}
