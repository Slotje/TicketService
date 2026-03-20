package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.CustomerDTO;
import nl.ticketservice.service.CustomerService;

import nl.ticketservice.service.AdminAuthService;

import java.util.List;

@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerResource {

    @Inject
    CustomerService customerService;

    @Inject
    AdminAuthService adminAuthService;

    @GET
    public List<CustomerDTO> getAll(@HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return customerService.getAllCustomers();
    }

    @GET
    @Path("/{id}")
    public CustomerDTO getById(@PathParam("id") Long id, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return customerService.getCustomer(id);
    }

    @POST
    public Response create(@Valid CustomerDTO dto, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        CustomerDTO created = customerService.createCustomer(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public CustomerDTO update(@PathParam("id") Long id, @Valid CustomerDTO dto,
                              @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        return customerService.updateCustomer(id, dto);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        customerService.deleteCustomer(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/resend-invite")
    public Response resendInvite(@PathParam("id") Long id, @HeaderParam("Authorization") String authHeader) {
        adminAuthService.requireAdmin(authHeader);
        customerService.resendInvite(id);
        return Response.ok().build();
    }

    @GET
    @Path("/slug/{slug}")
    public CustomerDTO getBySlug(@PathParam("slug") String slug) {
        return customerService.getCustomerBySlug(slug);
    }
}
