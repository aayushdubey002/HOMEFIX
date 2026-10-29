package com.homefix.api.service;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.Invoice;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoicePdfServiceTest {

    private final InvoicePdfService invoicePdfService = new InvoicePdfService();

    @Test
    void invoicePdfContainsSavedPriceBreakdownAndPaymentStatus() throws Exception {
        BookingEntity booking = new BookingEntity(
                "HF-INVOICE-102", "Invoice Customer", "PLUMBING",
                "Invoice Provider", "COMPLETED", null);
        booking.setPhone("9876543210");
        booking.setAddress("12 Main Street");
        booking.setPreferredDate("2026-12-02");

        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setInvoiceNumber("HF-HF-INVOICE-102");
        invoice.setSubtotal(new BigDecimal("320.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("320.00"));

        byte[] pdf = invoicePdfService.create(
                invoice,
                new BigDecimal("300.00"),
                new BigDecimal("20.00"),
                java.util.List.of(new InvoicePdfService.ChargeLine(
                        new BigDecimal("20.00"), "Tools: Replacement wrench")),
                "UPI",
                "PENDING",
                "pay_gateway_123",
                null,
                java.util.List.of(new InvoicePdfService.RefundLine(
                        new BigDecimal("10.00"), "PENDING", "", java.time.Instant.now())));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("HF-HF-INVOICE-102"));
            assertTrue(text.contains("Original estimate: INR 300.00"));
            assertTrue(text.contains("Approved additional charges: INR 20.00"));
            assertTrue(text.contains("Tools: Replacement wrench: INR 20.00"));
            assertTrue(text.contains("Final payable amount: INR 320.00"));
            assertTrue(text.contains("Discount: INR 0.00"));
            assertTrue(text.contains("Payment status: PENDING"));
            assertTrue(text.contains("Transaction reference: pay_gateway_123"));
            assertTrue(text.contains("REFUND HISTORY"));
            assertTrue(text.contains("INR 10.00"));
        }
    }

    @Test
    void invoicePdfShowsCashReceivedAndChangeSeparatelyFromPayableTotal() throws Exception {
        BookingEntity booking = new BookingEntity(
                "HF-CASH-INVOICE", "Cash Customer", "PLUMBING",
                "Cash Provider", "COMPLETED", null);
        Invoice invoice = new Invoice();
        invoice.setBooking(booking);
        invoice.setInvoiceNumber("HF-HF-CASH-INVOICE");
        invoice.setSubtotal(new BigDecimal("300.00"));
        invoice.setTax(BigDecimal.ZERO);
        invoice.setTotal(new BigDecimal("300.00"));

        byte[] pdf = invoicePdfService.create(
                invoice, new BigDecimal("300.00"), BigDecimal.ZERO, java.util.List.of(),
                "CASH", "PAID", null, new BigDecimal("350.00"), java.util.List.of());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("Final payable amount: INR 300.00"));
            assertTrue(text.contains("Cash received: INR 350.00"));
            assertTrue(text.contains("Change due: INR 50.00"));
        }
    }
}
