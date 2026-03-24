package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.dto.TicketCategoryDTO;
import nl.ticketservice.dto.TicketSalesDTO;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.exception.TicketServiceException;
import nl.ticketservice.service.EventService;
import nl.ticketservice.service.AdminAuthService;
import nl.ticketservice.service.CustomerAuthService;
import nl.ticketservice.service.PhysicalTicketService;

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

    @Inject
    PhysicalTicketService physicalTicketService;

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
    // Physical tickets & Sales insight (admin)
    // =========================================================================

    @POST
    @Path("/{id}/physical-tickets/generate")
    @Produces("application/pdf")
    public Response generatePhysicalTickets(@PathParam("id") Long id,
                                             @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        byte[] pdf = physicalTicketService.generatePhysicalTickets(id);
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"fysieke-tickets-" + id + ".pdf\"")
                .build();
    }

    @GET
    @Path("/{id}/physical-tickets/pdf")
    @Produces("application/pdf")
    public Response downloadPhysicalTicketsPdf(@PathParam("id") Long id,
                                                @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        byte[] pdf = physicalTicketService.getPhysicalTicketsPdf(id);
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"fysieke-tickets-" + id + ".pdf\"")
                .build();
    }

    @POST
    @Path("/{id}/physical-tickets/sell")
    public EventDTO markPhysicalTicketsSold(@PathParam("id") Long id, Map<String, Integer> body,
                                             @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        Integer quantity = body.get("quantity");
        if (quantity == null || quantity <= 0) {
            throw new TicketServiceException("Aantal is verplicht en moet positief zijn", 400);
        }
        physicalTicketService.markPhysicalTicketsSold(id, quantity);
        return eventService.getEvent(id);
    }

    @PUT
    @Path("/{id}/physical-tickets/sold-count")
    public EventDTO adjustPhysicalTicketsSold(@PathParam("id") Long id, Map<String, Integer> body,
                                               @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        Integer count = body.get("count");
        if (count == null) {
            throw new TicketServiceException("Aantal is verplicht", 400);
        }
        physicalTicketService.adjustPhysicalTicketsSold(id, count);
        return eventService.getEvent(id);
    }

    @GET
    @Path("/{id}/sales")
    public TicketSalesDTO getTicketSales(@PathParam("id") Long id,
                                          @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return eventService.getTicketSales(id);
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
                dto.location(), dto.address(), dto.maxTickets(), dto.physicalTickets(),
                dto.ticketPrice(), dto.serviceFee(), dto.effectiveOnlineServiceFee(),
                dto.maxTicketsPerOrder(), dto.onlineTickets(),
                dto.ticketsSold(), dto.ticketsReserved(),
                dto.availableTickets(), dto.physicalTicketsSold(),
                dto.availablePhysicalTickets(), dto.totalSold(),
                dto.physicalTicketsGenerated(), dto.showAvailability(),
                dto.imageUrl(), dto.status(),
                customer.id, customer.companyName,
                dto.ticketCategories()
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

    // =========================================================================
    // Customer: Physical tickets & Sales insight
    // =========================================================================

    @POST
    @Path("/my/{id}/physical-tickets/generate")
    @Produces("application/pdf")
    public Response generateMyPhysicalTickets(@PathParam("id") Long id,
                                               @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        byte[] pdf = physicalTicketService.generatePhysicalTickets(id);
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"fysieke-tickets-" + id + ".pdf\"")
                .build();
    }

    @GET
    @Path("/my/{id}/physical-tickets/pdf")
    @Produces("application/pdf")
    public Response downloadMyPhysicalTicketsPdf(@PathParam("id") Long id,
                                                  @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        byte[] pdf = physicalTicketService.getPhysicalTicketsPdf(id);
        return Response.ok(pdf)
                .header("Content-Disposition", "attachment; filename=\"fysieke-tickets-" + id + ".pdf\"")
                .build();
    }

    @POST
    @Path("/my/{id}/physical-tickets/sell")
    public EventDTO markMyPhysicalTicketsSold(@PathParam("id") Long id, Map<String, Integer> body,
                                               @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        Integer quantity = body.get("quantity");
        if (quantity == null || quantity <= 0) {
            throw new TicketServiceException("Aantal is verplicht en moet positief zijn", 400);
        }
        physicalTicketService.markPhysicalTicketsSold(id, quantity);
        return eventService.getEvent(id);
    }

    @PUT
    @Path("/my/{id}/physical-tickets/sold-count")
    public EventDTO adjustMyPhysicalTicketsSold(@PathParam("id") Long id, Map<String, Integer> body,
                                                 @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        Integer count = body.get("count");
        if (count == null) {
            throw new TicketServiceException("Aantal is verplicht", 400);
        }
        physicalTicketService.adjustPhysicalTicketsSold(id, count);
        return eventService.getEvent(id);
    }

    @GET
    @Path("/my/{id}/sales")
    public TicketSalesDTO getMyTicketSales(@PathParam("id") Long id,
                                            @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        return eventService.getTicketSales(id);
    }

    // =========================================================================
    // Ticket Categories — manage per event (customer + admin)
    // =========================================================================

    @GET
    @Path("/{id}/categories")
    public List<TicketCategoryDTO> getCategories(@PathParam("id") Long id) {
        return eventService.getTicketCategories(id);
    }

    @POST
    @Path("/my/{id}/categories")
    public TicketCategoryDTO createMyCategory(@PathParam("id") Long id, @Valid TicketCategoryDTO dto,
                                               @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        return eventService.createTicketCategory(id, dto);
    }

    @PUT
    @Path("/my/{id}/categories/{categoryId}")
    public TicketCategoryDTO updateMyCategory(@PathParam("id") Long id, @PathParam("categoryId") Long categoryId,
                                               @Valid TicketCategoryDTO dto,
                                               @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        return eventService.updateTicketCategory(id, categoryId, dto);
    }

    @DELETE
    @Path("/my/{id}/categories/{categoryId}")
    public Response deleteMyCategory(@PathParam("id") Long id, @PathParam("categoryId") Long categoryId,
                                      @HeaderParam("Authorization") String authHeader) {
        Customer customer = customerAuthService.requireCustomer(authHeader);
        requireOwnership(id, customer);
        eventService.deleteTicketCategory(id, categoryId);
        return Response.noContent().build();
    }

    // Admin category management
    @POST
    @Path("/{id}/categories")
    public TicketCategoryDTO createCategory(@PathParam("id") Long id, @Valid TicketCategoryDTO dto,
                                             @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return eventService.createTicketCategory(id, dto);
    }

    @PUT
    @Path("/{id}/categories/{categoryId}")
    public TicketCategoryDTO updateCategory(@PathParam("id") Long id, @PathParam("categoryId") Long categoryId,
                                             @Valid TicketCategoryDTO dto,
                                             @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return eventService.updateTicketCategory(id, categoryId, dto);
    }

    @DELETE
    @Path("/{id}/categories/{categoryId}")
    public Response deleteCategory(@PathParam("id") Long id, @PathParam("categoryId") Long categoryId,
                                    @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        eventService.deleteTicketCategory(id, categoryId);
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
