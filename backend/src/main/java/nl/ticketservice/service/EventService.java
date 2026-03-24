package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.dto.TicketCategoryDTO;
import nl.ticketservice.dto.TicketSalesDTO;
import nl.ticketservice.entity.*;
import nl.ticketservice.exception.TicketServiceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EventService {

    public List<EventDTO> getAllEvents() {
        return Event.<Event>listAll().stream()
                .map(this::toDTO)
                .toList();
    }

    public List<EventDTO> getEventsByCustomer(Long customerId) {
        return Event.<Event>list("customer.id", customerId).stream()
                .map(this::toDTO)
                .toList();
    }

    public List<EventDTO> getPublishedEvents() {
        return Event.<Event>list("status in ?1 and eventDate > ?2",
                        List.of(EventStatus.PUBLISHED, EventStatus.SOLD_OUT),
                        LocalDateTime.now()).stream()
                .map(this::toDTO)
                .toList();
    }

    public EventDTO getEvent(Long id) {
        Event event = Event.findById(id);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }
        return toDTO(event);
    }

    @Transactional
    public EventDTO createEvent(EventDTO dto) {
        Customer customer = Customer.findById(dto.customerId());
        if (customer == null) {
            throw new TicketServiceException("Klant niet gevonden", 404);
        }

        Event event = new Event();
        updateEntity(event, dto);
        event.customer = customer;
        event.persist();
        return toDTO(event);
    }

    @Transactional
    public EventDTO updateEvent(Long id, EventDTO dto) {
        Event event = Event.findById(id);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (dto.maxTickets() != null && dto.maxTickets() < event.ticketsSold) {
            throw new TicketServiceException(
                    "Maximaal aantal tickets kan niet lager zijn dan het aantal al verkochte tickets (" + event.ticketsSold + ")",
                    400
            );
        }

        updateEntity(event, dto);
        return toDTO(event);
    }

    @Transactional
    public EventDTO updateEventStatus(Long id, String statusStr) {
        Event event = Event.findById(id);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        try {
            EventStatus newStatus = EventStatus.valueOf(statusStr.toUpperCase());
            event.status = newStatus;
        } catch (IllegalArgumentException e) {
            throw new TicketServiceException("Ongeldige status: " + statusStr, 400);
        }

        return toDTO(event);
    }

    @Transactional
    public void deleteEvent(Long id) {
        Event event = Event.findById(id);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }
        if (event.ticketsSold > 0) {
            throw new TicketServiceException("Evenement kan niet worden verwijderd: er zijn al tickets verkocht", 409);
        }
        event.delete();
    }

    public TicketSalesDTO getTicketSales(Long eventId) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        int onlineTickets = event.getOnlineTickets();
        int totalSold = event.getTotalSold();
        int totalRemaining = event.maxTickets - totalSold - event.ticketsReserved;

        // Count scanned tickets
        long scanned = Ticket.count("event.id = ?1 and scanned = true", eventId);
        long totalTicketEntities = Ticket.count("event.id = ?1", eventId);

        // Revenue calculations
        BigDecimal onlineRevenue = event.ticketPrice.multiply(BigDecimal.valueOf(event.ticketsSold));
        BigDecimal physicalRevenue = event.ticketPrice.multiply(BigDecimal.valueOf(event.physicalTicketsSold));
        BigDecimal effectiveFee = event.getEffectiveOnlineServiceFee();
        BigDecimal serviceFeeRevenue = effectiveFee.multiply(BigDecimal.valueOf(event.ticketsSold));
        BigDecimal totalRevenue = onlineRevenue.add(physicalRevenue).add(serviceFeeRevenue);

        return new TicketSalesDTO(
                event.id, event.name, event.status.name(),
                event.maxTickets, event.physicalTickets, onlineTickets,
                totalSold, totalRemaining,
                event.ticketsSold, event.ticketsReserved, event.getAvailableTickets(),
                event.physicalTicketsSold, event.getAvailablePhysicalTickets(),
                event.physicalTicketsGenerated,
                event.ticketPrice, event.serviceFee, effectiveFee,
                onlineRevenue, physicalRevenue, serviceFeeRevenue, totalRevenue,
                (int) scanned, (int) (totalTicketEntities - scanned)
        );
    }

    // =========================================================================
    // Ticket Category management
    // =========================================================================

    public List<TicketCategoryDTO> getTicketCategories(Long eventId) {
        return TicketCategory.findByEvent(eventId).stream()
                .map(this::toCategoryDTO)
                .toList();
    }

    @Transactional
    public TicketCategoryDTO createTicketCategory(Long eventId, TicketCategoryDTO dto) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        TicketCategory category = new TicketCategory();
        category.event = event;
        updateCategoryEntity(category, dto);
        category.persist();
        return toCategoryDTO(category);
    }

    @Transactional
    public TicketCategoryDTO updateTicketCategory(Long eventId, Long categoryId, TicketCategoryDTO dto) {
        TicketCategory category = TicketCategory.findById(categoryId);
        if (category == null || !category.event.id.equals(eventId)) {
            throw new TicketServiceException("Ticket categorie niet gevonden", 404);
        }
        updateCategoryEntity(category, dto);
        return toCategoryDTO(category);
    }

    @Transactional
    public void deleteTicketCategory(Long eventId, Long categoryId) {
        TicketCategory category = TicketCategory.findById(categoryId);
        if (category == null || !category.event.id.equals(eventId)) {
            throw new TicketServiceException("Ticket categorie niet gevonden", 404);
        }
        if (category.ticketsSold > 0) {
            throw new TicketServiceException("Categorie kan niet worden verwijderd: er zijn al tickets verkocht", 409);
        }
        category.delete();
    }

    private void updateCategoryEntity(TicketCategory category, TicketCategoryDTO dto) {
        category.name = dto.name();
        category.description = dto.description();
        category.price = dto.price();
        category.serviceFee = dto.serviceFee();
        if (dto.maxTickets() != null) {
            category.maxTickets = dto.maxTickets();
        }
        category.validDate = dto.validDate();
        category.validEndDate = dto.validEndDate();
        category.startTime = dto.startTime();
        category.endTime = dto.endTime();
        if (dto.sortOrder() != null) {
            category.sortOrder = dto.sortOrder();
        }
        category.active = dto.active();
    }

    private void updateEntity(Event event, EventDTO dto) {
        event.name = dto.name();
        event.description = dto.description();
        event.eventDate = dto.eventDate();
        event.endDate = dto.endDate();
        event.location = dto.location();
        event.address = dto.address();
        event.maxTickets = dto.maxTickets();
        if (dto.physicalTickets() != null) {
            if (dto.physicalTickets() > dto.maxTickets()) {
                throw new TicketServiceException(
                        "Fysieke tickets (" + dto.physicalTickets() + ") kan niet meer zijn dan totaal tickets (" + dto.maxTickets() + ")", 400);
            }
            event.physicalTickets = dto.physicalTickets();
        }
        event.ticketPrice = dto.ticketPrice();
        if (dto.serviceFee() != null) {
            event.serviceFee = dto.serviceFee();
        }
        if (dto.maxTicketsPerOrder() != null) {
            event.maxTicketsPerOrder = dto.maxTicketsPerOrder();
        }
        event.imageUrl = dto.imageUrl();
        if (dto.status() != null) {
            event.status = EventStatus.valueOf(dto.status());
        }
    }

    public EventDTO toDTO(Event e) {
        List<TicketCategoryDTO> categoryDTOs = TicketCategory.findByEvent(e.id).stream()
                .map(this::toCategoryDTO)
                .toList();

        return new EventDTO(
                e.id, e.name, e.description, e.eventDate, e.endDate,
                e.location, e.address, e.maxTickets, e.physicalTickets,
                e.ticketPrice, e.serviceFee, e.getEffectiveOnlineServiceFee(),
                e.maxTicketsPerOrder, e.getOnlineTickets(),
                e.ticketsSold, e.ticketsReserved,
                e.getAvailableTickets(), e.physicalTicketsSold,
                e.getAvailablePhysicalTickets(), e.getTotalSold(),
                e.physicalTicketsGenerated,
                e.imageUrl, e.status.name(),
                e.customer.id, e.customer.companyName,
                categoryDTOs
        );
    }

    private TicketCategoryDTO toCategoryDTO(TicketCategory c) {
        return new TicketCategoryDTO(
                c.id, c.name, c.description, c.price, c.serviceFee,
                c.maxTickets, c.ticketsSold, c.ticketsReserved,
                c.getAvailableTickets(), c.validDate, c.validEndDate, c.startTime, c.endTime,
                c.sortOrder, c.active
        );
    }
}
