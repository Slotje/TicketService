package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.CustomerDTO;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.exception.TicketServiceException;

import java.util.List;

@ApplicationScoped
public class CustomerService {

    public List<CustomerDTO> getAllCustomers() {
        return Customer.<Customer>listAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public CustomerDTO getCustomer(Long id) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new TicketServiceException("Klant niet gevonden", 404);
        }
        return toDTO(customer);
    }

    @Transactional
    public CustomerDTO createCustomer(CustomerDTO dto) {
        if (Customer.find("email", dto.email()).firstResult() != null) {
            throw new TicketServiceException("E-mailadres is al in gebruik", 409);
        }

        Customer customer = new Customer();
        updateEntity(customer, dto);
        customer.persist();
        return toDTO(customer);
    }

    @Transactional
    public CustomerDTO updateCustomer(Long id, CustomerDTO dto) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new TicketServiceException("Klant niet gevonden", 404);
        }

        Customer existing = Customer.find("email", dto.email()).firstResult();
        if (existing != null && !existing.id.equals(id)) {
            throw new TicketServiceException("E-mailadres is al in gebruik door een andere klant", 409);
        }

        updateEntity(customer, dto);
        return toDTO(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new TicketServiceException("Klant niet gevonden", 404);
        }
        if (!customer.events.isEmpty()) {
            throw new TicketServiceException("Klant kan niet worden verwijderd: er zijn nog evenementen gekoppeld", 409);
        }
        customer.delete();
    }

    private void updateEntity(Customer customer, CustomerDTO dto) {
        customer.companyName = dto.companyName();
        customer.contactPerson = dto.contactPerson();
        customer.email = dto.email();
        customer.phone = dto.phone();
        customer.logoUrl = dto.logoUrl();
        customer.primaryColor = dto.primaryColor();
        customer.secondaryColor = dto.secondaryColor();
        customer.website = dto.website();
        customer.active = dto.active();
    }

    private CustomerDTO toDTO(Customer c) {
        return new CustomerDTO(
                c.id, c.companyName, c.contactPerson, c.email, c.phone,
                c.logoUrl, c.primaryColor, c.secondaryColor, c.website, c.active
        );
    }
}
