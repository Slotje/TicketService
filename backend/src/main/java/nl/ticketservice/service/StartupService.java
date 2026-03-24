package nl.ticketservice.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.AdminUser;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.EventStatus;
import nl.ticketservice.entity.ScannerUser;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@ApplicationScoped
public class StartupService {

    private static final Logger LOG = Logger.getLogger(StartupService.class);

    @Inject
    AuthService authService;

    @Inject
    AdminAuthService adminAuthService;

    @ConfigProperty(name = "quarkus.hibernate-orm.database.generation", defaultValue = "none")
    String dbGeneration;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        // Always ensure a default admin exists
        if (AdminUser.count() == 0) {
            adminAuthService.createUser("admin@ticketservice.nl", "admin", "Beheerder");
            LOG.info("Standaard admin gebruiker aangemaakt (admin@ticketservice.nl / admin)");
        }

        if (!"drop-and-create".equals(dbGeneration)) {
            return;
        }

        LOG.info("Dev modus: seed data laden...");

        if (ScannerUser.count() == 0) {
            authService.createUser("scanner", "scanner123", "Standaard Scanner");
            LOG.info("Standaard scanner gebruiker aangemaakt (scanner / scanner123)");
        }

        if (Customer.count() == 0) {
            seedTestData();
            LOG.info("Testdata geladen: klanten en evenementen");
        }
    }

    private void seedTestData() {
        // --- Klant 1: Feestfabriek ---
        Customer feestfabriek = new Customer();
        feestfabriek.companyName = "De Feestfabriek";
        feestfabriek.contactPerson = "Jan de Vries";
        feestfabriek.email = "info@feestfabriek.nl";
        feestfabriek.phone = "06-12345678";
        feestfabriek.primaryColor = "#FF6B00";
        feestfabriek.secondaryColor = "#1A1A2E";
        feestfabriek.website = "https://feestfabriek.nl";
        feestfabriek.slug = "feestfabriek";
        feestfabriek.active = true;
        feestfabriek.persist();

        createEvent(feestfabriek, "Zomerfestival 2026", "Het grootste zomerfestival van Nederland! "
                + "Met live muziek, foodtrucks en activiteiten voor het hele gezin.",
                LocalDateTime.of(2026, 7, 12, 14, 0),
                LocalDateTime.of(2026, 7, 12, 23, 0),
                "Stadspark Amsterdam", "Stadspark 1, 1012 AB Amsterdam",
                500, 50, new BigDecimal("27.50"), new BigDecimal("2.50"), 5, EventStatus.PUBLISHED);

        createEvent(feestfabriek, "Nacht van de Muziek", "Een onvergetelijke avond met de beste DJ's en live acts.",
                LocalDateTime.of(2026, 8, 22, 21, 0),
                LocalDateTime.of(2026, 8, 23, 4, 0),
                "Club Neon, Rotterdam", "Wijnhaven 100, 3011 WN Rotterdam",
                300, 0, new BigDecimal("35.00"), new BigDecimal("3.00"), 4, EventStatus.PUBLISHED);

        createEvent(feestfabriek, "Oktoberfest Deluxe", "Proost op de herfst met bier, bratwurst en gezelligheid!",
                LocalDateTime.of(2026, 10, 3, 16, 0),
                LocalDateTime.of(2026, 10, 3, 23, 30),
                "Evenementenhal Breda", "Hallweg 5, 4811 CA Breda",
                800, 100, new BigDecimal("22.50"), new BigDecimal("2.00"), 8, EventStatus.DRAFT);

        // --- Klant 2: SportEvents BV ---
        Customer sportEvents = new Customer();
        sportEvents.companyName = "SportEvents BV";
        sportEvents.contactPerson = "Lisa Bakker";
        sportEvents.email = "contact@sportevents.nl";
        sportEvents.phone = "06-98765432";
        sportEvents.primaryColor = "#00B4D8";
        sportEvents.secondaryColor = "#023E8A";
        sportEvents.website = "https://sportevents.nl";
        sportEvents.slug = "sportevents";
        sportEvents.active = true;
        sportEvents.persist();

        createEvent(sportEvents, "Utrecht City Run 10K", "Hardloopevenement door het hart van Utrecht. "
                + "Geschikt voor beginners en gevorderden.",
                LocalDateTime.of(2026, 5, 17, 9, 0),
                LocalDateTime.of(2026, 5, 17, 14, 0),
                "Jaarbeursplein Utrecht", "Jaarbeursplein 1, 3521 AL Utrecht",
                2000, 200, new BigDecimal("15.00"), new BigDecimal("1.50"), 5, EventStatus.PUBLISHED);

        createEvent(sportEvents, "Beach Volleyball Toernooi", "Zomertoernooi op het strand van Scheveningen.",
                LocalDateTime.of(2026, 6, 28, 10, 0),
                LocalDateTime.of(2026, 6, 28, 18, 0),
                "Strand Scheveningen", "Strandweg 1, 2586 JK Den Haag",
                150, 0, new BigDecimal("12.50"), new BigDecimal("1.00"), 2, EventStatus.PUBLISHED);

        // --- Klant 3: Cultuurhuis De Boog ---
        Customer cultuurhuis = new Customer();
        cultuurhuis.companyName = "Cultuurhuis De Boog";
        cultuurhuis.contactPerson = "Maria van den Berg";
        cultuurhuis.email = "info@cultuurhuisdeboog.nl";
        cultuurhuis.phone = "030-2345678";
        cultuurhuis.primaryColor = "#9B2335";
        cultuurhuis.secondaryColor = "#2D2D2D";
        cultuurhuis.website = "https://cultuurhuisdeboog.nl";
        cultuurhuis.slug = "cultuurhuis-de-boog";
        cultuurhuis.active = true;
        cultuurhuis.persist();

        createEvent(cultuurhuis, "Theatervoorstelling: De Storm", "Een meeslepende theatervoorstelling "
                + "gebaseerd op het werk van Shakespeare.",
                LocalDateTime.of(2026, 4, 18, 20, 0),
                LocalDateTime.of(2026, 4, 18, 22, 30),
                "Cultuurhuis De Boog", "Boogstraat 12, 3511 XA Utrecht",
                120, 20, new BigDecimal("19.50"), new BigDecimal("1.50"), 4, EventStatus.PUBLISHED);

        createEvent(cultuurhuis, "Stand-up Comedy Avond", "Lach je een avond lang suf met de beste comedians van NL.",
                LocalDateTime.of(2026, 5, 9, 20, 30),
                LocalDateTime.of(2026, 5, 9, 23, 0),
                "Cultuurhuis De Boog", "Boogstraat 12, 3511 XA Utrecht",
                80, 0, new BigDecimal("14.00"), new BigDecimal("1.00"), 6, EventStatus.PUBLISHED);

        createEvent(cultuurhuis, "Kunstexpositie: Licht & Schaduw", "Gratis toegang tot de jaarlijkse kunstexpositie.",
                LocalDateTime.of(2026, 9, 1, 10, 0),
                LocalDateTime.of(2026, 9, 14, 17, 0),
                "Cultuurhuis De Boog", "Boogstraat 12, 3511 XA Utrecht",
                1000, 0, new BigDecimal("0.00"), new BigDecimal("0.00"), 10, EventStatus.DRAFT);

        // --- Klant 4: TechConf Nederland (inactief) ---
        Customer techConf = new Customer();
        techConf.companyName = "TechConf Nederland";
        techConf.contactPerson = "Pieter Smit";
        techConf.email = "pieter@techconf.nl";
        techConf.phone = "06-55512345";
        techConf.primaryColor = "#6C63FF";
        techConf.secondaryColor = "#F5F5F5";
        techConf.website = "https://techconf.nl";
        techConf.slug = "techconf";
        techConf.active = false;
        techConf.persist();

        createEvent(techConf, "DevDays 2026", "Twee dagen vol talks over AI, cloud en software development.",
                LocalDateTime.of(2026, 11, 14, 9, 0),
                LocalDateTime.of(2026, 11, 15, 17, 0),
                "RAI Amsterdam", "Europaplein 24, 1078 GZ Amsterdam",
                1500, 0, new BigDecimal("149.00"), new BigDecimal("5.00"), 3, EventStatus.DRAFT);
    }

    private void createEvent(Customer customer, String name, String description,
                             LocalDateTime startDate, LocalDateTime endDate,
                             String location, String address,
                             int maxTickets, int physicalTickets,
                             BigDecimal price, BigDecimal serviceFee,
                             int maxPerOrder, EventStatus status) {
        Event event = new Event();
        event.name = name;
        event.description = description;
        event.eventDate = startDate;
        event.endDate = endDate;
        event.location = location;
        event.address = address;
        event.maxTickets = maxTickets;
        event.physicalTickets = physicalTickets;
        event.ticketPrice = price;
        event.serviceFee = serviceFee;
        event.maxTicketsPerOrder = maxPerOrder;
        event.status = status;
        event.customer = customer;
        event.persist();
    }
}
