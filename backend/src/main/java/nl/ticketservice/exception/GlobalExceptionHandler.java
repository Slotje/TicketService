package nl.ticketservice.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.stream.Collectors;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof TicketServiceException tse) {
            return Response.status(tse.getStatusCode())
                    .entity(Map.of("error", tse.getMessage()))
                    .build();
        }

        if (exception instanceof ConstraintViolationException cve) {
            var errors = cve.getConstraintViolations().stream()
                    .collect(Collectors.toMap(
                            v -> v.getPropertyPath().toString(),
                            v -> v.getMessage(),
                            (a, b) -> a + "; " + b
                    ));
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("errors", errors))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Er is een interne fout opgetreden"))
                .build();
    }
}
