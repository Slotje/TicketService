package nl.ticketservice.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DtoTest {

    // --- EventDTO ---

    @Test
    void testEventDTO() {
        LocalDateTime now = LocalDateTime.now();
        TicketCategoryDTO cat = new TicketCategoryDTO(
                1L, "VIP", "VIP access", new BigDecimal("50.00"), new BigDecimal("5.00"),
                100, 10, 5, 85, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                LocalDateTime.of(2026, 6, 1, 18, 0), LocalDateTime.of(2026, 6, 2, 23, 0),
                1, true
        );
        EventDTO dto = new EventDTO(
                1L, "Concert", "A great concert", now, now.plusHours(3),
                "Arena", "123 Main St", 500, 50,
                new BigDecimal("25.00"), new BigDecimal("2.00"), new BigDecimal("2.50"),
                5, 450, 100, 20, 330, 10, 40, 110,
                true, true, "http://img.png", "PUBLISHED",
                10L, "Acme Corp", List.of(cat)
        );

        assertEquals(1L, dto.id());
        assertEquals("Concert", dto.name());
        assertEquals("A great concert", dto.description());
        assertEquals(now, dto.eventDate());
        assertEquals(now.plusHours(3), dto.endDate());
        assertEquals("Arena", dto.location());
        assertEquals("123 Main St", dto.address());
        assertEquals(500, dto.maxTickets());
        assertEquals(50, dto.physicalTickets());
        assertEquals(new BigDecimal("25.00"), dto.ticketPrice());
        assertEquals(new BigDecimal("2.00"), dto.serviceFee());
        assertEquals(new BigDecimal("2.50"), dto.effectiveOnlineServiceFee());
        assertEquals(5, dto.maxTicketsPerOrder());
        assertEquals(450, dto.onlineTickets());
        assertEquals(100, dto.ticketsSold());
        assertEquals(20, dto.ticketsReserved());
        assertEquals(330, dto.availableTickets());
        assertEquals(10, dto.physicalTicketsSold());
        assertEquals(40, dto.availablePhysicalTickets());
        assertEquals(110, dto.totalSold());
        assertTrue(dto.physicalTicketsGenerated());
        assertTrue(dto.showAvailability());
        assertEquals("http://img.png", dto.imageUrl());
        assertEquals("PUBLISHED", dto.status());
        assertEquals(10L, dto.customerId());
        assertEquals("Acme Corp", dto.customerName());
        assertEquals(1, dto.ticketCategories().size());
    }

    // --- OrderRequestDTO ---

    @Test
    void testOrderRequestDTO() {
        OrderRequestDTO dto = new OrderRequestDTO(
                1L, 2L, "John", "Doe", "john@example.com", "0612345678", 3
        );

        assertEquals(1L, dto.eventId());
        assertEquals(2L, dto.ticketCategoryId());
        assertEquals("John", dto.buyerFirstName());
        assertEquals("Doe", dto.buyerLastName());
        assertEquals("john@example.com", dto.buyerEmail());
        assertEquals("0612345678", dto.buyerPhone());
        assertEquals(3, dto.quantity());
    }

    // --- OrderResponseDTO ---

    @Test
    void testOrderResponseDTO() {
        LocalDateTime now = LocalDateTime.now();
        TicketDTO ticket = new TicketDTO(
                1L, "TKT-ABC", "qr-data", "ONLINE", "VIP",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 2),
                false, null, now
        );
        OrderResponseDTO dto = new OrderResponseDTO(
                1L, "ORD-12345678", "John", "Doe", "john@example.com", "0612345678",
                "Kerkstraat", "10", "1234AB", "Amsterdam",
                2, new BigDecimal("25.00"), new BigDecimal("2.50"),
                new BigDecimal("5.00"), new BigDecimal("55.00"),
                "CONFIRMED", "Concert", 10L, "VIP", 5L,
                now, now.plusMinutes(5), now.plusMinutes(30),
                List.of(ticket),
                null, null
        );

        assertEquals(1L, dto.id());
        assertEquals("ORD-12345678", dto.orderNumber());
        assertEquals("John", dto.buyerFirstName());
        assertEquals("Doe", dto.buyerLastName());
        assertEquals("john@example.com", dto.buyerEmail());
        assertEquals("0612345678", dto.buyerPhone());
        assertEquals("Kerkstraat", dto.buyerStreet());
        assertEquals("10", dto.buyerHouseNumber());
        assertEquals("1234AB", dto.buyerPostalCode());
        assertEquals("Amsterdam", dto.buyerCity());
        assertEquals(2, dto.quantity());
        assertEquals(new BigDecimal("25.00"), dto.ticketPrice());
        assertEquals(new BigDecimal("2.50"), dto.serviceFeePerTicket());
        assertEquals(new BigDecimal("5.00"), dto.totalServiceFee());
        assertEquals(new BigDecimal("55.00"), dto.totalPrice());
        assertEquals("CONFIRMED", dto.status());
        assertEquals("Concert", dto.eventName());
        assertEquals(10L, dto.eventId());
        assertEquals("VIP", dto.ticketCategoryName());
        assertEquals(5L, dto.ticketCategoryId());
        assertEquals(now, dto.createdAt());
        assertEquals(now.plusMinutes(5), dto.confirmedAt());
        assertEquals(now.plusMinutes(30), dto.expiresAt());
        assertEquals(1, dto.tickets().size());
    }

    // --- LoginDTO ---

    @Test
    void testLoginDTO() {
        LoginDTO dto = new LoginDTO("admin", "secret123");

        assertEquals("admin", dto.username());
        assertEquals("secret123", dto.password());
    }

    // --- RegisterDTO ---

    @Test
    void testRegisterDTO() {
        RegisterDTO dto = new RegisterDTO(
                "user@example.com", "pass1234", "Jane", "Smith", "0698765432"
        );

        assertEquals("user@example.com", dto.email());
        assertEquals("pass1234", dto.password());
        assertEquals("Jane", dto.firstName());
        assertEquals("Smith", dto.lastName());
        assertEquals("0698765432", dto.phone());
    }

    // --- LoginResponseDTO ---

    @Test
    void testLoginResponseDTO() {
        LoginResponseDTO dto = new LoginResponseDTO("jwt-token-123", "Admin User", "admin");

        assertEquals("jwt-token-123", dto.token());
        assertEquals("Admin User", dto.displayName());
        assertEquals("admin", dto.username());
    }

    // --- UserLoginDTO ---

    @Test
    void testUserLoginDTO() {
        UserLoginDTO dto = new UserLoginDTO("user@example.com", "password123");

        assertEquals("user@example.com", dto.email());
        assertEquals("password123", dto.password());
    }

    // --- CustomerDTO ---

    @Test
    void testCustomerDTO() {
        CustomerDTO dto = new CustomerDTO(
                1L, "Acme Corp", "John Boss", "john@acme.com", "0201234567",
                "http://logo.png", "#FF0000", "#00FF00", "http://acme.com", true
        );

        assertEquals(1L, dto.id());
        assertEquals("Acme Corp", dto.companyName());
        assertEquals("John Boss", dto.contactPerson());
        assertEquals("john@acme.com", dto.email());
        assertEquals("0201234567", dto.phone());
        assertEquals("http://logo.png", dto.logoUrl());
        assertEquals("#FF0000", dto.primaryColor());
        assertEquals("#00FF00", dto.secondaryColor());
        assertEquals("http://acme.com", dto.website());
        assertTrue(dto.active());
    }

    // --- BuyerDetailsDTO ---

    @Test
    void testBuyerDetailsDTO() {
        BuyerDetailsDTO dto = new BuyerDetailsDTO(
                "Kerkstraat", "42", "1234AB", "Amsterdam"
        );

        assertEquals("Kerkstraat", dto.buyerStreet());
        assertEquals("42", dto.buyerHouseNumber());
        assertEquals("1234AB", dto.buyerPostalCode());
        assertEquals("Amsterdam", dto.buyerCity());
    }

    // --- ScannerUserDTO ---

    @Test
    void testScannerUserDTO() {
        LocalDateTime now = LocalDateTime.now();
        ScannerUserDTO dto = new ScannerUserDTO(1L, "scanner1", "Scanner One", true, now);

        assertEquals(1L, dto.id());
        assertEquals("scanner1", dto.username());
        assertEquals("Scanner One", dto.displayName());
        assertTrue(dto.active());
        assertEquals(now, dto.createdAt());
    }

    // --- CreateScannerUserDTO ---

    @Test
    void testCreateScannerUserDTO() {
        CreateScannerUserDTO dto = new CreateScannerUserDTO("scanner1", "pass1234", "Scanner One");

        assertEquals("scanner1", dto.username());
        assertEquals("pass1234", dto.password());
        assertEquals("Scanner One", dto.displayName());
    }

    // --- TicketDTO ---

    @Test
    void testTicketDTO() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate date = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 2);
        TicketDTO dto = new TicketDTO(
                1L, "TKT-ABC12345", "uuid-qr-data", "ONLINE", "General Admission",
                date, endDate, true, now, now.minusHours(1)
        );

        assertEquals(1L, dto.id());
        assertEquals("TKT-ABC12345", dto.ticketCode());
        assertEquals("uuid-qr-data", dto.qrCodeData());
        assertEquals("ONLINE", dto.ticketType());
        assertEquals("General Admission", dto.categoryName());
        assertEquals(date, dto.validDate());
        assertEquals(endDate, dto.validEndDate());
        assertTrue(dto.scanned());
        assertEquals(now, dto.scannedAt());
        assertEquals(now.minusHours(1), dto.createdAt());
    }

    // --- TicketCategoryDTO ---

    @Test
    void testTicketCategoryDTO() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        LocalDate endDate = LocalDate.of(2026, 6, 2);
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 1, 18, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 6, 2, 23, 0);
        TicketCategoryDTO dto = new TicketCategoryDTO(
                1L, "VIP", "VIP access", new BigDecimal("75.00"), new BigDecimal("5.00"),
                200, 50, 10, 140, date, endDate, startTime, endTime, 1, true
        );

        assertEquals(1L, dto.id());
        assertEquals("VIP", dto.name());
        assertEquals("VIP access", dto.description());
        assertEquals(new BigDecimal("75.00"), dto.price());
        assertEquals(new BigDecimal("5.00"), dto.serviceFee());
        assertEquals(200, dto.maxTickets());
        assertEquals(50, dto.ticketsSold());
        assertEquals(10, dto.ticketsReserved());
        assertEquals(140, dto.availableTickets());
        assertEquals(date, dto.validDate());
        assertEquals(endDate, dto.validEndDate());
        assertEquals(startTime, dto.startTime());
        assertEquals(endTime, dto.endTime());
        assertEquals(1, dto.sortOrder());
        assertTrue(dto.active());
    }

    // --- TicketSalesDTO ---

    @Test
    void testTicketSalesDTO() {
        TicketSalesDTO dto = new TicketSalesDTO(
                1L, "Concert", "PUBLISHED",
                500, 50, 450, 110, 390,
                100, 20, 330,
                10, 40, true,
                new BigDecimal("25.00"), new BigDecimal("2.00"), new BigDecimal("2.50"),
                new BigDecimal("2500.00"), new BigDecimal("250.00"),
                new BigDecimal("200.00"), new BigDecimal("2950.00"),
                80, 30
        );

        assertEquals(1L, dto.eventId());
        assertEquals("Concert", dto.eventName());
        assertEquals("PUBLISHED", dto.eventStatus());
        assertEquals(500, dto.maxTickets());
        assertEquals(50, dto.physicalTickets());
        assertEquals(450, dto.onlineTickets());
        assertEquals(110, dto.totalSold());
        assertEquals(390, dto.totalRemaining());
        assertEquals(100, dto.onlineSold());
        assertEquals(20, dto.onlineReserved());
        assertEquals(330, dto.onlineAvailable());
        assertEquals(10, dto.physicalSold());
        assertEquals(40, dto.physicalAvailable());
        assertTrue(dto.physicalTicketsGenerated());
        assertEquals(new BigDecimal("25.00"), dto.ticketPrice());
        assertEquals(new BigDecimal("2.00"), dto.serviceFeePerTicket());
        assertEquals(new BigDecimal("2.50"), dto.effectiveOnlineServiceFee());
        assertEquals(new BigDecimal("2500.00"), dto.totalOnlineRevenue());
        assertEquals(new BigDecimal("250.00"), dto.totalPhysicalRevenue());
        assertEquals(new BigDecimal("200.00"), dto.totalServiceFeeRevenue());
        assertEquals(new BigDecimal("2950.00"), dto.totalRevenue());
        assertEquals(80, dto.ticketsScanned());
        assertEquals(30, dto.ticketsNotScanned());
    }

    // --- UserResponseDTO ---

    @Test
    void testUserResponseDTO() {
        UserResponseDTO dto = new UserResponseDTO(
                "jwt-token", "user@example.com", "Jane", "Smith",
                "0612345678", "Kerkstraat", "10", "1234AB", "Amsterdam"
        );

        assertEquals("jwt-token", dto.token());
        assertEquals("user@example.com", dto.email());
        assertEquals("Jane", dto.firstName());
        assertEquals("Smith", dto.lastName());
        assertEquals("0612345678", dto.phone());
        assertEquals("Kerkstraat", dto.street());
        assertEquals("10", dto.houseNumber());
        assertEquals("1234AB", dto.postalCode());
        assertEquals("Amsterdam", dto.city());
    }

    // --- UserUpdateDTO ---

    @Test
    void testUserUpdateDTO() {
        UserUpdateDTO dto = new UserUpdateDTO(
                "Jane", "Smith", "0612345678", "Kerkstraat", "10", "1234AB", "Amsterdam"
        );

        assertEquals("Jane", dto.firstName());
        assertEquals("Smith", dto.lastName());
        assertEquals("0612345678", dto.phone());
        assertEquals("Kerkstraat", dto.street());
        assertEquals("10", dto.houseNumber());
        assertEquals("1234AB", dto.postalCode());
        assertEquals("Amsterdam", dto.city());
    }
}
