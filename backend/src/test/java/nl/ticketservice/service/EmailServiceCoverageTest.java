package nl.ticketservice.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class EmailServiceCoverageTest {

    @Inject
    EmailService emailService;

    @Inject
    EntityManager em;

    // =========================================================================
    // sendPasswordResetEmail
    // =========================================================================

    @Test
    @Order(1)
    void sendPasswordResetEmail_success_returnsTrue() {
        boolean result = emailService.sendPasswordResetEmail(
                "resettest@test.nl", "Test User", "https://example.com/reset?token=abc123");
        assertTrue(result);
    }

    // =========================================================================
    // sendPhysicalTicketsPdf
    // =========================================================================

    @Test
    @Order(10)
    @Transactional
    void sendPhysicalTicketsPdf_success_returnsTrue() {
        Customer customer = new Customer();
        customer.companyName = "Physical Email BV";
        customer.contactPerson = "Physical Tester";
        customer.email = "physical-email-" + System.nanoTime() + "@test.nl";
        customer.active = true;
        customer.slug = "physical-email-" + System.nanoTime();
        em.persist(customer);

        Event event = new Event();
        event.name = "Physical Email Event";
        event.description = "Event for testing physical ticket emails";
        event.eventDate = LocalDateTime.now().plusMonths(2);
        event.location = "Email Test Venue";
        event.maxTickets = 100;
        event.physicalTickets = 10;
        event.ticketPrice = new BigDecimal("25.00");
        event.serviceFee = BigDecimal.ZERO;
        event.status = EventStatus.PUBLISHED;
        event.customer = customer;
        em.persist(event);
        em.flush();

        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46}; // "%PDF"
        boolean result = emailService.sendPhysicalTicketsPdf(event, fakePdf);
        assertTrue(result);
    }

    @Test
    @Order(11)
    @Transactional
    void sendPhysicalTicketsPdf_withSpecialCharsInName_success() {
        Customer customer = new Customer();
        customer.companyName = "Special Chars & Co.";
        customer.contactPerson = "Special Tester";
        customer.email = "special-" + System.nanoTime() + "@test.nl";
        customer.active = true;
        customer.slug = "special-" + System.nanoTime();
        em.persist(customer);

        Event event = new Event();
        event.name = "Event Met Spaties & Tekens!";
        event.description = "Test event name sanitization in PDF filename";
        event.eventDate = LocalDateTime.now().plusMonths(3);
        event.location = "Special Venue";
        event.maxTickets = 50;
        event.physicalTickets = 5;
        event.ticketPrice = new BigDecimal("15.00");
        event.serviceFee = BigDecimal.ZERO;
        event.status = EventStatus.PUBLISHED;
        event.customer = customer;
        em.persist(event);
        em.flush();

        byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46};
        boolean result = emailService.sendPhysicalTicketsPdf(event, fakePdf);
        assertTrue(result);
    }

    // =========================================================================
    // sendOrderConfirmation - already tested in ScheduledServicesTest
    // but let's verify the buildCustomerFrom with a normal company name
    // =========================================================================

    @Test
    @Order(20)
    @Transactional
    void sendOrderConfirmation_withValidOrder_returnsTrue() {
        Customer customer = new Customer();
        customer.companyName = "Email Order BV";
        customer.contactPerson = "Order Tester";
        customer.email = "email-order-" + System.nanoTime() + "@test.nl";
        customer.active = true;
        customer.slug = "email-order-" + System.nanoTime();
        em.persist(customer);

        Event event = new Event();
        event.name = "Email Order Event";
        event.eventDate = LocalDateTime.now().plusMonths(1);
        event.location = "Order Venue";
        event.maxTickets = 100;
        event.ticketPrice = new BigDecimal("20.00");
        event.serviceFee = new BigDecimal("2.00");
        event.status = EventStatus.PUBLISHED;
        event.customer = customer;
        em.persist(event);

        TicketOrder order = new TicketOrder();
        order.buyerFirstName = "Email";
        order.buyerLastName = "Koper";
        order.buyerEmail = "emailkoper@test.nl";
        order.buyerPhone = "0612345678";
        order.quantity = 2;
        order.serviceFeePerTicket = new BigDecimal("2.00");
        order.totalServiceFee = new BigDecimal("4.00");
        order.totalPrice = new BigDecimal("44.00");
        order.status = OrderStatus.CONFIRMED;
        order.event = event;
        em.persist(order);

        Ticket ticket = new Ticket();
        ticket.ticketType = TicketType.ONLINE;
        ticket.event = event;
        ticket.order = order;
        em.persist(ticket);
        order.tickets.add(ticket);
        em.flush();

        boolean result = emailService.sendOrderConfirmation(order);
        assertTrue(result);
    }

    // =========================================================================
    // sendCustomerInvite - already tested, but test with non-empty company name
    // that has all special characters stripped
    // =========================================================================

    @Test
    @Order(30)
    @Transactional
    void sendCustomerInvite_withSymbolOnlyName_usesFallback() {
        Customer customer = new Customer();
        customer.companyName = "!!!###";
        customer.contactPerson = "Symbol Tester";
        customer.email = "symbol-" + System.nanoTime() + "@test.nl";
        customer.active = true;
        customer.slug = "symbol-" + System.nanoTime();
        em.persist(customer);
        em.flush();

        boolean result = emailService.sendCustomerInvite(customer, "test-token-symbol");
        assertTrue(result);
    }

    @Test
    @Order(31)
    @Transactional
    void sendCustomerInvite_withNormalName_usesCustomFrom() {
        Customer customer = new Customer();
        customer.companyName = "Test Bedrijf";
        customer.contactPerson = "Normaal Persoon";
        customer.email = "normal-" + System.nanoTime() + "@test.nl";
        customer.active = true;
        customer.slug = "normal-" + System.nanoTime();
        em.persist(customer);
        em.flush();

        boolean result = emailService.sendCustomerInvite(customer, "test-token-normal");
        assertTrue(result);
    }
}
