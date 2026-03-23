package nl.ticketservice.entity;

import nl.ticketservice.entity.*;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EntityTest {

    // --- Event tests ---

    @Test
    void testEventGetAvailableTickets() {
        Event event = new Event();
        event.maxTickets = 100;
        event.ticketsSold = 30;
        event.ticketsReserved = 20;

        assertEquals(50, event.getAvailableTickets());
    }

    @Test
    void testEventHasAvailableTicketsTrue() {
        Event event = new Event();
        event.maxTickets = 100;
        event.ticketsSold = 30;
        event.ticketsReserved = 20;

        assertTrue(event.hasAvailableTickets(50));
    }

    @Test
    void testEventHasAvailableTicketsFalse() {
        Event event = new Event();
        event.maxTickets = 100;
        event.ticketsSold = 30;
        event.ticketsReserved = 20;

        assertFalse(event.hasAvailableTickets(51));
    }

    @Test
    void testEventPrePersist() {
        Event event = new Event();
        assertNull(event.createdAt);
        assertNull(event.updatedAt);

        event.prePersist();

        assertNotNull(event.createdAt);
        assertNotNull(event.updatedAt);
    }

    @Test
    void testEventPreUpdate() {
        Event event = new Event();
        event.prePersist();
        var originalUpdatedAt = event.updatedAt;

        // Ensure some time passes so the timestamp differs
        event.preUpdate();

        assertNotNull(event.updatedAt);
    }

    // --- TicketOrder tests ---

    @Test
    void testTicketOrderPrePersist() {
        TicketOrder order = new TicketOrder();
        assertNull(order.orderNumber);
        assertNull(order.createdAt);

        order.prePersist();

        assertNotNull(order.orderNumber);
        assertTrue(order.orderNumber.startsWith("ORD-"));
        assertNotNull(order.createdAt);
    }

    @Test
    void testTicketOrderPrePersistWithExistingOrderNumber() {
        TicketOrder order = new TicketOrder();
        order.orderNumber = "EXISTING";

        order.prePersist();

        assertEquals("EXISTING", order.orderNumber);
    }

    // --- Ticket tests ---

    @Test
    void testTicketPrePersist() {
        Ticket ticket = new Ticket();
        assertNull(ticket.ticketCode);
        assertNull(ticket.qrCodeData);
        assertNull(ticket.createdAt);

        ticket.prePersist();

        assertNotNull(ticket.ticketCode);
        assertTrue(ticket.ticketCode.startsWith("TKT-"));
        assertNotNull(ticket.qrCodeData);
        assertNotNull(ticket.createdAt);
    }

    @Test
    void testTicketPrePersistWithExistingCodes() {
        Ticket ticket = new Ticket();
        ticket.ticketCode = "MY-CODE";
        ticket.qrCodeData = "MY-QR";

        ticket.prePersist();

        assertEquals("MY-CODE", ticket.ticketCode);
        assertEquals("MY-QR", ticket.qrCodeData);
    }

    // --- Customer tests ---

    @Test
    void testCustomerPrePersist() {
        Customer customer = new Customer();
        assertNull(customer.createdAt);
        assertNull(customer.updatedAt);

        customer.prePersist();

        assertNotNull(customer.createdAt);
        assertNotNull(customer.updatedAt);
    }

    @Test
    void testCustomerPreUpdate() {
        Customer customer = new Customer();
        customer.prePersist();

        customer.preUpdate();

        assertNotNull(customer.updatedAt);
    }

    // --- AdminUser tests ---

    @Test
    void testAdminUserPrePersist() {
        AdminUser adminUser = new AdminUser();
        assertNull(adminUser.createdAt);

        adminUser.prePersist();

        assertNotNull(adminUser.createdAt);
    }

    // --- ScannerUser tests ---

    @Test
    void testScannerUserPrePersist() {
        ScannerUser scannerUser = new ScannerUser();
        assertNull(scannerUser.createdAt);

        scannerUser.prePersist();

        assertNotNull(scannerUser.createdAt);
    }

    // --- User tests ---

    @Test
    void testUserPrePersist() {
        User user = new User();
        assertNull(user.createdAt);

        user.prePersist();

        assertNotNull(user.createdAt);
    }

    // --- TicketOrder.prePersist generates orderNumber starting with ORD- ---

    @Test
    void testTicketOrderPrePersistGeneratesOrdPrefix() {
        TicketOrder order = new TicketOrder();
        order.prePersist();

        assertNotNull(order.orderNumber);
        assertTrue(order.orderNumber.startsWith("ORD-"), "orderNumber should start with ORD-");
        assertEquals(12, order.orderNumber.length(), "ORD- plus 8 uppercase hex chars");
    }

    // --- Ticket.prePersist generates ticketCode starting with TKT- and qrCodeData is UUID ---

    @Test
    void testTicketPrePersistGeneratesTktPrefixAndUuidQr() {
        Ticket ticket = new Ticket();
        ticket.prePersist();

        assertNotNull(ticket.ticketCode);
        assertTrue(ticket.ticketCode.startsWith("TKT-"), "ticketCode should start with TKT-");
        assertNotNull(ticket.qrCodeData);
        // qrCodeData should be a valid UUID
        assertDoesNotThrow(() -> UUID.fromString(ticket.qrCodeData), "qrCodeData should be a valid UUID");
    }

    // --- Event.prePersist sets default values ---

    @Test
    void testEventPrePersistSetsDefaults() {
        Event event = new Event();
        // Before prePersist, defaults are set by field initializers
        assertEquals(0, event.ticketsSold);
        assertEquals(0, event.ticketsReserved);
        assertEquals(EventStatus.DRAFT, event.status);

        event.prePersist();

        // After prePersist, timestamps are set and defaults remain
        assertNotNull(event.createdAt);
        assertNotNull(event.updatedAt);
        assertEquals(0, event.ticketsSold);
        assertEquals(0, event.ticketsReserved);
        assertEquals(EventStatus.DRAFT, event.status);
    }

    // --- Enum tests ---

    @Test
    void testEventStatusValues() {
        EventStatus[] values = EventStatus.values();
        assertEquals(5, values.length);
        assertNotNull(EventStatus.valueOf("DRAFT"));
        assertNotNull(EventStatus.valueOf("PUBLISHED"));
        assertNotNull(EventStatus.valueOf("SOLD_OUT"));
        assertNotNull(EventStatus.valueOf("CANCELLED"));
        assertNotNull(EventStatus.valueOf("COMPLETED"));
    }

    @Test
    void testOrderStatusValues() {
        OrderStatus[] values = OrderStatus.values();
        assertEquals(4, values.length);
        assertNotNull(OrderStatus.valueOf("RESERVED"));
        assertNotNull(OrderStatus.valueOf("CONFIRMED"));
        assertNotNull(OrderStatus.valueOf("CANCELLED"));
        assertNotNull(OrderStatus.valueOf("EXPIRED"));
    }
}
