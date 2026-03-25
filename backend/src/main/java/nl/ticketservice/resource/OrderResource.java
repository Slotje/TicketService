package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.BuyerDetailsDTO;
import nl.ticketservice.dto.OrderRequestDTO;
import nl.ticketservice.dto.OrderResponseDTO;
import nl.ticketservice.dto.TicketDTO;
import nl.ticketservice.exception.TicketServiceException;
import nl.ticketservice.service.AdminAuthService;
import nl.ticketservice.service.CustomerAuthService;
import nl.ticketservice.service.OrderService;
import nl.ticketservice.service.PdfService;
import nl.ticketservice.service.QrCodeService;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.TicketOrder;

import java.util.List;

@Path("/api/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {

    @Inject
    OrderService orderService;

    @Inject
    PdfService pdfService;

    @Inject
    QrCodeService qrCodeService;

    @Inject
    nl.ticketservice.service.AuthService authService;

    @Inject
    AdminAuthService adminAuthService;

    @Inject
    CustomerAuthService customerAuthService;

    @GET
    @Path("/event/{eventId}")
    public List<OrderResponseDTO> getByEvent(@PathParam("eventId") Long eventId,
                                             @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return orderService.getOrdersByEvent(eventId);
    }

    @GET
    @Path("/{id}")
    public OrderResponseDTO getById(@PathParam("id") Long id) {
        return orderService.getOrder(id);
    }

    @GET
    @Path("/number/{orderNumber}")
    public OrderResponseDTO getByNumber(@PathParam("orderNumber") String orderNumber) {
        return orderService.getOrderByNumber(orderNumber);
    }

    @GET
    @Path("/email/{email}")
    public List<OrderResponseDTO> getByEmail(@PathParam("email") String email) {
        return orderService.getOrdersByEmail(email);
    }

    @POST
    public OrderResponseDTO create(@Valid OrderRequestDTO dto) {
        return orderService.createOrder(dto);
    }

    @PUT
    @Path("/{id}/details")
    public OrderResponseDTO updateDetails(@PathParam("id") Long id, @Valid BuyerDetailsDTO dto) {
        return orderService.updateBuyerDetails(id, dto);
    }

    @POST
    @Path("/{id}/confirm")
    public OrderResponseDTO confirm(@PathParam("id") Long id) {
        return orderService.confirmOrder(id);
    }

    @POST
    @Path("/{id}/cancel")
    public OrderResponseDTO cancel(@PathParam("id") Long id) {
        return orderService.cancelOrder(id);
    }

    @GET
    @Path("/{id}/pdf")
    @Produces("application/pdf")
    public Response downloadPdf(@PathParam("id") Long id) {
        TicketOrder order = TicketOrder.findById(id);
        if (order == null) {
            throw new TicketServiceException("Bestelling niet gevonden", 404);
        }

        byte[] pdfBytes = pdfService.generateOrderPdf(order);

        return Response.ok(pdfBytes)
                .header("Content-Disposition", "attachment; filename=\"tickets-" + order.orderNumber + ".pdf\"")
                .build();
    }

    @GET
    @Path("/ticket/{qrCodeData}/qr")
    @Produces("image/png")
    public Response getQrCode(@PathParam("qrCodeData") String qrCodeData) {
        byte[] qrImage = qrCodeService.generateQrCodeImage(qrCodeData);
        return Response.ok(qrImage).build();
    }

    @POST
    @Path("/scan/{qrCodeData}")
    public TicketDTO scanTicket(@PathParam("qrCodeData") String qrCodeData,
                                @QueryParam("eventId") Long eventId,
                                @HeaderParam("Authorization") String authHeader) {
        // Verify scanner user is authenticated
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7) : null;
        if (token == null || authService.validateToken(token) == null) {
            throw new TicketServiceException("Niet geautoriseerd. Log in als scanner gebruiker.", 401);
        }
        return orderService.scanTicket(qrCodeData, eventId);
    }

    // =========================================================================
    // Payment endpoints
    // =========================================================================

    @POST
    @Path("/{id}/pay")
    public OrderResponseDTO initiatePayment(@PathParam("id") Long id) {
        return orderService.initiatePayment(id);
    }

    @POST
    @Path("/webhooks/mollie")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response mollieWebhook(@FormParam("id") String paymentId) {
        try {
            if (paymentId != null && !paymentId.isBlank()) {
                orderService.completePayment(paymentId);
            }
        } catch (Exception e) {
            // Always return 200 to Mollie, even on errors (Mollie requirement)
            // Log the error for debugging
        }
        return Response.ok().build();
    }

    @POST
    @Path("/{id}/refund")
    public OrderResponseDTO refund(@PathParam("id") Long id,
                                   @HeaderParam("Authorization") String authHeader) {
        // Require admin or customer auth
        try {
            adminAuthService.requireAdmin(authHeader);
        } catch (Exception adminFail) {
            try {
                Customer customer = customerAuthService.requireCustomer(authHeader);
                // Customer can only refund orders for their own events
                TicketOrder order = TicketOrder.findById(id);
                if (order == null || !order.event.customer.id.equals(customer.id)) {
                    throw new TicketServiceException("Geen toegang tot deze bestelling", 403);
                }
            } catch (TicketServiceException e) {
                throw e;
            } catch (Exception customerFail) {
                throw new TicketServiceException("Niet geautoriseerd", 401);
            }
        }
        return orderService.refundOrder(id);
    }
}
