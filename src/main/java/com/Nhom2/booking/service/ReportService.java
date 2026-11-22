package com.Nhom2.booking.service;

import com.Nhom2.booking.entity.Booking;
import com.Nhom2.booking.repository.BookingRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class ReportService {

    private final BookingRepository bookingRepository;

    public ReportService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public byte[] exportBookingsExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Bookings");
            Row header = sheet.createRow(0);
            String[] columns = {
                    "ID",
                    "User",
                    "Hotel",
                    "Check in",
                    "Check out",
                    "Status",
                    "Payment",
                    "Total price",
                    "Created at"
            };

            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            List<Booking> bookings = bookingRepository.findAllByOrderByCreatedAtDesc();

            for (int i = 0; i < bookings.size(); i++) {
                Booking booking = bookings.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(booking.getId() != null ? booking.getId() : 0L);
                row.createCell(1).setCellValue(sanitizeCell(booking.getUser() != null ? booking.getUser().getUsername() : ""));
                row.createCell(2).setCellValue(sanitizeCell(booking.getHotel() != null ? booking.getHotel().getName() : ""));
                row.createCell(3).setCellValue(String.valueOf(booking.getCheckInDate()));
                row.createCell(4).setCellValue(String.valueOf(booking.getCheckOutDate()));
                row.createCell(5).setCellValue(String.valueOf(booking.getStatus()));
                row.createCell(6).setCellValue(String.valueOf(booking.getPaymentStatus()));
                row.createCell(7).setCellValue(
                        booking.getTotalPrice() == null ? 0 : booking.getTotalPrice()
                );
                row.createCell(8).setCellValue(String.valueOf(booking.getCreatedAt()));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.setColumnWidth(i, 22 * 256);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Cannot export booking report", ex);
        }
    }

    private static String sanitizeCell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}
