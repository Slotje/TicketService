package nl.ticketservice.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.DashedBorder;
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
import nl.ticketservice.exception.TicketServiceException;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Generates premium printable PDF with 4 physical tickets per A4 page.
 * Features company logo, event image, branded colors, and professional layout.
 * Designed for duplex printing on thick paper (250g/m²+) with cut marks.
 */
@ApplicationScoped
public class PhysicalTicketPdfService {

    @Inject
    QrCodeService qrCodeService;

    @Inject
    ImageLoaderService imageLoader;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // A4: 595 x 842 points. 2x2 grid with margins
    private static final float TICKET_HEIGHT = 371f;

    // Premium color palette
    private static final DeviceRgb DARK_BG = new DeviceRgb(15, 23, 42);
    private static final DeviceRgb DARK_CARD = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb TEXT_MUTED = new DeviceRgb(148, 163, 184);
    private static final DeviceRgb TEXT_LIGHT = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb GOLD = new DeviceRgb(212, 168, 83);
    private static final DeviceRgb DIVIDER = new DeviceRgb(51, 65, 85);
    private static final DeviceRgb WHITE = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb CUT_LINE = new DeviceRgb(200, 200, 200);

    public byte[] generatePhysicalTicketsPdf(Event event, List<Ticket> tickets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(25, 25, 25, 25);

            Customer customer = event.customer;
            DeviceRgb primaryColor = parseColor(customer.primaryColor, GOLD);
            DeviceRgb secondaryColor = parseColor(customer.secondaryColor, DARK_BG);

            // Load images once
            byte[] logoBytes = imageLoader.loadImage(customer.logoUrl);
            byte[] eventImageBytes = imageLoader.loadImage(event.imageUrl);

            // Process tickets in groups of 4
            for (int i = 0; i < tickets.size(); i += 4) {
                if (i > 0) {
                    document.add(new AreaBreak());
                }

                int count = Math.min(4, tickets.size() - i);
                List<Ticket> pageTickets = tickets.subList(i, i + count);

                // FRONT SIDE
                addFrontPage(document, event, customer, pageTickets, primaryColor, secondaryColor, logoBytes, eventImageBytes);

                // BACK SIDE (mirrored for duplex)
                document.add(new AreaBreak());
                addBackPage(document, event, customer, pageTickets, primaryColor, secondaryColor, logoBytes);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van fysieke tickets PDF: " + e.getMessage(), 500);
        }
    }

    private void addFrontPage(Document document, Event event, Customer customer,
                               List<Ticket> tickets, DeviceRgb primaryColor, DeviceRgb secondaryColor,
                               byte[] logoBytes, byte[] eventImageBytes) {
        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setFixedLayout();

        for (int j = 0; j < 4; j++) {
            Cell cell = new Cell()
                    .setBorder(new DashedBorder(CUT_LINE, 0.5f))
                    .setPadding(0)
                    .setMinHeight(TICKET_HEIGHT);

            if (j < tickets.size()) {
                addFrontTicket(cell, event, customer, tickets.get(j), primaryColor, secondaryColor, logoBytes, eventImageBytes);
            }

            grid.addCell(cell);
        }

        document.add(grid);
    }

    private void addFrontTicket(Cell cell, Event event, Customer customer, Ticket ticket,
                                 DeviceRgb primaryColor, DeviceRgb secondaryColor,
                                 byte[] logoBytes, byte[] eventImageBytes) {
        // Dark background for entire ticket
        Div ticketDiv = new Div()
                .setBackgroundColor(DARK_BG)
                .setMinHeight(TICKET_HEIGHT)
                .setPadding(0);

        // === Mini hero with event image or gradient ===
        Div heroArea = new Div().setMinHeight(65).setPadding(0);
        if (eventImageBytes != null) {
            try {
                Image eventImg = new Image(ImageDataFactory.create(eventImageBytes))
                        .setWidth(UnitValue.createPercentValue(100))
                        .setHeight(65)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                eventImg.setProperty(com.itextpdf.layout.properties.Property.OBJECT_FIT, com.itextpdf.layout.properties.ObjectFit.COVER);
                heroArea.add(eventImg);
            } catch (Exception e) {
                heroArea.setBackgroundColor(DARK_CARD).setMinHeight(65);
            }
        } else {
            heroArea.setBackgroundColor(DARK_CARD).setMinHeight(65);
        }
        ticketDiv.add(heroArea);

        // === Brand strip with logo + company ===
        Div brandStrip = new Div()
                .setBackgroundColor(primaryColor)
                .setPadding(5)
                .setPaddingLeft(8)
                .setPaddingRight(8);

        Table brandRow = new Table(UnitValue.createPercentArray(new float[]{20, 80}))
                .useAllAvailableWidth();

        Cell logoCellBrand = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (logoBytes != null) {
            try {
                Image logo = new Image(ImageDataFactory.create(logoBytes))
                        .setMaxWidth(22)
                        .setMaxHeight(18);
                logoCellBrand.add(logo);
            } catch (Exception e) { /* skip */ }
        }
        brandRow.addCell(logoCellBrand);

        Cell nameCellBrand = new Cell().setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT)
                .setVerticalAlignment(VerticalAlignment.MIDDLE);
        nameCellBrand.add(new Paragraph(customer.companyName)
                .setFontSize(8)
                .setBold()
                .setFontColor(contrastColor(primaryColor))
                .setMargin(0));
        brandRow.addCell(nameCellBrand);

        brandStrip.add(brandRow);
        ticketDiv.add(brandStrip);

        // === Content area ===
        Div content = new Div().setPadding(8).setPaddingTop(6);

        // Event name
        content.add(new Paragraph(event.name)
                .setFontSize(12)
                .setBold()
                .setFontColor(WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(3));

        // Date + time
        String dateStr = event.eventDate.format(DATE_FMT);
        String timeStr = event.eventDate.format(TIME_FMT);
        if (event.endDate != null) {
            timeStr += " — " + event.endDate.format(TIME_FMT);
        }

        content.add(new Paragraph(dateStr + "  •  " + timeStr)
                .setFontSize(7)
                .setFontColor(GOLD)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(2));

        // Location
        content.add(new Paragraph(event.location)
                .setFontSize(7)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(6));

        // Thin divider
        content.add(new Div().setHeight(0.5f).setBackgroundColor(DIVIDER).setMarginBottom(6));

        // QR Code centered with white bg
        Div qrBox = new Div()
                .setBackgroundColor(WHITE)
                .setPadding(6)
                .setWidth(105)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(4));

        byte[] qrImage = qrCodeService.generateQrCodeImage(ticket.qrCodeData);
        Image qrImg = new Image(ImageDataFactory.create(qrImage))
                .setWidth(93)
                .setHeight(93)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        qrBox.add(qrImg);
        content.add(qrBox);

        // Ticket code
        content.add(new Paragraph(ticket.ticketCode)
                .setFontSize(6)
                .setFontColor(TEXT_MUTED)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(3)
                .setMarginBottom(3));

        // Price badge
        Div priceBadge = new Div()
                .setBackgroundColor(DARK_CARD)
                .setPadding(4)
                .setPaddingLeft(12)
                .setPaddingRight(12)
                .setHorizontalAlignment(HorizontalAlignment.CENTER)
                .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(12));

        priceBadge.add(new Paragraph("€ " + event.ticketPrice.toPlainString())
                .setFontSize(10)
                .setBold()
                .setFontColor(primaryColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));

        content.add(priceBadge);

        ticketDiv.add(content);
        cell.add(ticketDiv);
    }

    private void addBackPage(Document document, Event event, Customer customer,
                              List<Ticket> tickets, DeviceRgb primaryColor, DeviceRgb secondaryColor,
                              byte[] logoBytes) {
        // Mirrored for duplex: [1][0] / [3][2]
        int[] mirroredOrder = {1, 0, 3, 2};

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setFixedLayout();

        for (int j = 0; j < 4; j++) {
            int idx = mirroredOrder[j];
            Cell cell = new Cell()
                    .setBorder(new DashedBorder(CUT_LINE, 0.5f))
                    .setPadding(0)
                    .setMinHeight(TICKET_HEIGHT);

            if (idx < tickets.size()) {
                addBackTicket(cell, event, customer, tickets.get(idx), primaryColor, secondaryColor, logoBytes);
            }

            grid.addCell(cell);
        }

        document.add(grid);
    }

    private void addBackTicket(Cell cell, Event event, Customer customer, Ticket ticket,
                                DeviceRgb primaryColor, DeviceRgb secondaryColor,
                                byte[] logoBytes) {
        Div ticketDiv = new Div()
                .setBackgroundColor(DARK_BG)
                .setMinHeight(TICKET_HEIGHT)
                .setPadding(0);

        // === Header with logo + company ===
        Div header = new Div()
                .setBackgroundColor(primaryColor)
                .setPadding(12)
                .setPaddingLeft(15)
                .setPaddingRight(15)
                .setMarginBottom(12);

        if (logoBytes != null) {
            try {
                Image logo = new Image(ImageDataFactory.create(logoBytes))
                        .setMaxWidth(36)
                        .setMaxHeight(28)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER)
                        .setMarginBottom(4);
                header.add(logo);
            } catch (Exception e) { /* skip */ }
        }

        header.add(new Paragraph(customer.companyName)
                .setFontSize(12)
                .setBold()
                .setFontColor(contrastColor(primaryColor))
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));

        if (customer.website != null && !customer.website.isEmpty()) {
            header.add(new Paragraph(customer.website)
                    .setFontSize(7)
                    .setFontColor(contrastColor(primaryColor))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2)
                    .setMarginBottom(0));
        }

        ticketDiv.add(header);

        // === Event details ===
        Div details = new Div().setPadding(12).setPaddingTop(0);

        details.add(new Paragraph("EVENEMENT")
                .setFontSize(6)
                .setBold()
                .setFontColor(GOLD)
                .setCharacterSpacing(1.5f)
                .setMarginBottom(3));

        details.add(new Paragraph(event.name)
                .setFontSize(11)
                .setBold()
                .setFontColor(WHITE)
                .setMarginBottom(10));

        // Details table
        Table detailsTable = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        addDetailRow(detailsTable, "Datum", event.eventDate.format(DATE_FMT));
        String timeInfo = event.eventDate.format(TIME_FMT);
        if (event.endDate != null) {
            timeInfo += " — " + event.endDate.format(TIME_FMT);
        }
        addDetailRow(detailsTable, "Tijd", timeInfo);
        addDetailRow(detailsTable, "Locatie", event.location);
        if (event.address != null && !event.address.isEmpty()) {
            addDetailRow(detailsTable, "Adres", event.address);
        }
        addDetailRow(detailsTable, "Ticketcode", ticket.ticketCode);

        details.add(detailsTable);

        // === Terms/conditions ===
        Div terms = new Div()
                .setBackgroundColor(DARK_CARD)
                .setPadding(8)
                .setBorderRadius(new com.itextpdf.layout.properties.BorderRadius(4))
                .setMarginTop(8);

        terms.add(new Paragraph("VOORWAARDEN")
                .setFontSize(6)
                .setBold()
                .setFontColor(GOLD)
                .setCharacterSpacing(1f)
                .setMarginBottom(3));

        String[] conditions = {
                "Dit ticket is eenmalig geldig en wordt gescand bij de ingang.",
                "Bewaar dit ticket zorgvuldig, duplicaten worden niet geaccepteerd.",
                "Bij verlies of diefstal kan geen nieuw ticket worden verstrekt."
        };
        for (String condition : conditions) {
            terms.add(new Paragraph("•  " + condition)
                    .setFontSize(6)
                    .setFontColor(TEXT_MUTED)
                    .setMarginBottom(1));
        }

        details.add(terms);
        ticketDiv.add(details);
        cell.add(ticketDiv);
    }

    private void addDetailRow(Table table, String label, String value) {
        Cell labelCell = new Cell().setBorder(Border.NO_BORDER).setPadding(2);
        labelCell.add(new Paragraph(label)
                .setFontSize(7)
                .setBold()
                .setFontColor(TEXT_MUTED));
        table.addCell(labelCell);

        Cell valueCell = new Cell().setBorder(Border.NO_BORDER).setPadding(2);
        valueCell.add(new Paragraph(value)
                .setFontSize(7)
                .setFontColor(TEXT_LIGHT));
        table.addCell(valueCell);
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
}
