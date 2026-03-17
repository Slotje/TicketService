package nl.ticketservice.exception;

public class TicketServiceException extends RuntimeException {
    private final int statusCode;

    public TicketServiceException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
