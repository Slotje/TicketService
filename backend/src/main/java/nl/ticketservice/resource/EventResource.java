package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.exception.TicketServiceException;
import nl.ticketservice.service.EventService;
import nl.ticketservice.service.AdminAuthService;
import nl.ticketservice.service.CustomerAuthService;

import java.util.List;
import java.util.Map;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @Inject
    AdminAuthService adminAuthService;

    @Inject
    CustomerAuthService customerAuthService;

    @GET
    public List<EventDTO> getAll(@HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return eventService.getAllEvents();
    }

    @GET
    @Path("/published")
    public List<EventDTO> getPublished() {
        return eventService.getPublishedEvents();
    }

    @GET
    @Path("/customer/{customerId}")
    public List<EventDTO> getByCustomer(@PathParam("customerId") Long customerId) {
        return eventService.getEventsByCustomer(customerId);
    }

    @GET
    @Path("/{id}")
    public EventDTO getById(@PathParam("id") Long id) {
        return eventService.getEvent(id);
    }

    @POST
    public Response create(@Valid EventDTO dto, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        EventDTO created = eventService.createEvent(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public EventDTO update(@PathParam("id") Long id, @Valid EventDTO dto,
                           @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return eventService.updateEvent(id, dto);
    }

    @PATCH
    @Path("/{id}/status")
    public EventDTO updateStatus(@PathParam("id") Long id, Map<String, String> body,
                                 @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Status is verplicht");
        }
        return eventService.updateEventStatus(id, status);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        eventService.deleteEvent(id);
        return Response.noContent().build();
    }

    // =========================================================================
    // Customer-specific endpoints (klant beheert eigen evenementen)
    // =========================================================================

    @GET
    @Path("/my")
    public List<EventDTO> getMyEvents(@HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        return eventService.getEventsByCustomer(customer.id);
    }

    @POST
    @Path("/my")
    public Response createMyEvent(@Valid EventDTO dto, @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        EventDTO forced = new EventDTO(
                dto.id(), dto.name(), dto.description(), dto.eventDate(), dto.endDate(),
                dto.location(), dto.address(), dto.maxTickets(), dto.ticketPrice(),
                dto.serviceFee(), dto.maxTicketsPerOrder(), dto.ticketsSold(), dto.ticketsReserved(),
                dto.availableTickets(), dto.imageUrl(), dto.status(),
                customer.id, customer.companyName
        );
        EventDTO created = eventService.createEvent(forced);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/my/{id}")
    public EventDTO updateMyEvent(@PathParam("id") Long id, @Valid EventDTO dto,
                                  @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        return eventService.updateEvent(id, dto);
    }

    @PATCH
    @Path("/my/{id}/status")
    public EventDTO updateMyEventStatus(@PathParam("id") Long id, Map<String, String> body,
                                        @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Status is verplicht");
        }
        return eventService.updateEventStatus(id, status);
    }

    @DELETE
    @Path("/my/{id}")
    public Response deleteMyEvent(@PathParam("id") Long id, @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        eventService.deleteEvent(id);
        return Response.noContent().build();
    }

    private void requireOwnership(Long eventId, Customer customer) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }
        if (!event.customer.id.equals(customer.id)) {
            throw new TicketServiceException("Je hebt geen toegang tot dit evenement", 403);
        }
    }
}
