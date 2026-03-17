package nl.ticketservice.resource;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.ticketservice.dto.CustomerDTO;
import nl.ticketservice.service.CustomerService;

import java.util.List;

@Path("/api/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerResource {

    @Inject
    CustomerService customerService;

    @GET
    public List<CustomerDTO> getAll() {
        return customerService.getAllCustomers();
    }

    @GET
    @Path("/{id}")
    public CustomerDTO getById(@PathParam("id") Long id) {
        return customerService.getCustomer(id);
    }

    @POST
    public Response create(@Valid CustomerDTO dto) {
        CustomerDTO created = customerService.createCustomer(dto);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public CustomerDTO update(@PathParam("id") Long id, @Valid CustomerDTO dto) {
        return customerService.updateCustomer(id, dto);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        customerService.deleteCustomer(id);
        return Response.noContent().build();
    }
}
