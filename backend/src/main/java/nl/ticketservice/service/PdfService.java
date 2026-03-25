package nl.ticketservice.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.ticketservice.entity.Customer;
import nl.ticketservice.entity.Event;
import nl.ticketservice.entity.Ticket;
import nl.ticketservice.entity.TicketOrder;
import nl.ticketservice.exception.TicketServiceException;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class PdfService {

    @Inject
    QrCodeService qrCodeService;

    @Inject
    ImageLoaderService imageLoader;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.forLanguageTag("nl-NL"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    // Premium color palette
    private static final DeviceRgb DARK_BG = new DeviceRgb(15, 23, 42);       // Navy 950
    private static final DeviceRgb DARK_CARD = new DeviceRgb(30, 41, 59);      // Navy 900
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(203, 213, 225);   // Slate 300 — better dark mode contrast
    private static final DeviceRgb TEXT_LIGHT = new DeviceRgb(226, 232, 240);   // Slate 200
    private static final DeviceRgb GOLD = new DeviceRgb(212, 168, 83);          // Brand gold
    private static final DeviceRgb GOLD_DARK = new DeviceRgb(184, 144, 62);     // Darker gold
    private static final DeviceRgb DIVIDER = new DeviceRgb(51, 65, 85);         // Navy 700
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);

    public byte[] generateOrderPdf(TicketOrder order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(0, 0, 0, 0);

            Event event = order.event;
            Customer customer = event.customer;

            DeviceRgb primaryColor = parseColor(customer.primaryColor, GOLD);
            DeviceRgb primaryLight = lightenColor(primaryColor, 0.85f);

            // Load images
            byte[] logoBytes = imageLoader.loadImage(customer.logoUrl);
            byte[] eventImageBytes = imageLoader.loadImage(event.imageUrl);

            for (int i = 0; i < order.tickets.size(); i++) {
                Ticket ticket = order.tickets.get(i);

                if (i > 0) {
                    document.add(new AreaBreak());
                }

                // === FULL PAGE DARK BACKGROUND ===
                Div page = new Div()
                        .setBackgroundColor(DARK_BG)
                        .setMinHeight(842)
                        .setPadding(0);

                // === TOP HERO SECTION with event image ===
                Div heroSection = new Div()
                        .setMinHeight(220)
                        .setPadding(0)
                        .setMarginBottom(0);

                if (eventImageBytes != null) {
                    // Event image as hero banner
                    try {
                        Image eventImg = new Image(ImageDataFactory.create(eventImageBytes))
                                .setWidth(UnitValue.createPercentValue(100))
                                .setHeight(220)
                                .setHorizontalAlignment(HorizontalAlignment.CENTER);
                        eventImg.setProperty(com.itextpdf.layout.properties.Property.OBJECT_FIT, com.itextpdf.layout.properties.ObjectFit.COVER);
                        heroSection.add(eventImg);
                    } catch (Exception e) {
                        // Fallback: gradient colored hero
                        heroSection.setBackgroundColor(DARK_CARD).setMinHeight(220);
                    }
                } else {
                    heroSection.setBackgroundColor(DARK_CARD).setMinHeight(220);
                }

                page.add(heroSection);

                // === BRAND BAR with logo + company name ===
                Div brandBar = new Div()
                        .setBackgroundColor(primaryColor)
                        .setPadding(10)
                        .setPaddingLeft(30)
                        .setPaddingRight(30);

                Table brandTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 1}))
                        .useAllAvailableWidth();

                // Logo cell (left)
                Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
                if (logoBytes != null) {
                    try {
                        Image logo = new Image(ImageDataFactory.create(logoBytes))
                                .setMaxWidth(40)
                                .setMaxHeight(32);
                        logoCell.add(logo);
                    } catch (Exception e) {
                        // Skip logo if corrupted
                    }
                }
                brandTable.addCell(logoCell);

                // Company name (center)
                Cell nameCell = new Cell().setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE);
                nameCell.add(new Paragraph(customer.companyName)
                        .setFontSize(14)
                        .setBold()
                        .setFontColor(contrastColor(primaryColor))
                        .setMargin(0));
                brandTable.addCell(nameCell);

                // Ticket indicator (right)
                Cell indicatorCell = new Cell().setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE);
                indicatorCell.add(new Paragraph(String.format("%d / %d", i + 1, order.tickets.size()))
                        .setFontSize(10)
                        .setFontColor(contrastColor(primaryColor))
                        .setMargin(0));
                brandTable.addCell(indicatorCell);

                brandBar.add(brandTable);
                page.add(brandBar);

                // === MAIN CONTENT AREA ===
                Div contentArea = new Div()
                        .setPadding(30)
                        .setPaddingTop(25);

                // Event name
                contentArea.add(new Paragraph(event.name)
                        .setFontSize(28)
                        .setBold()
                        .setFontColor(WHITE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(4));

                // Date & time
                String dateStr = event.eventDate.format(DATE_FMT);
                String timeStr = event.eventDate.format(TIME_FMT);
                if (event.endDate != null) {
                    timeStr += " — " + event.endDate.format(TIME_FMT);
                }

                contentArea.add(new Paragraph(dateStr + "  •  " + timeStr)
                        .setFontSize(12)
                        .setFontColor(GOLD)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(2));

                // Location
                String locationStr = event.location;
                if (event.address != null && !event.address.isEmpty()) {
                    locationStr += "  •  " + event.address;
                }
                contentArea.add(new Paragraph(locationStr)
                        .setFontSize(10)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20));

                // === DIVIDER with perforated effect ===
                contentArea.add(createPerforation());

                // === QR CODE + DETAILS LAYOUT ===
                Table mainLayout = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                        .useAllAvailableWidth()
                        .setMarginTop(20)
                        .setMarginBottom(15);

                // Left: QR code block
                Cell qrCell = new Cell().setBorder(Border.NO_BORDER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setPaddingRight(15);

                // QR container with white background
                Div qrContainer = new Div()
                        .setBackgroundColor(WHITE)
                        .setPadding(12)
                        .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(8));

                byte[] qrImage = qrCodeService.generateQrCodeImage(ticket.qrCodeData);
                Image qrImg = new Image(ImageDataFactory.create(qrImage))
                        .setWidth(160)
                        .setHeight(160)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                qrContainer.add(qrImg);
                qrCell.add(qrContainer);

                qrCell.add(new Paragraph("Scan bij de ingang")
                        .setFontSize(8)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(6));

                mainLayout.addCell(qrCell);

                // Right: visitor & order details
                Cell detailsCell = new Cell().setBorder(Border.NO_BORDER)
                        .setPaddingLeft(15)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE);

                // Visitor info
                addDetailBlock(detailsCell, "BEZOEKER",
                        order.buyerFirstName + " " + order.buyerLastName,
                        order.buyerEmail);

                // Ticket code
                addDetailBlock(detailsCell, "TICKETCODE", ticket.ticketCode, null);

                // Order number
                addDetailBlock(detailsCell, "BESTELLING", order.orderNumber, null);

                mainLayout.addCell(detailsCell);
                contentArea.add(mainLayout);

                // === PRICE BAR ===
                Div priceBar = new Div()
                        .setBackgroundColor(DARK_CARD)
                        .setPadding(12)
                        .setPaddingLeft(20)
                        .setPaddingRight(20)
                        .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(8))
                        .setMarginTop(10);

                Table priceTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                        .useAllAvailableWidth();

                // Ticket price
                Cell p1 = new Cell().setBorder(Border.NO_BORDER);
                p1.add(new Paragraph("TICKETPRIJS").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
                p1.add(new Paragraph("€ " + event.ticketPrice.toPlainString()).setFontSize(13).setBold().setFontColor(TEXT_LIGHT));
                priceTable.addCell(p1);

                // Service fee
                Cell p2 = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER);
                if (order.serviceFeePerTicket.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    p2.add(new Paragraph("SERVICEKOSTEN").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
                    p2.add(new Paragraph("€ " + order.serviceFeePerTicket.toPlainString()).setFontSize(13).setBold().setFontColor(TEXT_LIGHT));
                }
                priceTable.addCell(p2);

                // Total
                Cell p3 = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
                p3.add(new Paragraph("TOTAAL").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
                p3.add(new Paragraph("€ " + order.totalPrice.toPlainString()).setFontSize(16).setBold().setFontColor(primaryColor));
                priceTable.addCell(p3);

                priceBar.add(priceTable);
                contentArea.add(priceBar);

                // === FOOTER ===
                Div footer = new Div().setMarginTop(15);

                footer.add(new Paragraph("Dit ticket is persoonlijk en op naam van " + order.buyerFirstName + " " + order.buyerLastName + ". Neem een geldig legitimatiebewijs mee.")
                        .setFontSize(8)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(4));

                String footerText = customer.companyName;
                if (customer.website != null && !customer.website.isEmpty()) {
                    footerText += "  •  " + customer.website;
                }
                footer.add(new Paragraph(footerText)
                        .setFontSize(7)
                        .setFontColor(TEXT_MUTED)
                        .setTextAlignment(TextAlignment.CENTER));

                contentArea.add(footer);
                page.add(contentArea);
                document.add(page);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van PDF: " + e.getMessage(), 500);
        }
    }

    /**
     * Generates a preview PDF with example data using the customer's branding.
     */
    public byte[] generatePreviewPdf(Customer customer) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(0, 0, 0, 0);

            DeviceRgb primaryColor = parseColor(customer.primaryColor, GOLD);

            byte[] logoBytes = imageLoader.loadImage(customer.logoUrl);

            // === FULL PAGE DARK BACKGROUND ===
            Div page = new Div()
                    .setBackgroundColor(DARK_BG)
                    .setMinHeight(842)
                    .setPadding(0);

            // === TOP HERO SECTION (gradient fallback, no event image) ===
            Div heroSection = new Div()
                    .setBackgroundColor(DARK_CARD)
                    .setMinHeight(220);
            page.add(heroSection);

            // === BRAND BAR ===
            Div brandBar = new Div()
                    .setBackgroundColor(primaryColor)
                    .setPadding(10)
                    .setPaddingLeft(30)
                    .setPaddingRight(30);

            Table brandTable = new Table(UnitValue.createPercentArray(new float[]{1, 3, 1}))
                    .useAllAvailableWidth();

            Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
            if (logoBytes != null) {
                try {
                    Image logo = new Image(ImageDataFactory.create(logoBytes))
                            .setMaxWidth(40).setMaxHeight(32);
                    logoCell.add(logo);
                } catch (Exception ignored) {}
            }
            brandTable.addCell(logoCell);

            Cell nameCell = new Cell().setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            nameCell.add(new Paragraph(customer.companyName)
                    .setFontSize(14).setBold()
                    .setFontColor(contrastColor(primaryColor))
                    .setMargin(0));
            brandTable.addCell(nameCell);

            Cell indicatorCell = new Cell().setBorder(Border.NO_BORDER)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            indicatorCell.add(new Paragraph("1 / 2")
                    .setFontSize(10)
                    .setFontColor(contrastColor(primaryColor))
                    .setMargin(0));
            brandTable.addCell(indicatorCell);

            brandBar.add(brandTable);
            page.add(brandBar);

            // === MAIN CONTENT ===
            Div contentArea = new Div().setPadding(30).setPaddingTop(25);

            contentArea.add(new Paragraph("Voorbeeld Evenement")
                    .setFontSize(28).setBold().setFontColor(WHITE)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));

            contentArea.add(new Paragraph("zaterdag 15 maart 2026  •  20:00")
                    .setFontSize(12).setFontColor(GOLD)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));

            contentArea.add(new Paragraph("Voorbeeld Locatie  •  Voorbeeldstraat 1, 1234 AB Amsterdam")
                    .setFontSize(10).setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

            contentArea.add(createPerforation());

            // === QR CODE + DETAILS ===
            Table mainLayout = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                    .useAllAvailableWidth().setMarginTop(20).setMarginBottom(15);

            Cell qrCell = new Cell().setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setTextAlignment(TextAlignment.CENTER).setPaddingRight(15);

            Div qrContainer = new Div()
                    .setBackgroundColor(WHITE).setPadding(12)
                    .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(8));

            byte[] qrImage = qrCodeService.generateQrCodeImage("VOORBEELD-TICKET-" + customer.id);
            Image qrImg = new Image(ImageDataFactory.create(qrImage))
                    .setWidth(160).setHeight(160)
                    .setHorizontalAlignment(HorizontalAlignment.CENTER);
            qrContainer.add(qrImg);
            qrCell.add(qrContainer);
            qrCell.add(new Paragraph("Scan bij de ingang")
                    .setFontSize(8).setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
            mainLayout.addCell(qrCell);

            Cell detailsCell = new Cell().setBorder(Border.NO_BORDER)
                    .setPaddingLeft(15).setVerticalAlignment(VerticalAlignment.MIDDLE);
            addDetailBlock(detailsCell, "BEZOEKER", "Jan de Vries", "jan@voorbeeld.nl");
            addDetailBlock(detailsCell, "TICKETCODE", "TKT-VOORBEELD", null);
            addDetailBlock(detailsCell, "BESTELLING", "ORD-VOORBEELD", null);
            mainLayout.addCell(detailsCell);
            contentArea.add(mainLayout);

            // === PRICE BAR ===
            Div priceBar = new Div()
                    .setBackgroundColor(DARK_CARD).setPadding(12)
                    .setPaddingLeft(20).setPaddingRight(20)
                    .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(8))
                    .setMarginTop(10);

            Table priceTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                    .useAllAvailableWidth();

            Cell p1 = new Cell().setBorder(Border.NO_BORDER);
            p1.add(new Paragraph("TICKETPRIJS").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
            p1.add(new Paragraph("€ 25.00").setFontSize(13).setBold().setFontColor(TEXT_LIGHT));
            priceTable.addCell(p1);

            Cell p2 = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER);
            p2.add(new Paragraph("SERVICEKOSTEN").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
            p2.add(new Paragraph("€ 2.50").setFontSize(13).setBold().setFontColor(TEXT_LIGHT));
            priceTable.addCell(p2);

            Cell p3 = new Cell().setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT);
            p3.add(new Paragraph("TOTAAL").setFontSize(7).setFontColor(TEXT_MUTED).setBold().setMarginBottom(1));
            p3.add(new Paragraph("€ 27.50").setFontSize(16).setBold().setFontColor(primaryColor));
            priceTable.addCell(p3);

            priceBar.add(priceTable);
            contentArea.add(priceBar);

            // === FOOTER ===
            Div footer = new Div().setMarginTop(15);
            footer.add(new Paragraph("Dit is een voorbeeldticket — dit ticket is niet geldig voor toegang.")
                    .setFontSize(8).setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER).setMarginBottom(4));

            String footerText = customer.companyName;
            if (customer.website != null && !customer.website.isEmpty()) {
                footerText += "  •  " + customer.website;
            }
            footer.add(new Paragraph(footerText)
                    .setFontSize(7).setFontColor(TEXT_MUTED)
                    .setTextAlignment(TextAlignment.CENTER));

            contentArea.add(footer);
            page.add(contentArea);
            document.add(page);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van voorbeeld PDF: " + e.getMessage(), 500);
        }
    }

    private void addDetailBlock(Cell cell, String label, String value, String subValue) {
        cell.add(new Paragraph(label)
                .setFontSize(7)
                .setBold()
                .setFontColor(GOLD_DARK)
                .setMarginBottom(1)
                .setCharacterSpacing(1.2f));
        cell.add(new Paragraph(value)
                .setFontSize(12)
                .setBold()
                .setFontColor(TEXT_LIGHT)
                .setMarginBottom(subValue != null ? 1 : 12));
        if (subValue != null) {
            cell.add(new Paragraph(subValue)
                    .setFontSize(9)
                    .setFontColor(TEXT_MUTED)
                    .setMarginBottom(12));
        }
    }

    private Div createPerforation() {
        // Simulated perforated tear line
        Table perfLine = new Table(UnitValue.createPercentArray(new float[]{1, 6, 1}))
                .useAllAvailableWidth();

        Cell dotLeft = new Cell().setBorder(Border.NO_BORDER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        dotLeft.add(new Paragraph("✂").setFontSize(10).setFontColor(DIVIDER).setMargin(0));
        perfLine.addCell(dotLeft);

        Cell dashCenter = new Cell().setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(DIVIDER, 1))
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        dashCenter.add(new Paragraph(" ").setFontSize(1).setMargin(0));
        perfLine.addCell(dashCenter);

        Cell dotRight = new Cell().setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        dotRight.add(new Paragraph("✂").setFontSize(10).setFontColor(DIVIDER).setMargin(0));
        perfLine.addCell(dotRight);

        Div wrapper = new Div();
        wrapper.add(perfLine);
        return wrapper;
    }

    private DeviceRgb contrastColor(DeviceRgb color) {
        float[] c = color.getColorValue();
        double luminance = 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2];
        return luminance > 0.5 ? new DeviceRgb(15, 23, 42) : WHITE;
    }

    private DeviceRgb parseColor(String hexColor, DeviceRgb defaultColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return defaultColor;
        }
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new DeviceRgb(r, g, b);
        } catch (Exception e) {
            return defaultColor;
        }
    }

    private DeviceRgb lightenColor(DeviceRgb color, float factor) {
        float[] components = color.getColorValue();
        int r = Math.min(255, (int) (components[0] * 255 + (255 - components[0] * 255) * factor));
        int g = Math.min(255, (int) (components[1] * 255 + (255 - components[1] * 255) * factor));
        int b = Math.min(255, (int) (components[2] * 255 + (255 - components[2] * 255) * factor));
        return new DeviceRgb(r, g, b);
    }
}
