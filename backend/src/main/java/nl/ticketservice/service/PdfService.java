package nl.ticketservice.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.Color;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'om' HH:mm", java.util.Locale.forLanguageTag("nl-NL"));
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public byte[] generateOrderPdf(TicketOrder order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(40, 40, 40, 40);

            Event event = order.event;
            Customer customer = event.customer;

            DeviceRgb primaryColor = parseColor(customer.primaryColor, new DeviceRgb(41, 128, 185));
            DeviceRgb lightBg = lightenColor(primaryColor, 0.92f);

            for (int i = 0; i < order.tickets.size(); i++) {
                Ticket ticket = order.tickets.get(i);

                if (i > 0) {
                    document.add(new AreaBreak());
                }

                // === Header bar with company name ===
                Div headerBar = new Div()
                        .setBackgroundColor(primaryColor)
                        .setPadding(15)
                        .setMarginBottom(5);

                headerBar.add(new Paragraph(customer.companyName)
                        .setFontSize(22)
                        .setBold()
                        .setFontColor(ColorConstants.WHITE)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMargin(0));

                document.add(headerBar);

                // === Ticket number indicator ===
                Div ticketIndicator = new Div()
                        .setBackgroundColor(lightBg)
                        .setPadding(8)
                        .setMarginBottom(20);

                ticketIndicator.add(new Paragraph("Ticket " + (i + 1) + " van " + order.tickets.size())
                        .setFontSize(12)
                        .setBold()
                        .setFontColor(primaryColor)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMargin(0));

                document.add(ticketIndicator);

                // === Event title ===
                document.add(new Paragraph(event.name)
                        .setFontSize(24)
                        .setBold()
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(5));

                // === Event date ===
                document.add(new Paragraph(event.eventDate.format(DATE_FMT))
                        .setFontSize(13)
                        .setFontColor(new DeviceRgb(100, 100, 100))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginBottom(20));

                // === Main content: Info left, QR right ===
                Table mainLayout = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                        .useAllAvailableWidth()
                        .setMarginBottom(20);

                // Left column: order & event details
                Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(15);

                // Bezoeker info block
                leftCell.add(new Paragraph("Bezoeker")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setBold()
                        .setMarginBottom(2));
                leftCell.add(new Paragraph(order.buyerFirstName + " " + order.buyerLastName)
                        .setFontSize(14)
                        .setBold()
                        .setMarginBottom(2));
                leftCell.add(new Paragraph(order.buyerEmail)
                        .setFontSize(11)
                        .setFontColor(new DeviceRgb(100, 100, 100))
                        .setMarginBottom(15));

                // Locatie info block
                leftCell.add(new Paragraph("Locatie")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setBold()
                        .setMarginBottom(2));
                leftCell.add(new Paragraph(event.location)
                        .setFontSize(12)
                        .setBold()
                        .setMarginBottom(2));
                if (event.address != null && !event.address.isEmpty()) {
                    leftCell.add(new Paragraph(event.address)
                            .setFontSize(11)
                            .setFontColor(new DeviceRgb(100, 100, 100))
                            .setMarginBottom(15));
                } else {
                    leftCell.add(new Paragraph("").setMarginBottom(15));
                }

                // Bestelling info block
                leftCell.add(new Paragraph("Bestelling")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setBold()
                        .setMarginBottom(2));
                leftCell.add(new Paragraph(order.orderNumber)
                        .setFontSize(11)
                        .setMarginBottom(2));
                leftCell.add(new Paragraph("Ticketcode: " + ticket.ticketCode)
                        .setFontSize(10)
                        .setFontColor(new DeviceRgb(100, 100, 100)));

                mainLayout.addCell(leftCell);

                // Right column: QR code
                Cell rightCell = new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setTextAlignment(TextAlignment.CENTER);

                byte[] qrImage = qrCodeService.generateQrCodeImage(ticket.qrCodeData);
                Image qrImg = new Image(ImageDataFactory.create(qrImage))
                        .setWidth(180)
                        .setHeight(180)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                rightCell.add(qrImg);

                rightCell.add(new Paragraph("Toon deze QR-code bij de ingang")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(120, 120, 120))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(5));

                mainLayout.addCell(rightCell);
                document.add(mainLayout);

                // === Divider line ===
                Div divider = new Div()
                        .setHeight(1)
                        .setBackgroundColor(new DeviceRgb(220, 220, 220))
                        .setMarginTop(10)
                        .setMarginBottom(15);
                document.add(divider);

                // === Price summary (compact) ===
                Table priceTable = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                        .useAllAvailableWidth()
                        .setMarginBottom(20);

                Cell priceCell1 = new Cell().setBorder(Border.NO_BORDER);
                priceCell1.add(new Paragraph("Ticketprijs")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setMarginBottom(1));
                priceCell1.add(new Paragraph("€ " + event.ticketPrice.toPlainString())
                        .setFontSize(12)
                        .setBold());
                priceTable.addCell(priceCell1);

                if (order.serviceFeePerTicket.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Cell priceCell2 = new Cell().setBorder(Border.NO_BORDER);
                    priceCell2.add(new Paragraph("Servicekosten")
                            .setFontSize(9)
                            .setFontColor(new DeviceRgb(150, 150, 150))
                            .setMarginBottom(1));
                    priceCell2.add(new Paragraph("€ " + order.serviceFeePerTicket.toPlainString())
                            .setFontSize(12)
                            .setBold());
                    priceTable.addCell(priceCell2);
                } else {
                    priceTable.addCell(new Cell().setBorder(Border.NO_BORDER));
                }

                Cell priceCell3 = new Cell().setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.RIGHT);
                priceCell3.add(new Paragraph("Totaal bestelling")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setMarginBottom(1));
                priceCell3.add(new Paragraph("€ " + order.totalPrice.toPlainString())
                        .setFontSize(14)
                        .setBold()
                        .setFontColor(primaryColor));
                priceTable.addCell(priceCell3);

                document.add(priceTable);

                // === Footer ===
                document.add(new Paragraph("Dit ticket is persoonlijk en op naam van " + order.buyerFirstName + " " + order.buyerLastName + ". Neem een geldig legitimatiebewijs mee.")
                        .setFontSize(9)
                        .setFontColor(new DeviceRgb(150, 150, 150))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(10));

                document.add(new Paragraph("Gegenereerd door " + customer.companyName + " • " + order.orderNumber)
                        .setFontSize(8)
                        .setFontColor(new DeviceRgb(180, 180, 180))
                        .setTextAlignment(TextAlignment.CENTER)
                        .setMarginTop(5));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van PDF: " + e.getMessage(), 500);
        }
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
