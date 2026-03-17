package nl.ticketservice.service;

import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public byte[] generateOrderPdf(TicketOrder order) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4);
            document.setMargins(30, 30, 30, 30);

            Event event = order.event;
            Customer customer = event.customer;

            DeviceRgb primaryColor = parseColor(customer.primaryColor, new DeviceRgb(41, 128, 185));

            // Header
            Paragraph header = new Paragraph(customer.companyName)
                    .setFontSize(24)
                    .setBold()
                    .setFontColor(primaryColor)
                    .setTextAlignment(TextAlignment.CENTER);
            document.add(header);

            Paragraph eventTitle = new Paragraph(event.name)
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(20);
            document.add(eventTitle);

            // Order info
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 2}))
                    .useAllAvailableWidth()
                    .setMarginBottom(20);

            addInfoRow(infoTable, "Bestelnummer:", order.orderNumber);
            addInfoRow(infoTable, "Naam:", order.buyerName);
            addInfoRow(infoTable, "E-mail:", order.buyerEmail);
            addInfoRow(infoTable, "Datum evenement:", event.eventDate.format(DATE_FMT));
            addInfoRow(infoTable, "Locatie:", event.location);
            if (event.address != null) {
                addInfoRow(infoTable, "Adres:", event.address);
            }
            addInfoRow(infoTable, "Aantal tickets:", String.valueOf(order.quantity));
            addInfoRow(infoTable, "Ticketprijs:", "EUR " + event.ticketPrice.toPlainString() + " per ticket");
            if (order.serviceFeePerTicket.compareTo(java.math.BigDecimal.ZERO) > 0) {
                addInfoRow(infoTable, "Servicekosten:", "EUR " + order.serviceFeePerTicket.toPlainString() + " per ticket");
                addInfoRow(infoTable, "Totaal servicekosten:", "EUR " + order.totalServiceFee.toPlainString());
            }
            addInfoRow(infoTable, "Totaalprijs:", "EUR " + order.totalPrice.toPlainString());

            document.add(infoTable);

            // Tickets with QR codes
            for (int i = 0; i < order.tickets.size(); i++) {
                Ticket ticket = order.tickets.get(i);

                if (i > 0) {
                    document.add(new AreaBreak());
                }

                Div ticketDiv = new Div()
                        .setBorder(new SolidBorder(primaryColor, 2))
                        .setPadding(20)
                        .setMarginBottom(20);

                ticketDiv.add(new Paragraph("Ticket " + (i + 1) + " van " + order.tickets.size())
                        .setFontSize(14)
                        .setBold()
                        .setFontColor(primaryColor));

                ticketDiv.add(new Paragraph(event.name)
                        .setFontSize(16)
                        .setBold());

                ticketDiv.add(new Paragraph("Ticketcode: " + ticket.ticketCode)
                        .setFontSize(11));

                ticketDiv.add(new Paragraph("Datum: " + event.eventDate.format(DATE_FMT))
                        .setFontSize(11));

                ticketDiv.add(new Paragraph("Locatie: " + event.location)
                        .setFontSize(11));

                // QR Code
                byte[] qrImage = qrCodeService.generateQrCodeImage(ticket.qrCodeData);
                Image qrImg = new Image(ImageDataFactory.create(qrImage))
                        .setWidth(150)
                        .setHeight(150)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER)
                        .setMarginTop(10);
                ticketDiv.add(qrImg);

                ticketDiv.add(new Paragraph(ticket.qrCodeData)
                        .setFontSize(8)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.GRAY));

                document.add(ticketDiv);
            }

            // Footer
            document.add(new Paragraph("Dit ticket is gegenereerd door " + customer.companyName)
                    .setFontSize(8)
                    .setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginTop(20));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new TicketServiceException("Fout bij het genereren van PDF: " + e.getMessage(), 500);
        }
    }

    private void addInfoRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(11))
                .setBorder(null));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(11))
                .setBorder(null));
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
