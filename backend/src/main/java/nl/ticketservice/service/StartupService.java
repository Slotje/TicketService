package nl.ticketservice.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.AdminUser;
import nl.ticketservice.entity.ScannerUser;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

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
        if ("drop-and-create".equals(dbGeneration)) {
            if (ScannerUser.count() == 0) {
                authService.createUser("scanner", "scanner123", "Standaard Scanner");
                LOG.info("Standaard scanner gebruiker aangemaakt (scanner / scanner123)");
            }
            if (AdminUser.count() == 0) {
                adminAuthService.createUser("admin", "admin", "Beheerder");
                LOG.info("Standaard admin gebruiker aangemaakt (admin / admin)");
            }
        }

        // Always ensure at least one admin user exists in production
        if (AdminUser.count() == 0) {
            adminAuthService.createUser("admin", "admin", "Beheerder");
            LOG.info("Standaard admin gebruiker aangemaakt (admin / admin)");
        }
    }
}
