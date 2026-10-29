package com.homefix.api.service;

import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.Invoice;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.List;

@Service
public class InvoicePdfService {

    private static final DateTimeFormatter ISSUED_AT_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] create(
            Invoice invoice,
            BigDecimal originalEstimate,
            BigDecimal approvedAdditionalCharges,
            List<ChargeLine> approvedChargeItems,
            String paymentMethod,
            String paymentStatus,
            String reference,
            BigDecimal cashReceivedAmount,
            List<RefundLine> refunds) {
        BookingEntity booking = invoice.getBooking();
        BigDecimal estimate = originalEstimate == null ? BigDecimal.ZERO : originalEstimate;
        BigDecimal additionalCharges = approvedAdditionalCharges == null
                ? BigDecimal.ZERO : approvedAdditionalCharges;
        BigDecimal otherPriceChanges = originalEstimate == null
                ? BigDecimal.ZERO
                : invoice.getSubtotal().subtract(estimate).subtract(additionalCharges);
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 780;
                y = draw(content, "HOMEFIX SERVICE INVOICE", 20, true, 48, y);
                y = draw(content, "Invoice: " + invoice.getInvoiceNumber(), 11, false, 48, y - 10);
                y = draw(content, "Issued: " + ISSUED_AT_FORMAT.format(invoice.getIssuedAt()), 10, false, 48, y);
                y -= 12;
                y = draw(content, "CUSTOMER", 11, true, 48, y);
                y = draw(content, "Name: " + booking.getCustomerName(), 10, false, 48, y);
                y = draw(content, "Phone: " + booking.getPhone(), 10, false, 48, y);
                y = draw(content, "Service address: " + booking.getAddress(), 10, false, 48, y);
                y -= 8;
                y = draw(content, "PROVIDER", 11, true, 48, y);
                y = draw(content, "Name: " + valueOr(booking.getProviderName(), "Not assigned"), 10, false, 48, y);
                y -= 8;
                y = draw(content, "BOOKING DETAILS", 11, true, 48, y);
                y = draw(content, "Booking ID: " + booking.getBookingId(), 10, false, 48, y);
                y = draw(content, "Service: " + booking.getService(), 10, false, 48, y);
                y = draw(content, "Scheduled: " + valueOr(booking.getPreferredDate(), "Not set"), 10, false, 48, y);
                y -= 8;
                y = draw(content, "PRICE SUMMARY", 11, true, 48, y);
                y = draw(content, "Original estimate: "
                        + amountOrNotRecorded(originalEstimate), 10, false, 48, y);
                y = draw(content, "Approved additional charges: INR "
                        + additionalCharges.toPlainString(), 10, false, 48, y);
                if (approvedChargeItems != null) {
                    for (ChargeLine charge : approvedChargeItems) {
                        y = draw(content,
                                valueOr(charge.description(), "Additional charge") + ": INR "
                                        + charge.amount().toPlainString(),
                                9, false, 64, y);
                    }
                }
                if (otherPriceChanges.signum() != 0) {
                    y = draw(content, "Other approved price changes: INR "
                            + otherPriceChanges.toPlainString(), 10, false, 48, y);
                }
                y = draw(content, "Subtotal: INR " + invoice.getSubtotal().toPlainString(), 10, false, 48, y);
                y = draw(content, "Discount: INR " + invoice.getDiscount().toPlainString(), 10, false, 48, y);
                y = draw(content, "Taxable amount: INR "
                        + invoice.getTaxableAmount().toPlainString(), 10, false, 48, y);
                y = draw(content, "Tax: INR " + invoice.getTax().toPlainString(), 10, false, 48, y);
                y = draw(content, "Final payable amount: INR "
                        + invoice.getTotal().toPlainString(), 13, true, 48, y);
                y = draw(content, "Method: " + valueOr(paymentMethod, "Not recorded"), 10, false, 48, y);
                y = draw(content, "Payment status: " + valueOr(paymentStatus, "Not recorded"), 10, false, 48, y);
                if (cashReceivedAmount != null) {
                    y = draw(content, "Cash received: INR "
                            + cashReceivedAmount.toPlainString(), 10, false, 48, y);
                    BigDecimal changeDue = cashReceivedAmount.subtract(invoice.getTotal());
                    if (changeDue.signum() > 0) {
                        y = draw(content, "Change due: INR "
                                + changeDue.toPlainString(), 10, false, 48, y);
                    }
                }
                if (reference != null && !reference.isBlank()) {
                    y = draw(content, "Transaction reference: " + reference, 10, false, 48, y);
                }
                if (refunds != null && !refunds.isEmpty()) {
                    y -= 8;
                    y = draw(content, "REFUND HISTORY (separate from original invoice)",
                            11, true, 48, y);
                    for (RefundLine refund : refunds) {
                        y = draw(content,
                                "INR " + refund.amount().toPlainString() + " | " + refund.status()
                                        + " | " + valueOr(refund.reference(), "Gateway reference pending")
                                        + " | " + ISSUED_AT_FORMAT.format(refund.createdAt()),
                                9, false, 48, y);
                    }
                }
                draw(content,
                        "This service invoice is generated by HomeFix from the completed booking record.",
                        9, false, 48, y - 18);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate invoice PDF.", exception);
        }
    }

    private String amountOrNotRecorded(BigDecimal amount) {
        return amount == null ? "Not recorded" : "INR " + amount.toPlainString();
    }

    private float draw(
            PDPageContentStream content,
            String text,
            float fontSize,
            boolean bold,
            float x,
            float y) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(bold
                ? Standard14Fonts.FontName.HELVETICA_BOLD
                : Standard14Fonts.FontName.HELVETICA), fontSize);
        content.newLineAtOffset(x, y);
        content.showText(safeText(text));
        content.endText();
        return y - fontSize - 8;
    }

    private String safeText(String value) {
        String normalized = Normalizer.normalize(valueOr(value, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record RefundLine(
            BigDecimal amount,
            String status,
            String reference,
            Instant createdAt) {
    }

    public record ChargeLine(BigDecimal amount, String description) {
    }
}
