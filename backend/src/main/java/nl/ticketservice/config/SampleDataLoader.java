package nl.ticketservice.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.EventStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApplicationScoped
public class SampleDataLoader {

    @ConfigProperty(name = "quarkus.hibernate-orm.database.generation", defaultValue = "none")
    String dbGeneration;

    @Transactional
    public void onStartup(@Observes StartupEvent event) {
        if (!"drop-and-create".equals(dbGeneration)) {
            return;
        }

        // Customer 1
        Customer customer1 = new Customer();
        customer1.companyName = "Festival Events BV";
        customer1.contactPerson = "Jan de Vries";
        customer1.email = "info@festivalevents.nl";
        customer1.phone = "+31 6 12345678";
        customer1.primaryColor = "#E74C3C";
        customer1.secondaryColor = "#2C3E50";
        customer1.website = "https://festivalevents.nl";
        customer1.active = true;
        customer1.persist();

        // Customer 2
        Customer customer2 = new Customer();
        customer2.companyName = "Nachtleven Productions";
        customer2.contactPerson = "Sophie Bakker";
        customer2.email = "info@nachtleven.nl";
        customer2.phone = "+31 6 98765432";
        customer2.primaryColor = "#9B59B6";
        customer2.secondaryColor = "#1ABC9C";
        customer2.website = "https://nachtleven.nl";
        customer2.active = true;
        customer2.persist();

        // Events
        Event event1 = new Event();
        event1.name = "Zomerfestival 2026";
        event1.description = "Het grootste zomerfestival van Nederland met live muziek, food trucks en entertainment voor het hele gezin!";
        event1.eventDate = LocalDateTime.of(2026, 7, 15, 14, 0);
        event1.endDate = LocalDateTime.of(2026, 7, 15, 23, 0);
        event1.location = "Vondelpark, Amsterdam";
        event1.address = "Vondelpark 1, 1071 AA Amsterdam";
        event1.maxTickets = 500;
        event1.ticketPrice = new BigDecimal("35.00");
        event1.serviceFee = new BigDecimal("3.50");
        event1.maxTicketsPerOrder = 10;
        event1.status = EventStatus.PUBLISHED;
        event1.customer = customer1;
        event1.persist();

        Event event2 = new Event();
        event2.name = "Neon Night Party";
        event2.description = "De meest spectaculaire neon party van het jaar! DJ's, lichtshows en een onvergetelijke nacht.";
        event2.eventDate = LocalDateTime.of(2026, 8, 22, 22, 0);
        event2.endDate = LocalDateTime.of(2026, 8, 23, 5, 0);
        event2.location = "Club Nova, Rotterdam";
        event2.address = "Witte de Withstraat 50, 3012 BR Rotterdam";
        event2.maxTickets = 200;
        event2.ticketPrice = new BigDecimal("25.00");
        event2.serviceFee = new BigDecimal("2.50");
        event2.maxTicketsPerOrder = 5;
        event2.status = EventStatus.PUBLISHED;
        event2.customer = customer2;
        event2.persist();

        Event event3 = new Event();
        event3.name = "Oktoberfest Deluxe";
        event3.description = "Authentiek Oktoberfest met Duits bier, bratwurst en live oompah muziek!";
        event3.eventDate = LocalDateTime.of(2026, 10, 3, 16, 0);
        event3.endDate = LocalDateTime.of(2026, 10, 3, 23, 30);
        event3.location = "Jaarbeurs, Utrecht";
        event3.address = "Jaarbeursplein 6, 3521 AL Utrecht";
        event3.maxTickets = 1000;
        event3.ticketPrice = new BigDecimal("45.00");
        event3.serviceFee = new BigDecimal("4.50");
        event3.maxTicketsPerOrder = 8;
        event3.status = EventStatus.PUBLISHED;
        event3.customer = customer1;
        event3.persist();
    }
}
