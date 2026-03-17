package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.ticketservice.dto.EventDTO;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.EventStatus;
import nl.ticketservice.exception.TicketServiceException;

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
        return Event.<Event>list("status", EventStatus.PUBLISHED).stream()
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

    private void updateEntity(Event event, EventDTO dto) {
        event.name = dto.name();
        event.description = dto.description();
        event.eventDate = dto.eventDate();
        event.endDate = dto.endDate();
        event.location = dto.location();
        event.address = dto.address();
        event.maxTickets = dto.maxTickets();
        event.ticketPrice = dto.ticketPrice();
        if (dto.maxTicketsPerOrder() != null) {
            event.maxTicketsPerOrder = dto.maxTicketsPerOrder();
        }
        event.imageUrl = dto.imageUrl();
        if (dto.status() != null) {
            event.status = EventStatus.valueOf(dto.status());
        }
    }

    public EventDTO toDTO(Event e) {
        return new EventDTO(
                e.id, e.name, e.description, e.eventDate, e.endDate,
                e.location, e.address, e.maxTickets, e.ticketPrice,
                e.maxTicketsPerOrder, e.ticketsSold, e.ticketsReserved,
                e.getAvailableTickets(), e.imageUrl, e.status.name(),
                e.customer.id, e.customer.companyName
        );
    }
}
