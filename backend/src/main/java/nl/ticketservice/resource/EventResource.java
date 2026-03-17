package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.service.EventService;

import java.util.List;
import java.util.Map;

@Path("/api/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @GET
    public List<EventDTO> getAll() {
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
    public Response create(@Valid EventDTO dto) {
        EventDTO created = eventService.createEvent(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public EventDTO update(@PathParam("id") Long id, @Valid EventDTO dto) {
        return eventService.updateEvent(id, dto);
    }

    @PATCH
    @Path("/{id}/status")
    public EventDTO updateStatus(@PathParam("id") Long id, Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.isBlank()) {
            throw new BadRequestException("Status is verplicht");
        }
        return eventService.updateEventStatus(id, status);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        eventService.deleteEvent(id);
        return Response.noContent().build();
    }
}
