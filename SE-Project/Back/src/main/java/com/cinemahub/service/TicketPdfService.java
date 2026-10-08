package com.cinemahub.service;

import com.cinemahub.model.Booking;
import com.cinemahub.model.BookingFoodItem;
import com.cinemahub.model.Ticket;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Downloadable PDF ticket (matches the Proposal Document's "Automated Booking Receipt"
 * minor function) - built only for a CONFIRMED booking, see BookingController#downloadTicket.
 * Reuses the exact QR PNG bytes QrCodeService already generates for the on-screen ticket;
 * this class never creates a second/different QR code for the same booking.
 */
@Service
public class TicketPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    public byte[] buildTicketPdf(Booking booking, Ticket ticket, byte[] qrCodePng) {
        return buildTicketPdf(booking, ticket, qrCodePng, List.of());
    }

    /** Same ticket, also listing any food and parking added to the booking. */
    public byte[] buildTicketPdf(Booking booking, Ticket ticket, byte[] qrCodePng, List<BookingFoodItem> foodLines) {
        try {
            Document document = new Document();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
            Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 12);
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9);

            document.add(new Paragraph("CinemaX Lanka - Ticket", titleFont));
            document.add(new Paragraph(" "));

            String seatList = booking.getBookingSeats().stream()
                    .map(bs -> bs.getSeat().getSeatCode())
                    .collect(Collectors.joining(", "));

            document.add(field("Movie", booking.getShowtime().getMovie().getTitle(), labelFont, valueFont));
            document.add(field("Showtime", booking.getShowtime().getDateTime().format(DATE_FORMAT), labelFont, valueFont));
            document.add(field("Hall", booking.getShowtime().getCinemaHall().getName(), labelFont, valueFont));
            document.add(field("Seats", seatList, labelFont, valueFont));
            if (foodLines != null && !foodLines.isEmpty()) {
                String food = foodLines.stream()
                        .map(line -> line.getQuantity() + " x " + line.getItemName())
                        .collect(Collectors.joining(", "));
                document.add(field("Food", food, labelFont, valueFont));
            }
            if (booking.isParkingSelected()) {
                document.add(field("Parking", booking.getParkingLabel() != null ? booking.getParkingLabel() : "Included",
                        labelFont, valueFont));
            }
            document.add(field("Booking Reference", "#" + booking.getId(), labelFont, valueFont));
            document.add(field("Total Paid", "Rs. " + booking.getTotalPrice(), labelFont, valueFont));
            document.add(new Paragraph(" "));

            Image qrImage = Image.getInstance(qrCodePng);
            qrImage.setAlignment(Element.ALIGN_CENTER);
            document.add(qrImage);

            document.add(new Paragraph(" "));
            document.add(new Paragraph("Present this QR code at the entrance. Ticket code: " + ticket.getQrCodeData(), footerFont));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ticket PDF", e);
        }
    }

    private Paragraph field(String label, String value, Font labelFont, Font valueFont) {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(label + ": ", labelFont));
        paragraph.add(new Chunk(value == null ? "" : value, valueFont));
        return paragraph;
    }
}
