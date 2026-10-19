package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.entity.BookingRoom;
import com.Nhom2.booking.entity.Hotel;
import com.Nhom2.booking.entity.Room;
import com.Nhom2.booking.entity.User;
import com.Nhom2.booking.enums.BookingStatus;
import com.Nhom2.booking.enums.PaymentStatus;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfGeneratorTest {

    private final InvoicePdfGenerator generator =
            new InvoicePdfGenerator(Clock.fixed(Instant.parse("2026-03-05T03:00:00Z"), ZoneOffset.UTC));

    @Test
    void producesAPdfWithBookingDetailsInVietnamese() throws Exception {
        byte[] pdf = generator.generate(sampleBooking());

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String text = extractText(pdf);
        assertThat(text)
                .contains("#42")
                .contains("Khách sạn Biển Xanh")
                .contains("Phòng Deluxe hướng biển")
                .contains("05/03/2026")
                .contains("10/03/2026")
                .contains("3.000.000")
                .contains("alice@example.com");
    }

    @Test
    void lineTotalsUseTheStoredBookingPriceTimesQuantityAndNights() throws Exception {
        String text = extractText(generator.generate(sampleBooking()));

        // 2 rooms x 500.000 x 3 nights
        assertThat(text).contains("500.000").contains("3.000.000");
    }

    private static String extractText(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            StringBuilder text = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        } finally {
            reader.close();
        }
    }

    static Booking sampleBooking() {
        User user = new User();
        user.setUsername("alice");
        user.setEmail("alice@example.com");

        Hotel hotel = new Hotel();
        hotel.setId(10L);
        hotel.setName("Khách sạn Biển Xanh");
        hotel.setAddress("12 Trần Phú");
        hotel.setCity("Nha Trang");

        Room room = new Room();
        room.setName("Phòng Deluxe hướng biển");
        room.setPricePerNight(650_000.0);

        BookingRoom line = new BookingRoom();
        line.setRoom(room);
        line.setQuantity(2);
        line.setPrice(500_000.0);

        Booking booking = new Booking();
        booking.setId(42L);
        booking.setUser(user);
        booking.setHotel(hotel);
        booking.setCheckInDate(LocalDate.of(2026, 3, 7));
        booking.setCheckOutDate(LocalDate.of(2026, 3, 10));
        booking.setTotalPrice(3_000_000.0);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentStatus(PaymentStatus.PAID);
        booking.setBookingRooms(List.of(line));
        return booking;
    }
}
