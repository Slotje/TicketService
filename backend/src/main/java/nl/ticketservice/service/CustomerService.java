package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.CustomerDTO;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.exception.TicketServiceException;

import java.text.Normalizer;
import java.util.List;

@ApplicationScoped
public class CustomerService {

    @Inject
    CustomerAuthService customerAuthService;

    @Inject
    EmailService emailService;

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

    public CustomerDTO getCustomerBySlug(String slug) {
        Customer customer = Customer.findBySlug(slug);
        if (customer == null || !customer.active) {
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
        customer.slug = generateSlug(dto.companyName());
        customer.persist();

        // Generate invite token and send email
        String inviteToken = customerAuthService.generateInviteToken(customer);
        emailService.sendCustomerInvite(customer, inviteToken);

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

        // Update slug if company name changed
        if (!customer.companyName.equals(dto.companyName())) {
            customer.slug = generateSlug(dto.companyName());
        }

        return toDTO(customer);
    }

    @Transactional
    public void resendInvite(Long id) {
        Customer customer = Customer.findById(id);
        if (customer == null) {
            throw new TicketServiceException("Klant niet gevonden", 404);
        }
        if (customer.passwordHash != null) {
            throw new TicketServiceException("Klant heeft al een wachtwoord ingesteld", 400);
        }
        String inviteToken = customerAuthService.generateInviteToken(customer);
        emailService.sendCustomerInvite(customer, inviteToken);
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

    private String generateSlug(String companyName) {
        String normalized = Normalizer.normalize(companyName, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        // Ensure uniqueness
        String baseSlug = slug;
        int counter = 1;
        while (Customer.findBySlug(slug) != null) {
            slug = baseSlug + "-" + counter++;
        }
        return slug;
    }

    private CustomerDTO toDTO(Customer c) {
        return new CustomerDTO(
                c.id, c.companyName, c.contactPerson, c.email, c.phone,
                c.logoUrl, c.primaryColor, c.secondaryColor, c.website, c.active
        );
    }
}
