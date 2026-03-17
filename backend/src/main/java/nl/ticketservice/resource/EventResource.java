package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.service.EventService;

import nl.ticketservice.service.AdminAuthService;

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
}
