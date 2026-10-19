package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.BookingRoom;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/** Renders a booking as a one-page PDF invoice. Labels are Vietnamese with English in brackets. */
@Component
public class InvoicePdfGenerator {

    // The standard PDF fonts have no Vietnamese glyphs, so a Unicode font is embedded (SIL OFL, see fonts/).
    private static final String REGULAR_FONT = "fonts/NotoSans-Regular.ttf";
    private static final String BOLD_FONT = "fonts/NotoSans-Bold.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color BRAND_BLUE = new Color(0, 53, 128);
    private static final Color HEADER_GREY = new Color(235, 240, 248);

    private final Clock clock;
    private final BaseFont regular;
    private final BaseFont bold;

    public InvoicePdfGenerator(Clock clock) {
        this.clock = clock;
        this.regular = loadFont(REGULAR_FONT);
        this.bold = loadFont(BOLD_FONT);
    }

    public byte[] generate(Booking booking) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 48);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            addHeader(document, booking);
            addParties(document, booking);
            addRoomLines(document, booking);
            addFooter(document, booking);
        } catch (DocumentException e) {
            throw new IllegalStateException("Không thể tạo hóa đơn PDF cho booking #" + booking.getId(), e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private void addHeader(Document document, Booking booking) throws DocumentException {
        Paragraph brand = new Paragraph("Booking Nhóm 2", font(bold, 20, BRAND_BLUE));
        document.add(brand);
        Paragraph title = new Paragraph("HÓA ĐƠN THANH TOÁN (INVOICE)", font(bold, 14, Color.BLACK));
        title.setSpacingBefore(6);
        document.add(title);
        document.add(new Paragraph(
                "Mã đơn (Booking): #" + booking.getId()
                        + "     Ngày xuất (Issued): " + LocalDate.now(clock).format(DATE),
                font(regular, 10, Color.DARK_GRAY)));
    }

    private void addParties(Document document, Booking booking) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1, 2});
        table.setWidthPercentage(100);
        table.setSpacingBefore(18);

        String customer = booking.getUser() == null ? "" : booking.getUser().getUsername()
                + (booking.getUser().getEmail() == null ? "" : " (" + booking.getUser().getEmail() + ")");
        String hotel = booking.getHotel() == null ? "" : joinNonBlank(
                booking.getHotel().getName(), booking.getHotel().getAddress(), booking.getHotel().getCity());

        addInfoRow(table, "Khách hàng (Guest)", customer);
        addInfoRow(table, "Khách sạn (Hotel)", hotel);
        addInfoRow(table, "Nhận phòng (Check-in)", format(booking.getCheckInDate()));
        addInfoRow(table, "Trả phòng (Check-out)", format(booking.getCheckOutDate()));
        addInfoRow(table, "Số đêm (Nights)", String.valueOf(nights(booking)));
        document.add(table);
    }

    private void addRoomLines(Document document, Booking booking) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{4, 1, 2, 1, 2});
        table.setWidthPercentage(100);
        table.setSpacingBefore(18);

        for (String heading : List.of("Phòng (Room)", "SL (Qty)", "Giá/đêm (Rate)", "Đêm", "Thành tiền (Amount)")) {
            PdfPCell cell = cell(heading, font(bold, 10, Color.BLACK), Element.ALIGN_CENTER);
            cell.setBackgroundColor(HEADER_GREY);
            table.addCell(cell);
        }

        long nights = nights(booking);
        List<BookingRoom> lines = booking.getBookingRooms() == null ? List.of() : booking.getBookingRooms();
        for (BookingRoom line : lines) {
            double rate = line.getPrice() != null ? line.getPrice() : line.getRoom().getPricePerNight();
            int quantity = line.getQuantity() == null ? 0 : line.getQuantity();
            table.addCell(cell(line.getRoom() == null ? "" : line.getRoom().getName(), font(regular, 10, Color.BLACK), Element.ALIGN_LEFT));
            table.addCell(cell(String.valueOf(quantity), font(regular, 10, Color.BLACK), Element.ALIGN_CENTER));
            table.addCell(cell(money(rate), font(regular, 10, Color.BLACK), Element.ALIGN_RIGHT));
            table.addCell(cell(String.valueOf(nights), font(regular, 10, Color.BLACK), Element.ALIGN_CENTER));
            table.addCell(cell(money(rate * quantity * nights), font(regular, 10, Color.BLACK), Element.ALIGN_RIGHT));
        }

        PdfPCell totalLabel = cell("TỔNG CỘNG (TOTAL)", font(bold, 11, Color.BLACK), Element.ALIGN_RIGHT);
        totalLabel.setColspan(4);
        table.addCell(totalLabel);
        table.addCell(cell(money(booking.getTotalPrice() == null ? 0 : booking.getTotalPrice()),
                font(bold, 11, BRAND_BLUE), Element.ALIGN_RIGHT));
        document.add(table);
    }

    private void addFooter(Document document, Booking booking) throws DocumentException {
        Paragraph paid = new Paragraph(
                "Trạng thái: ĐÃ THANH TOÁN (PAID) qua chuyển khoản, nội dung \"DH " + booking.getId() + "\".",
                font(regular, 10, Color.DARK_GRAY));
        paid.setSpacingBefore(18);
        document.add(paid);
        document.add(new Paragraph(
                "Cảm ơn quý khách đã đặt phòng. Vui lòng xuất trình hóa đơn này khi nhận phòng.",
                font(regular, 10, Color.DARK_GRAY)));
    }

    private void addInfoRow(PdfPTable table, String label, String value) {
        table.addCell(cell(label, font(bold, 10, Color.BLACK), Element.ALIGN_LEFT));
        table.addCell(cell(value, font(regular, 10, Color.BLACK), Element.ALIGN_LEFT));
    }

    private static PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(6);
        return cell;
    }

    private static Font font(BaseFont baseFont, float size, Color color) {
        return new Font(baseFont, size, Font.NORMAL, color);
    }

    private static long nights(Booking booking) {
        if (booking.getCheckInDate() == null || booking.getCheckOutDate() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
    }

    private static String format(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    private static String money(double amount) {
        return NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(Math.round(amount)) + " VND";
    }

    private static String joinNonBlank(String... parts) {
        return String.join(", ", java.util.Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).toList());
    }

    private static BaseFont loadFont(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException("Không tải được font hóa đơn: " + path, e);
        }
    }
}
