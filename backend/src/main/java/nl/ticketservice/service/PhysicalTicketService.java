package nl.ticketservice.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.*;
import nl.ticketservice.exception.TicketServiceException;

import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PhysicalTicketService {

    private static final Logger LOG = Logger.getLogger(PhysicalTicketService.class);

    @Inject
    PhysicalTicketPdfService physicalTicketPdfService;

    @Inject
    EmailService emailService;

    /**
     * Generate all physical tickets for an event.
     * Creates Ticket entities with type PHYSICAL (no order, linked directly to event).
     * Generates printable PDF and emails it to the customer.
     */
    @Transactional
    public byte[] generatePhysicalTickets(Long eventId) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (event.physicalTickets <= 0) {
            throw new TicketServiceException("Dit evenement heeft geen fysieke tickets geconfigureerd", 400);
        }

        if (event.physicalTicketsGenerated) {
            throw new TicketServiceException("Fysieke tickets zijn al gegenereerd voor dit evenement", 400);
        }

        // Create physical ticket entities
        List<Ticket> physicalTickets = new ArrayList<>();
        for (int i = 0; i < event.physicalTickets; i++) {
            Ticket ticket = new Ticket();
            ticket.event = event;
            ticket.ticketType = TicketType.PHYSICAL;
            // No order - physical tickets are standalone
            ticket.persist();
            physicalTickets.add(ticket);
        }

        event.physicalTicketsGenerated = true;

        // Generate the printable PDF
        byte[] pdfBytes = physicalTicketPdfService.generatePhysicalTicketsPdf(event, physicalTickets);

        // Send PDF to customer email
        emailService.sendPhysicalTicketsPdf(event, pdfBytes);

        LOG.infof("Generated %d physical tickets for event '%s' (ID: %d)",
                event.physicalTickets, event.name, event.id);

        return pdfBytes;
    }

    /**
     * Mark physical tickets as sold. Used when physical tickets are sold at the door
     * or through offline channels. This updates the sales count for live tracking.
     */
    @Transactional
    public void markPhysicalTicketsSold(Long eventId, int quantity) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (quantity <= 0) {
            throw new TicketServiceException("Aantal moet positief zijn", 400);
        }

        int available = event.getAvailablePhysicalTickets();
        if (quantity > available) {
            throw new TicketServiceException(
                    "Slechts " + available + " fysieke tickets beschikbaar om te markeren als verkocht", 400);
        }

        event.physicalTicketsSold += quantity;

        LOG.infof("Marked %d physical tickets as sold for event '%s' (total physical sold: %d/%d)",
                quantity, event.name, event.physicalTicketsSold, event.physicalTickets);
    }

    /**
     * Adjust physical tickets sold count (e.g., correct mistakes).
     */
    @Transactional
    public void adjustPhysicalTicketsSold(Long eventId, int newCount) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (newCount < 0) {
            throw new TicketServiceException("Aantal verkochte fysieke tickets kan niet negatief zijn", 400);
        }

        if (newCount > event.physicalTickets) {
            throw new TicketServiceException(
                    "Aantal verkochte fysieke tickets (" + newCount + ") kan niet meer zijn dan totaal (" + event.physicalTickets + ")", 400);
        }

        event.physicalTicketsSold = newCount;
    }

    /**
     * Get the physical tickets for an event (for re-downloading PDF).
     */
    public byte[] getPhysicalTicketsPdf(Long eventId) {
        Event event = Event.findById(eventId);
        if (event == null) {
            throw new TicketServiceException("Evenement niet gevonden", 404);
        }

        if (!event.physicalTicketsGenerated) {
            throw new TicketServiceException("Fysieke tickets zijn nog niet gegenereerd", 400);
        }

        List<Ticket> physicalTickets = Ticket.list("event.id = ?1 and ticketType = ?2",
                eventId, TicketType.PHYSICAL);

        return physicalTicketPdfService.generatePhysicalTicketsPdf(event, physicalTickets);
    }
}
