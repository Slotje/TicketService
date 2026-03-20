package nl.ticketservice.service;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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

    @Inject
    EntityManager em;

    @ConfigProperty(name = "quarkus.hibernate-orm.database.generation", defaultValue = "none")
    String dbGeneration;

    @Transactional
    void onStart(@Observes StartupEvent ev) {
        migrateAdminUsersTable();

        if ("drop-and-create".equals(dbGeneration)) {
            if (ScannerUser.count() == 0) {
                authService.createUser("scanner", "scanner123", "Standaard Scanner");
                LOG.info("Standaard scanner gebruiker aangemaakt (scanner / scanner123)");
            }
        }

        // Always ensure at least one admin user exists
        if (AdminUser.count() == 0) {
            adminAuthService.createUser("admin@ticketservice.nl", "admin", "Beheerder");
            LOG.info("Standaard admin gebruiker aangemaakt (admin@ticketservice.nl / admin)");
        }
    }

    private void migrateAdminUsersTable() {
        try {
            // Check if old 'username' column still exists and migrate to 'email'
            var result = em.createNativeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'admin_users' AND column_name = 'username'")
                    .getResultList();

            if (!result.isEmpty()) {
                LOG.info("Migratie: admin_users tabel heeft nog 'username' kolom, migreren naar 'email'...");

                // Add email column if it doesn't exist yet
                var emailCol = em.createNativeQuery(
                        "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'admin_users' AND column_name = 'email'")
                        .getResultList();

                if (emailCol.isEmpty()) {
                    em.createNativeQuery("ALTER TABLE admin_users ADD COLUMN email VARCHAR(255)").executeUpdate();
                }

                // Copy username to email for existing rows that don't have email set
                em.createNativeQuery("UPDATE admin_users SET email = username WHERE email IS NULL").executeUpdate();

                // Drop the old username column
                em.createNativeQuery("ALTER TABLE admin_users DROP COLUMN username").executeUpdate();

                // Clear all existing admin users (password hash may be invalid due to old schema)
                em.createNativeQuery("DELETE FROM admin_users").executeUpdate();

                LOG.info("Migratie: admin_users tabel gemigreerd naar email-gebaseerd");
            }
        } catch (Exception e) {
            LOG.warn("Migratie admin_users overgeslagen: " + e.getMessage());
        }
    }
}
