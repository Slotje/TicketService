package nl.ticketservice.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import nl.ticketservice.entity.*;
import nl.ticketservice.exception.TicketServiceException;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
public class PdfServiceCoverageTest {

    @Inject
    PdfService pdfService;

    @Inject
    EntityManager em;

    // =========================================================================
    // Helper: create a TicketOrder with configurable branches
    // =========================================================================

    @Transactional
    TicketOrder createTestOrder(boolean hasLogo, boolean hasWebsite, boolean hasServiceFee,
                                boolean hasEventImage, boolean hasEndDate, boolean hasAddress,
                                boolean hasCategoryName, boolean hasValidDate, boolean hasValidDateRange,
                                String primaryColor) {
        Customer c = new Customer();
        c.companyName = "Test Bedrijf";
        c.contactPerson = "Jan Test";
        c.email = "pdf-test-" + System.nanoTime() + "@example.com";
        c.phone = "0612345678";
        c.primaryColor = primaryColor;
        c.secondaryColor = null;
        c.slug = "pdf-test-" + System.nanoTime();
        c.active = true;

        if (hasLogo) {
            // Use a URL that ImageLoaderService will attempt to load (will return null since
            // there is no actual file, but the logoBytes path is exercised via imageLoader)
            c.logoUrl = "https://example.com/logo.png";
        } else {
            c.logoUrl = null;
        }

        if (hasWebsite) {
            c.website = "https://www.testbedrijf.nl";
        } else {
            c.website = null;
        }

        em.persist(c);

        Event e = new Event();
        e.name = "Test Evenement";
        e.description = "Een test evenement";
        e.eventDate = LocalDateTime.now().plusMonths(2);
        e.location = "Testlocatie";
        e.maxTickets = 100;
        e.ticketPrice = new BigDecimal("25.00");
        e.serviceFee = hasServiceFee ? new BigDecimal("2.50") : BigDecimal.ZERO;
        e.status = EventStatus.PUBLISHED;
        e.customer = c;

        if (hasEndDate) {
            e.endDate = e.eventDate.plusHours(4);
        } else {
            e.endDate = null;
        }

        if (hasAddress) {
            e.address = "Teststraat 1, 1234 AB Teststad";
        } else {
            e.address = null;
        }

        if (hasEventImage) {
            e.imageUrl = "https://example.com/event.png";
        } else {
            e.imageUrl = null;
        }

        em.persist(e);

        TicketOrder order = new TicketOrder();
        order.buyerFirstName = "Piet";
        order.buyerLastName = "Jansen";
        order.buyerEmail = "piet@example.com";
        order.buyerPhone = "0698765432";
        order.quantity = 1;
        order.serviceFeePerTicket = hasServiceFee ? new BigDecimal("2.50") : BigDecimal.ZERO;
        order.totalServiceFee = hasServiceFee ? new BigDecimal("2.50") : BigDecimal.ZERO;
        order.totalPrice = hasServiceFee ? new BigDecimal("27.50") : new BigDecimal("25.00");
        order.status = OrderStatus.CONFIRMED;
        order.event = e;

        em.persist(order);

        Ticket t = new Ticket();
        t.ticketType = TicketType.ONLINE;
        t.event = e;
        t.order = order;

        if (hasCategoryName) {
            t.categoryName = "VIP Rang";
        } else {
            t.categoryName = null;
        }

        if (hasValidDate) {
            t.validDate = LocalDate.now().plusMonths(2);
            if (hasValidDateRange) {
                t.validEndDate = t.validDate.plusDays(3);
            } else {
                t.validEndDate = null;
            }
        } else {
            t.validDate = null;
            t.validEndDate = null;
        }

        em.persist(t);
        order.tickets.add(t);

        em.flush();
        return order;
    }

    @Transactional
    Customer createTestCustomer(boolean hasLogo, boolean hasWebsite, String primaryColor) {
        Customer c = new Customer();
        c.companyName = "Preview Bedrijf";
        c.contactPerson = "Kees Preview";
        c.email = "preview-" + System.nanoTime() + "@example.com";
        c.phone = "0611111111";
        c.primaryColor = primaryColor;
        c.secondaryColor = null;
        c.slug = "preview-" + System.nanoTime();
        c.active = true;

        if (hasLogo) {
            c.logoUrl = "https://example.com/logo.png";
        } else {
            c.logoUrl = null;
        }

        if (hasWebsite) {
            c.website = "https://www.previewbedrijf.nl";
        } else {
            c.website = null;
        }

        em.persist(c);
        em.flush();
        return c;
    }

    // =========================================================================
    // generateOrderPdf tests
    // =========================================================================

    @Test
    @Order(1)
    public void testGenerateOrderPdf_noImage_noLogo_noServiceFee_noWebsite() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, "#FF0000");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(2)
    public void testGenerateOrderPdf_withEventImage() {
        // eventImageBytes will be null because the URL is unreachable, but the branch is entered
        TicketOrder order = createTestOrder(
                false, false, false, true, false, false, false, false, false, "#00FF00");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(3)
    public void testGenerateOrderPdf_withLogo() {
        TicketOrder order = createTestOrder(
                true, false, false, false, false, false, false, false, false, "#0000FF");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(4)
    public void testGenerateOrderPdf_withServiceFee() {
        TicketOrder order = createTestOrder(
                false, false, true, false, false, false, false, false, false, "#ABCDEF");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(5)
    public void testGenerateOrderPdf_withWebsite() {
        TicketOrder order = createTestOrder(
                false, true, false, false, false, false, false, false, false, "#123456");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(6)
    public void testGenerateOrderPdf_withEndDate() {
        TicketOrder order = createTestOrder(
                false, false, false, false, true, false, false, false, false, "#654321");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(7)
    public void testGenerateOrderPdf_withAddress() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, true, false, false, false, "#AABBCC");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(8)
    public void testGenerateOrderPdf_withEmptyAddress() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, "#DDEEFF");
        // Set address to empty string to test the isEmpty() branch
        setEventAddressEmpty(order.event.id);
        // Re-fetch
        TicketOrder refreshed = refreshOrder(order.id);
        byte[] pdf = pdfService.generateOrderPdf(refreshed);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Transactional
    void setEventAddressEmpty(Long eventId) {
        Event e = em.find(Event.class, eventId);
        e.address = "";
        em.merge(e);
        em.flush();
    }

    @Transactional
    TicketOrder refreshOrder(Long orderId) {
        TicketOrder o = em.find(TicketOrder.class, orderId);
        // Force-load lazy associations
        o.event.customer.companyName.length();
        o.tickets.size();
        return o;
    }

    @Test
    @Order(9)
    public void testGenerateOrderPdf_withCategoryName() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, true, false, false, "#112233");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(10)
    public void testGenerateOrderPdf_withValidDate() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, true, false, "#445566");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(11)
    public void testGenerateOrderPdf_withValidDateRange() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, true, true, "#778899");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(12)
    public void testGenerateOrderPdf_allBranchesEnabled() {
        TicketOrder order = createTestOrder(
                true, true, true, true, true, true, true, true, true, "#FF5733");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(13)
    public void testGenerateOrderPdf_nullPrimaryColor() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, null);
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(14)
    public void testGenerateOrderPdf_multipleTickets() {
        TicketOrder order = createOrderWithMultipleTickets();
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Transactional
    TicketOrder createOrderWithMultipleTickets() {
        Customer c = new Customer();
        c.companyName = "Multi Ticket Bedrijf";
        c.contactPerson = "Multi Test";
        c.email = "multi-" + System.nanoTime() + "@example.com";
        c.primaryColor = "#AABB00";
        c.slug = "multi-" + System.nanoTime();
        c.active = true;
        em.persist(c);

        Event e = new Event();
        e.name = "Multi Ticket Event";
        e.eventDate = LocalDateTime.now().plusMonths(3);
        e.location = "Multi Locatie";
        e.maxTickets = 200;
        e.ticketPrice = new BigDecimal("30.00");
        e.serviceFee = BigDecimal.ZERO;
        e.status = EventStatus.PUBLISHED;
        e.customer = c;
        em.persist(e);

        TicketOrder order = new TicketOrder();
        order.buyerFirstName = "Karel";
        order.buyerLastName = "Groot";
        order.buyerEmail = "karel@example.com";
        order.quantity = 3;
        order.serviceFeePerTicket = BigDecimal.ZERO;
        order.totalServiceFee = BigDecimal.ZERO;
        order.totalPrice = new BigDecimal("90.00");
        order.status = OrderStatus.CONFIRMED;
        order.event = e;
        em.persist(order);

        for (int i = 0; i < 3; i++) {
            Ticket t = new Ticket();
            t.ticketType = TicketType.ONLINE;
            t.event = e;
            t.order = order;
            em.persist(t);
            order.tickets.add(t);
        }

        em.flush();
        return order;
    }

    @Test
    @Order(15)
    public void testGenerateOrderPdf_withWebsiteEmpty() {
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, "#ABCDEF");
        setCustomerWebsiteEmpty(order.event.customer.id);
        TicketOrder refreshed = refreshOrder(order.id);
        byte[] pdf = pdfService.generateOrderPdf(refreshed);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Transactional
    void setCustomerWebsiteEmpty(Long customerId) {
        Customer c = em.find(Customer.class, customerId);
        c.website = "";
        em.merge(c);
        em.flush();
    }

    // =========================================================================
    // generatePreviewPdf tests
    // =========================================================================

    @Test
    @Order(20)
    public void testGeneratePreviewPdf_withLogoAndWebsite() {
        Customer customer = createTestCustomer(true, true, "#FF0000");
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(21)
    public void testGeneratePreviewPdf_withoutLogoOrWebsite() {
        Customer customer = createTestCustomer(false, false, "#00FF00");
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(22)
    public void testGeneratePreviewPdf_withLogo_noWebsite() {
        Customer customer = createTestCustomer(true, false, "#0000FF");
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(23)
    public void testGeneratePreviewPdf_noLogo_withWebsite() {
        Customer customer = createTestCustomer(false, true, "#ABCDEF");
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(24)
    public void testGeneratePreviewPdf_nullPrimaryColor() {
        Customer customer = createTestCustomer(false, false, null);
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(25)
    public void testGeneratePreviewPdf_emptyWebsite() {
        Customer customer = createTestCustomer(false, false, "#CCDDEE");
        setCustomerWebsiteEmpty(customer.id);
        Customer refreshed = refreshCustomer(customer.id);
        byte[] pdf = pdfService.generatePreviewPdf(refreshed);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Transactional
    Customer refreshCustomer(Long id) {
        return em.find(Customer.class, id);
    }

    // =========================================================================
    // parseColor tests (via reflection since it's private, or indirectly)
    // We test parseColor indirectly through generateOrderPdf/generatePreviewPdf
    // and directly via reflection for edge cases
    // =========================================================================

    @Test
    @Order(30)
    public void testParseColor_validHexWithHash() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 0);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "#FF0000", defaultColor);
        assertNotNull(result);
        float[] values = result.getColorValue();
        assertEquals(1.0f, values[0], 0.01f); // Red = 255/255
        assertEquals(0.0f, values[1], 0.01f); // Green = 0
        assertEquals(0.0f, values[2], 0.01f); // Blue = 0
    }

    @Test
    @Order(31)
    public void testParseColor_validHexWithoutHash() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 0);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "00FF00", defaultColor);
        assertNotNull(result);
        float[] values = result.getColorValue();
        assertEquals(0.0f, values[0], 0.01f);
        assertEquals(1.0f, values[1], 0.01f); // Green = 255/255
        assertEquals(0.0f, values[2], 0.01f);
    }

    @Test
    @Order(32)
    public void testParseColor_null() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(100, 100, 100);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, (String) null, defaultColor);
        assertSame(defaultColor, result);
    }

    @Test
    @Order(33)
    public void testParseColor_empty() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(100, 100, 100);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "", defaultColor);
        assertSame(defaultColor, result);
    }

    @Test
    @Order(34)
    public void testParseColor_invalidHex() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(50, 50, 50);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "ZZZZZZ", defaultColor);
        assertSame(defaultColor, result);
    }

    @Test
    @Order(35)
    public void testParseColor_tooShort() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(75, 75, 75);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "#AB", defaultColor);
        assertSame(defaultColor, result);
    }

    // =========================================================================
    // contrastColor tests
    // =========================================================================

    @Test
    @Order(40)
    public void testContrastColor_lightColor_returnsNavy() throws Exception {
        var method = PdfService.class.getDeclaredMethod("contrastColor",
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        // White-ish color -> luminance > 0.5 -> returns dark navy
        var lightColor = new com.itextpdf.kernel.colors.DeviceRgb(255, 255, 255);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, lightColor);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // Should return navy: DeviceRgb(15, 23, 42) = approx (0.059, 0.090, 0.165)
        assertTrue(values[0] < 0.1f);
        assertTrue(values[1] < 0.1f);
        assertTrue(values[2] < 0.2f);
    }

    @Test
    @Order(41)
    public void testContrastColor_darkColor_returnsWhite() throws Exception {
        var method = PdfService.class.getDeclaredMethod("contrastColor",
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        // Black -> luminance < 0.5 -> returns white
        var darkColor = new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 0);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, darkColor);
        assertNotNull(result);
        float[] values = result.getColorValue();
        assertEquals(1.0f, values[0], 0.01f);
        assertEquals(1.0f, values[1], 0.01f);
        assertEquals(1.0f, values[2], 0.01f);
    }

    @Test
    @Order(42)
    public void testContrastColor_midDarkColor_returnsWhite() throws Exception {
        var method = PdfService.class.getDeclaredMethod("contrastColor",
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        // Dark blue -> luminance < 0.5 -> returns white
        var midDark = new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 128);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, midDark);
        float[] values = result.getColorValue();
        assertEquals(1.0f, values[0], 0.01f);
        assertEquals(1.0f, values[1], 0.01f);
        assertEquals(1.0f, values[2], 0.01f);
    }

    @Test
    @Order(43)
    public void testContrastColor_midLightColor_returnsNavy() throws Exception {
        var method = PdfService.class.getDeclaredMethod("contrastColor",
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        // Light yellow -> luminance > 0.5 -> returns navy
        var midLight = new com.itextpdf.kernel.colors.DeviceRgb(255, 255, 128);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, midLight);
        float[] values = result.getColorValue();
        // Should return navy
        assertTrue(values[0] < 0.1f);
    }

    // =========================================================================
    // lightenColor tests
    // =========================================================================

    @Test
    @Order(50)
    public void testLightenColor_zeroFactor() throws Exception {
        var method = PdfService.class.getDeclaredMethod("lightenColor",
                com.itextpdf.kernel.colors.DeviceRgb.class, float.class);
        method.setAccessible(true);

        var color = new com.itextpdf.kernel.colors.DeviceRgb(100, 50, 25);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, color, 0.0f);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // factor=0 means no lightening, should be same as original
        assertEquals(100f / 255f, values[0], 0.02f);
        assertEquals(50f / 255f, values[1], 0.02f);
        assertEquals(25f / 255f, values[2], 0.02f);
    }

    @Test
    @Order(51)
    public void testLightenColor_fullFactor() throws Exception {
        var method = PdfService.class.getDeclaredMethod("lightenColor",
                com.itextpdf.kernel.colors.DeviceRgb.class, float.class);
        method.setAccessible(true);

        var color = new com.itextpdf.kernel.colors.DeviceRgb(100, 50, 25);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, color, 1.0f);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // factor=1.0 means fully lightened -> white (255,255,255)
        assertEquals(1.0f, values[0], 0.01f);
        assertEquals(1.0f, values[1], 0.01f);
        assertEquals(1.0f, values[2], 0.01f);
    }

    @Test
    @Order(52)
    public void testLightenColor_halfFactor() throws Exception {
        var method = PdfService.class.getDeclaredMethod("lightenColor",
                com.itextpdf.kernel.colors.DeviceRgb.class, float.class);
        method.setAccessible(true);

        var color = new com.itextpdf.kernel.colors.DeviceRgb(100, 0, 0);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, color, 0.5f);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // r = min(255, 100 + (255-100)*0.5) = min(255, 100 + 77.5) = 177 -> 177/255 ~ 0.694
        assertTrue(values[0] > 0.5f);
        assertTrue(values[0] < 0.8f);
    }

    @Test
    @Order(53)
    public void testLightenColor_blackColor() throws Exception {
        var method = PdfService.class.getDeclaredMethod("lightenColor",
                com.itextpdf.kernel.colors.DeviceRgb.class, float.class);
        method.setAccessible(true);

        var color = new com.itextpdf.kernel.colors.DeviceRgb(0, 0, 0);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, color, 0.85f);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // r = min(255, 0 + 255*0.85) = 216 -> 216/255 ~ 0.847
        assertTrue(values[0] > 0.8f);
        assertTrue(values[0] < 0.9f);
    }

    @Test
    @Order(54)
    public void testLightenColor_whiteColor() throws Exception {
        var method = PdfService.class.getDeclaredMethod("lightenColor",
                com.itextpdf.kernel.colors.DeviceRgb.class, float.class);
        method.setAccessible(true);

        var color = new com.itextpdf.kernel.colors.DeviceRgb(255, 255, 255);
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, color, 0.5f);
        assertNotNull(result);
        float[] values = result.getColorValue();
        // Already white, should stay capped at 255
        assertEquals(1.0f, values[0], 0.01f);
        assertEquals(1.0f, values[1], 0.01f);
        assertEquals(1.0f, values[2], 0.01f);
    }

    // =========================================================================
    // Edge cases and combined scenarios
    // =========================================================================

    @Test
    @Order(60)
    public void testGenerateOrderPdf_withServiceFeeZero_explicitCheck() {
        // serviceFeePerTicket = 0, verifying the compareTo(ZERO) <= 0 branch
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, "#AAAAAA");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(61)
    public void testGenerateOrderPdf_withEndDateAndAddress() {
        // Covers both endDate != null and address != null && !address.isEmpty()
        TicketOrder order = createTestOrder(
                false, false, false, false, true, true, false, false, false, "#BBBBBB");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(62)
    public void testGeneratePreviewPdf_withInvalidPrimaryColor() {
        // Invalid color -> parseColor returns default (GOLD)
        Customer customer = createTestCustomer(false, false, "XXXXXX");
        byte[] pdf = pdfService.generatePreviewPdf(customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(63)
    public void testGenerateOrderPdf_colorWithoutHash() {
        // Color without # prefix -> parseColor strips # branch not taken
        TicketOrder order = createTestOrder(
                false, false, false, false, false, false, false, false, false, "AABB00");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }

    @Test
    @Order(64)
    public void testParseColor_hashOnly() throws Exception {
        var method = PdfService.class.getDeclaredMethod("parseColor", String.class,
                com.itextpdf.kernel.colors.DeviceRgb.class);
        method.setAccessible(true);

        var defaultColor = new com.itextpdf.kernel.colors.DeviceRgb(10, 10, 10);
        // "#" -> after stripping hash, empty string -> substring throws -> returns default
        var result = (com.itextpdf.kernel.colors.DeviceRgb) method.invoke(pdfService, "#", defaultColor);
        assertSame(defaultColor, result);
    }

    @Test
    @Order(65)
    public void testGenerateOrderPdf_withLogoAndImage_andAllDetails() {
        // Full scenario: logo, event image, service fee, website, endDate, address
        TicketOrder order = createTestOrder(
                true, true, true, true, true, true, true, true, true, "#D4A853");
        byte[] pdf = pdfService.generateOrderPdf(order);
        assertNotNull(pdf);
        assertTrue(pdf.length > 0);
    }
}
