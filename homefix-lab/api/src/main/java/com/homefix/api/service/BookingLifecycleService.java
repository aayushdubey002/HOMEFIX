package com.homefix.api.service;

import com.homefix.api.model.AuditLog;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingPriceHistory;
import com.homefix.api.model.BookingStatusHistory;
import com.homefix.api.model.Invoice;
import com.homefix.api.model.Notification;
import com.homefix.api.model.Payment;
import com.homefix.api.model.User;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.BookingStatusHistoryRepository;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.InvoiceRepository;
import com.homefix.api.repository.NotificationRepository;
import com.homefix.api.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class BookingLifecycleService {

    private static final Set<String> PROVIDER_TRANSITIONS =
            Set.of("ACCEPTED>CONFIRMED", "CONFIRMED>ON_THE_WAY",
                    "ON_THE_WAY>ARRIVED", "ON_THE_WAY>IN_PROGRESS",
                    "ARRIVED>IN_PROGRESS", "IN_PROGRESS>COMPLETED");

    private final BookingStatusHistoryRepository historyRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogRepository auditLogRepository;
    private final BookingPriceHistoryRepository priceHistoryRepository;

    public BookingLifecycleService(
            BookingStatusHistoryRepository historyRepository,
            NotificationRepository notificationRepository,
            PaymentRepository paymentRepository,
            InvoiceRepository invoiceRepository,
            AuditLogRepository auditLogRepository,
            BookingPriceHistoryRepository priceHistoryRepository) {
        this.historyRepository = historyRepository;
        this.notificationRepository = notificationRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.auditLogRepository = auditLogRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
        public void created(BookingEntity booking, String paymentMethod, String merchantUpiId) {
        historyRepository.save(new BookingStatusHistory(
                booking, booking.getUser(), null, "PENDING", "Booking created"));
        notificationRepository.save(new Notification(
                booking.getUser(),
                "Booking received",
                "Your " + booking.getService() + " request is pending provider acceptance.",
                "BOOKING"
        ));
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setAmount(booking.getPrice());
        payment.setMethod(paymentMethod);
        payment.setMerchantUpiId(merchantUpiId);
        payment.setAdminNote("Customer selected " + paymentMethod + " at booking.");
        paymentRepository.save(payment);
        auditLogRepository.save(new AuditLog(
                booking.getUser(), "BOOKING_CREATED", "BOOKING",
                booking.getBookingId(), "Customer submitted a service request."));
    }

    @Transactional
    public void recordProviderPaymentConfirmation(
            BookingEntity booking, User actor, Payment payment, BigDecimal cashReceivedAmount) {
        payment.setAmountCollected(cashReceivedAmount);
        payment.setStatus("PAID");
        payment.setExternalReference(null);
        payment.setAdminNote("Provider confirmed CASH received: INR "
                + cashReceivedAmount.toPlainString() + " for payable amount INR "
                + payment.getAmount().toPlainString() + ".");
        paymentRepository.save(payment);
        auditLogRepository.save(new AuditLog(
                actor, "CASH_PAYMENT_CONFIRMED_BY_PROVIDER", "PAYMENT",
                String.valueOf(payment.getId()), "Cash received INR "
                        + cashReceivedAmount.toPlainString() + " for booking "
                        + booking.getBookingId() + "; payable amount INR "
                        + payment.getAmount().toPlainString()));
    }

    @Transactional
    public boolean providerTransition(BookingEntity booking, User actor,
                                      String nextStatus, String note) {
        String previous = normalized(booking.getStatus());
        String next = normalized(nextStatus);
        if (!PROVIDER_TRANSITIONS.contains(previous + ">" + next)) {
            return false;
        }
        update(booking, actor, previous, next, note);
        return true;
    }

    @Transactional
    public void update(BookingEntity booking, User actor,
                       String previous, String next, String note) {
        booking.setStatus(next);
        historyRepository.save(new BookingStatusHistory(
                booking, actor, previous, next, note));
        User recipient = booking.getUser();
        notificationRepository.save(new Notification(
                recipient,
                "Booking " + next.toLowerCase().replace('_', ' '),
                note == null || note.isBlank()
                        ? "Your booking " + booking.getBookingId() + " is now " + next + "."
                        : note,
                "BOOKING"
        ));
        auditLogRepository.save(new AuditLog(
                actor, "BOOKING_STATUS_CHANGED", "BOOKING",
                booking.getBookingId(), previous + " -> " + next));
        if ("COMPLETED".equals(next) && booking.getPrice() != null
                && invoiceRepository.findByBooking_Id(booking.getId()).isEmpty()) {
            BookingPriceHistory approvedPrice = priceHistoryRepository
                    .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "APPROVED")
                    .orElse(null);
            BigDecimal total = booking.getPrice();
            Invoice invoice = new Invoice();
            invoice.setBooking(booking);
            invoice.setInvoiceNumber("HF-" + booking.getBookingId());
            invoice.setSubtotal(approvedPrice == null
                    ? total : approvedPrice.getServiceSubtotal());
            invoice.setDiscount(approvedPrice == null
                    ? BigDecimal.ZERO : approvedPrice.getDiscountAmount());
            invoice.setTaxableAmount(approvedPrice == null
                    ? total : approvedPrice.getTaxableAmount());
            invoice.setTax(approvedPrice == null
                    ? BigDecimal.ZERO : approvedPrice.getTaxAmount());
            invoice.setTotal(total);
            invoiceRepository.save(invoice);
        }
    }

    @Transactional
    public void priceProposalEvent(
            BookingEntity booking, User actor, User recipient, String action, String message) {
        notificationRepository.save(new Notification(
                recipient,
                "Booking price update",
                message,
                "PRICING"
        ));
        auditLogRepository.save(new AuditLog(
                actor, action, "BOOKING_PRICE", booking.getBookingId(), message));
    }

    private String normalized(String status) {
        return status == null ? "" : status.trim().toUpperCase();
    }
}
