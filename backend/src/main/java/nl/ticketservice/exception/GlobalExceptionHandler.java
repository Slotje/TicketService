package nl.ticketservice.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception exception) {
        if (exception instanceof TicketServiceException tse) {
            return Response.status(tse.getStatusCode())
                    .entity(Map.of("error", tse.getMessage()))
                    .build();
        }

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", "Er is een interne fout opgetreden"))
                .build();
    }
}
