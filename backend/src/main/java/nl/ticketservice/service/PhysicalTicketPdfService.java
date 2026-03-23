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
 * Generates printable PDF with 4 physical tickets per A4 page.
 * Each page has a front side (4 tickets) and a back side (4 tickets mirrored),
 * designed for duplex printing on thick paper and professional cutting.
 */
@ApplicationScoped
public class PhysicalTicketPdfService {

    @Inject
    QrCodeService qrCodeService;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("nl-NL"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    // A4: 595 x 842 points. Each ticket quadrant is ~half width, ~half height
    private static final float TICKET_WIDTH = 247.5f;  // (595 - 3*margins) / 2
    private static final float TICKET_HEIGHT = 371f;   // (842 - 3*margins) / 2

    public byte[] generatePhysicalTicketsPdf(Event event, List<Ticket> tickets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(25, 25, 25, 25);

            Customer customer = event.customer;
            DeviceRgb primaryColor = parseColor(customer.primaryColor, new DeviceRgb(41, 128, 185));
            DeviceRgb secondaryColor = parseColor(customer.secondaryColor, new DeviceRgb(44, 62, 80));

            // Process tickets in groups of 4 (one sheet = front + back)
            for (int i = 0; i < tickets.size(); i += 4) {
                if (i > 0) {
                    document.add(new AreaBreak());
                }

                int count = Math.min(4, tickets.size() - i);
                List<Ticket> pageTickets = tickets.subList(i, i + count);

                // === FRONT SIDE (voorkant) ===
                addFrontPage(document, event, customer, pageTickets, primaryColor, secondaryColor);

                // === BACK SIDE (achterkant) - mirrored order for duplex printing ===
                document.add(new AreaBreak());
                addBackPage(document, event, customer, pageTickets, primaryColor, secondaryColor);
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van fysieke tickets PDF: " + e.getMessage(), 500);
        }
    }

    private void addFrontPage(Document document, Event event, Customer customer,
                               List<Ticket> tickets, DeviceRgb primaryColor, DeviceRgb secondaryColor) {
        // 2x2 grid layout
        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setFixedLayout();

        for (int j = 0; j < 4; j++) {
            Cell cell = new Cell()
                    .setBorder(new DashedBorder(new DeviceRgb(200, 200, 200), 0.5f))
                    .setPadding(10)
                    .setMinHeight(TICKET_HEIGHT);

            if (j < tickets.size()) {
                Ticket ticket = tickets.get(j);
                addFrontTicket(cell, event, customer, ticket, primaryColor, secondaryColor);
            }
            // Empty cells for incomplete groups stay blank

            grid.addCell(cell);
        }

        document.add(grid);
    }

    private void addFrontTicket(Cell cell, Event event, Customer customer,
                                 Ticket ticket, DeviceRgb primaryColor, DeviceRgb secondaryColor) {
        // Header with company branding
        Div header = new Div()
                .setBackgroundColor(primaryColor)
                .setPadding(8)
                .setMarginBottom(8);

        header.add(new Paragraph(customer.companyName)
                .setFontSize(11)
                .setBold()
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));

        cell.add(header);

        // Event name
        cell.add(new Paragraph(event.name)
                .setFontSize(14)
                .setBold()
                .setFontColor(secondaryColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        // Date and time
        String dateStr = event.eventDate.format(DATE_FMT);
        String timeStr = event.eventDate.format(TIME_FMT);
        if (event.endDate != null) {
            timeStr += " - " + event.endDate.format(TIME_FMT);
        }

        cell.add(new Paragraph(dateStr)
                .setFontSize(10)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(1));

        cell.add(new Paragraph(timeStr)
                .setFontSize(9)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(6));

        // Location
        cell.add(new Paragraph(event.location)
                .setFontSize(9)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(1));

        if (event.address != null && !event.address.isEmpty()) {
            cell.add(new Paragraph(event.address)
                    .setFontSize(8)
                    .setFontColor(new DeviceRgb(120, 120, 120))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(8));
        }

        // Divider
        Div divider = new Div()
                .setHeight(1)
                .setBackgroundColor(new DeviceRgb(220, 220, 220))
                .setMarginBottom(8);
        cell.add(divider);

        // QR Code
        byte[] qrImage = qrCodeService.generateQrCodeImage(ticket.qrCodeData);
        Image qrImg = new Image(ImageDataFactory.create(qrImage))
                .setWidth(120)
                .setHeight(120)
                .setHorizontalAlignment(HorizontalAlignment.CENTER);
        cell.add(qrImg);

        // Ticket code
        cell.add(new Paragraph(ticket.ticketCode)
                .setFontSize(8)
                .setFontColor(new DeviceRgb(150, 150, 150))
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4));

        // Price
        cell.add(new Paragraph("€ " + event.ticketPrice.toPlainString())
                .setFontSize(11)
                .setBold()
                .setFontColor(primaryColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(4));
    }

    private void addBackPage(Document document, Event event, Customer customer,
                              List<Ticket> tickets, DeviceRgb primaryColor, DeviceRgb secondaryColor) {
        // For duplex printing, tickets must be in mirrored column order:
        // Front: [0][1]    Back: [1][0]
        //        [2][3]          [3][2]
        int[] mirroredOrder = {1, 0, 3, 2};

        Table grid = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                .useAllAvailableWidth()
                .setFixedLayout();

        for (int j = 0; j < 4; j++) {
            int idx = mirroredOrder[j];
            Cell cell = new Cell()
                    .setBorder(new DashedBorder(new DeviceRgb(200, 200, 200), 0.5f))
                    .setPadding(10)
                    .setMinHeight(TICKET_HEIGHT);

            if (idx < tickets.size()) {
                addBackTicket(cell, event, customer, tickets.get(idx), primaryColor, secondaryColor);
            }

            grid.addCell(cell);
        }

        document.add(grid);
    }

    private void addBackTicket(Cell cell, Event event, Customer customer,
                                Ticket ticket, DeviceRgb primaryColor, DeviceRgb secondaryColor) {
        // Full colored background header
        Div header = new Div()
                .setBackgroundColor(primaryColor)
                .setPadding(12)
                .setMarginBottom(15);

        header.add(new Paragraph(customer.companyName)
                .setFontSize(13)
                .setBold()
                .setFontColor(ColorConstants.WHITE)
                .setTextAlignment(TextAlignment.CENTER)
                .setMargin(0));

        if (customer.website != null && !customer.website.isEmpty()) {
            header.add(new Paragraph(customer.website)
                    .setFontSize(8)
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(2)
                    .setMarginBottom(0));
        }

        cell.add(header);

        // Event info block
        cell.add(new Paragraph("EVENEMENT")
                .setFontSize(7)
                .setBold()
                .setFontColor(new DeviceRgb(150, 150, 150))
                .setMarginBottom(2));

        cell.add(new Paragraph(event.name)
                .setFontSize(11)
                .setBold()
                .setFontColor(secondaryColor)
                .setMarginBottom(8));

        // Details table
        Table details = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth()
                .setMarginBottom(10);

        addDetailRow(details, "Datum:", event.eventDate.format(DATE_FMT));
        addDetailRow(details, "Tijd:", event.eventDate.format(TIME_FMT)
                + (event.endDate != null ? " - " + event.endDate.format(TIME_FMT) : ""));
        addDetailRow(details, "Locatie:", event.location);
        if (event.address != null && !event.address.isEmpty()) {
            addDetailRow(details, "Adres:", event.address);
        }
        addDetailRow(details, "Ticketcode:", ticket.ticketCode);

        cell.add(details);

        // Terms / conditions
        Div terms = new Div()
                .setBackgroundColor(new DeviceRgb(245, 245, 245))
                .setPadding(8)
                .setMarginTop(10);

        terms.add(new Paragraph("VOORWAARDEN")
                .setFontSize(7)
                .setBold()
                .setFontColor(new DeviceRgb(150, 150, 150))
                .setMarginBottom(3));

        terms.add(new Paragraph("• Dit ticket is eenmalig geldig en wordt gescand bij de ingang.")
                .setFontSize(7)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setMarginBottom(1));
        terms.add(new Paragraph("• Bewaar dit ticket zorgvuldig, duplicaten worden niet geaccepteerd.")
                .setFontSize(7)
                .setFontColor(new DeviceRgb(100, 100, 100))
                .setMarginBottom(1));
        terms.add(new Paragraph("• Bij verlies of diefstal kan geen nieuw ticket worden verstrekt.")
                .setFontSize(7)
                .setFontColor(new DeviceRgb(100, 100, 100)));

        cell.add(terms);
    }

    private void addDetailRow(Table table, String label, String value) {
        Cell labelCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(1);
        labelCell.add(new Paragraph(label)
                .setFontSize(8)
                .setBold()
                .setFontColor(new DeviceRgb(100, 100, 100)));
        table.addCell(labelCell);

        Cell valueCell = new Cell()
                .setBorder(Border.NO_BORDER)
                .setPadding(1);
        valueCell.add(new Paragraph(value)
                .setFontSize(8));
        table.addCell(valueCell);
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
