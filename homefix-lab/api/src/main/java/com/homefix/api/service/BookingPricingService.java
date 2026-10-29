package com.homefix.api.service;

import com.homefix.api.dto.PricingConfiguration;
import com.homefix.api.model.BookingEntity;
import com.homefix.api.model.BookingPriceHistory;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderSkill;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;

@Service
public class BookingPricingService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal MAX_HISTORY_AMOUNT = new BigDecimal("99999999.99");
    private static final Set<String> ACTIVE_BOOKING_STATUSES =
            Set.of("ACCEPTED", "CONFIRMED", "ON_THE_WAY", "ARRIVED", "IN_PROGRESS");

    private final PricingConfigurationService configurationService;
    private final BookingPriceHistoryRepository priceHistoryRepository;
    private final ProviderSkillRepository providerSkillRepository;
    private final ServiceCategoryRepository categoryRepository;

    public BookingPricingService(
            PricingConfigurationService configurationService,
            BookingPriceHistoryRepository priceHistoryRepository,
            ProviderSkillRepository providerSkillRepository,
            ServiceCategoryRepository categoryRepository) {
        this.configurationService = configurationService;
        this.priceHistoryRepository = priceHistoryRepository;
        this.providerSkillRepository = providerSkillRepository;
        this.categoryRepository = categoryRepository;
    }

    public PriceQuote initialEstimate(BigDecimal baseAmount, LocalDateTime appointment) {
        return initialEstimate(baseAmount, appointment, "");
    }

    public PriceQuote initialEstimate(
            BigDecimal baseAmount, LocalDateTime appointment, String serviceCode) {
        PricingConfiguration configuration = configurationService.getConfiguration();
        return quote(baseAmount, BigDecimal.ONE, null, BigDecimal.ZERO,
                appointment, configuration, serviceCode);
    }

    @Transactional
    public BookingPriceHistory recordInitialEstimate(
            BookingEntity booking, User customer, PriceQuote quote) {
        BookingPriceHistory history = history(booking, customer, quote);
        history.setStatus("APPROVED");
        history.setExplanation("Initial server-calculated estimate");
        return priceHistoryRepository.save(history);
    }

    @Transactional
    public BookingPriceHistory refreshPendingProposal(
            BookingEntity booking, BookingPriceHistory proposal) {
        if (!"PENDING_APPROVAL".equals(proposal.getStatus())) {
            return proposal;
        }
        PriceQuote quote = additionalChargeQuote(booking, proposal.getAdditionalAmount());
        if (quote.total().compareTo(proposal.getProposedTotal()) == 0) {
            return proposal;
        }
        applyQuote(proposal, quote);
        return priceHistoryRepository.save(proposal);
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public BookingPriceHistory propose(
            BookingEntity booking,
            ProviderProfile provider,
            BigDecimal additionalAmount,
            String explanation) {
        if (!ACTIVE_BOOKING_STATUSES.contains(booking.getStatus().trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("A price proposal requires an accepted, active booking.");
        }
        PricingConfiguration configuration = configurationService.getConfiguration();
        BigDecimal maximumAdditional = configuration.maximumProviderAdditionalCharge();
        if (additionalAmount.signum() < 0 || additionalAmount.compareTo(maximumAdditional) > 0) {
            throw new IllegalArgumentException(
                    "The additional amount exceeds the administrator-configured limit.");
        }
        if (priceHistoryRepository.existsByBooking_IdAndStatus(booking.getId(), "PENDING_APPROVAL")) {
            throw new IllegalStateException("A price proposal is already awaiting customer approval.");
        }

        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase(booking.getService())
                .orElseThrow(() -> new IllegalStateException("The booking service category is unavailable."));
        boolean hasServiceSkill = providerSkillRepository.findByProvider_Id(provider.getId()).stream()
                .filter(candidate -> candidate.getCategory().getId().equals(category.getId()))
                .findAny()
                .isPresent();
        if (!hasServiceSkill) {
            throw new IllegalStateException("The assigned provider has no saved price for this service.");
        }
        PriceQuote quote = additionalChargeQuote(booking, additionalAmount);

        BookingPriceHistory history = history(booking, provider.getUser(), quote);
        history.setStatus("PENDING_APPROVAL");
        history.setExplanation(explanation.trim());
        return priceHistoryRepository.save(history);
    }

    private PriceQuote additionalChargeQuote(BookingEntity booking, BigDecimal additionalAmount) {
        BookingPriceHistory approved = priceHistoryRepository
                .findFirstByBooking_IdAndStatusOrderByCreatedAtDesc(booking.getId(), "APPROVED")
                .orElse(null);
        BigDecimal currentTotal = booking.getPrice();
        if (currentTotal == null && approved != null) {
            currentTotal = approved.getProposedTotal();
        }
        if (currentTotal == null) {
            throw new IllegalStateException("The current booking price is unavailable.");
        }

        BigDecimal total = currentTotal.add(additionalAmount).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(MAX_HISTORY_AMOUNT) > 0) {
            throw new IllegalArgumentException("The calculated total exceeds the supported booking amount.");
        }

        if (approved == null) {
            return new PriceQuote(currentTotal, null, BigDecimal.ONE,
                    ZERO, ZERO, ZERO, additionalAmount,
                    total, ZERO, total, ZERO, "NONE", total);
        }

        BigDecimal adjustment = currentTotal.subtract(approved.getProposedTotal());
        BigDecimal subtotal = approved.getServiceSubtotal()
                .add(adjustment)
                .add(additionalAmount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxableAmount = approved.getTaxableAmount()
                .add(adjustment)
                .add(additionalAmount)
                .setScale(2, RoundingMode.HALF_UP);
        return new PriceQuote(
                approved.getBaseAmount(),
                approved.getExperienceTier(),
                approved.getExperienceMultiplier(),
                approved.getWeekendSurcharge(),
                approved.getUrgentSurcharge(),
                approved.getLateNightSurcharge(),
                additionalAmount,
                subtotal,
                approved.getDiscountAmount(),
                taxableAmount,
                approved.getTaxAmount(),
                approved.getDiscountRule(),
                total);
    }

    private PriceQuote quote(
            BigDecimal baseAmount,
            BigDecimal experienceMultiplier,
            String experienceTier,
            BigDecimal additionalAmount,
            LocalDateTime appointment,
            PricingConfiguration configuration,
            String serviceCode) {
        BigDecimal adjustedBase = baseAmount.multiply(experienceMultiplier);
        BigDecimal weekend = ZERO;
        BigDecimal urgent = ZERO;
        BigDecimal lateNight = ZERO;
        if (appointment != null) {
            DayOfWeek day = appointment.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                weekend = percentage(adjustedBase, configuration.weekendSurchargePercent());
            }
            int urgentWindowHours = configuration.urgentWindowHours();
            LocalDateTime now = LocalDateTime.now();
            if (urgentWindowHours > 0 && !appointment.isBefore(now)
                    && !appointment.isAfter(now.plusHours(urgentWindowHours))) {
                urgent = percentage(adjustedBase, configuration.urgentSurchargePercent());
            }
            if (isLateNight(appointment.toLocalTime(), configuration.lateNightStart(),
                    configuration.lateNightEnd())) {
                lateNight = percentage(adjustedBase, configuration.lateNightSurchargePercent());
            }
        }
        BigDecimal subtotal = adjustedBase.add(weekend).add(urgent).add(lateNight).add(additionalAmount)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = calculateDiscount(subtotal, serviceCode, configuration);
        BigDecimal taxableAmount = subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = taxableAmount.multiply(configuration.taxPercent())
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        BigDecimal total = taxableAmount.add(tax).setScale(2, RoundingMode.HALF_UP);
        if (total.compareTo(MAX_HISTORY_AMOUNT) > 0) {
            throw new IllegalArgumentException("The calculated total exceeds the supported booking amount.");
        }
        return new PriceQuote(baseAmount, experienceTier, experienceMultiplier,
                weekend, urgent, lateNight, additionalAmount,
                subtotal, discount, taxableAmount, tax,
                discountRule(serviceCode, subtotal, configuration), total);
    }

    private BigDecimal calculateDiscount(
            BigDecimal subtotal, String serviceCode, PricingConfiguration configuration) {
        if ("NONE".equals(configuration.discountType())
                || subtotal.compareTo(configuration.discountMinimumSubtotal()) < 0
                || !isEligibleService(serviceCode, configuration.eligibleServiceCodes())) {
            return ZERO;
        }
        BigDecimal discount = switch (configuration.discountType()) {
            case "FIXED" -> configuration.discountValue().min(subtotal);
            case "PERCENTAGE" -> percentage(subtotal, configuration.discountValue());
            default -> ZERO;
        };
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private String discountRule(
            String serviceCode, BigDecimal subtotal, PricingConfiguration configuration) {
        if (calculateDiscount(subtotal, serviceCode, configuration).signum() == 0) {
            return "NONE";
        }
        return configuration.discountType() + ":" + configuration.discountValue().toPlainString();
    }

    private boolean isEligibleService(String serviceCode, String eligibleServiceCodes) {
        if (eligibleServiceCodes == null || eligibleServiceCodes.isBlank()) {
            return true;
        }
        String normalizedCode = serviceCode == null ? "" : serviceCode.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(eligibleServiceCodes.split(","))
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .anyMatch(normalizedCode::equals);
    }

    private BigDecimal percentage(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private boolean isLateNight(LocalTime appointment, LocalTime start, LocalTime end) {
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !appointment.isBefore(start) && appointment.isBefore(end);
        }
        return !appointment.isBefore(start) || appointment.isBefore(end);
    }

    private BookingPriceHistory history(BookingEntity booking, User actor, PriceQuote quote) {
        BookingPriceHistory history = new BookingPriceHistory();
        history.setBooking(booking);
        history.setActor(actor);
        applyQuote(history, quote);
        return history;
    }

    private void applyQuote(BookingPriceHistory history, PriceQuote quote) {
        history.setBaseAmount(quote.baseAmount());
        history.setExperienceTier(quote.experienceTier());
        history.setExperienceMultiplier(quote.experienceMultiplier());
        history.setWeekendSurcharge(quote.weekendSurcharge());
        history.setUrgentSurcharge(quote.urgentSurcharge());
        history.setLateNightSurcharge(quote.lateNightSurcharge());
        history.setAdditionalAmount(quote.additionalAmount());
        history.setServiceSubtotal(quote.serviceSubtotal());
        history.setDiscountAmount(quote.discountAmount());
        history.setTaxableAmount(quote.taxableAmount());
        history.setTaxAmount(quote.taxAmount());
        history.setDiscountRule(quote.discountRule());
        history.setProposedTotal(quote.total());
    }

    public record PriceQuote(
            BigDecimal baseAmount,
            String experienceTier,
            BigDecimal experienceMultiplier,
            BigDecimal weekendSurcharge,
            BigDecimal urgentSurcharge,
            BigDecimal lateNightSurcharge,
            BigDecimal additionalAmount,
            BigDecimal serviceSubtotal,
            BigDecimal discountAmount,
            BigDecimal taxableAmount,
            BigDecimal taxAmount,
            String discountRule,
            BigDecimal total) {
    }
}
