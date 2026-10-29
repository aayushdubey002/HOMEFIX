package com.homefix.api.service;

import com.homefix.api.dto.PricingConfiguration;
import com.homefix.api.repository.BookingPriceHistoryRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingPricingServiceTest {

    private final PricingConfigurationService configurationService =
            mock(PricingConfigurationService.class);
    private final BookingPricingService pricingService = new BookingPricingService(
            configurationService,
            mock(BookingPriceHistoryRepository.class),
            mock(ProviderSkillRepository.class),
            mock(ServiceCategoryRepository.class));

    @Test
    void appliesEligibleFixedDiscountBeforeTaxUsingCurrencyPrecision() {
        when(configurationService.getConfiguration()).thenReturn(configuration(
                "FIXED", "25.00", "100.00", "PLUMBING", "10.00"));

        BookingPricingService.PriceQuote quote = pricingService.initialEstimate(
                new BigDecimal("300.00"), null, "PLUMBING");

        assertEquals(new BigDecimal("300.00"), quote.serviceSubtotal());
        assertEquals(new BigDecimal("25.00"), quote.discountAmount());
        assertEquals(new BigDecimal("275.00"), quote.taxableAmount());
        assertEquals(new BigDecimal("27.50"), quote.taxAmount());
        assertEquals(new BigDecimal("302.50"), quote.total());
    }

    @Test
    void ignoresDiscountForIneligibleServiceAndSupportsPercentageDiscounts() {
        when(configurationService.getConfiguration()).thenReturn(configuration(
                "PERCENTAGE", "10.00", "0.00", "PLUMBING", "10.00"));

        BookingPricingService.PriceQuote ineligible = pricingService.initialEstimate(
                new BigDecimal("300.00"), null, "ELECTRICAL");
        BookingPricingService.PriceQuote eligible = pricingService.initialEstimate(
                new BigDecimal("300.00"), null, "PLUMBING");

        assertEquals(new BigDecimal("0.00"), ineligible.discountAmount());
        assertEquals(new BigDecimal("330.00"), ineligible.total());
        assertEquals(new BigDecimal("30.00"), eligible.discountAmount());
        assertEquals(new BigDecimal("27.00"), eligible.taxAmount());
        assertEquals(new BigDecimal("297.00"), eligible.total());
    }

    private PricingConfiguration configuration(
            String discountType,
            String discountValue,
            String minimumSubtotal,
            String eligibleServiceCodes,
            String taxPercent) {
        return new PricingConfiguration(
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                LocalTime.MIDNIGHT, LocalTime.MIDNIGHT, BigDecimal.ZERO,
                discountType, new BigDecimal(discountValue), new BigDecimal(minimumSubtotal),
                eligibleServiceCodes, new BigDecimal(taxPercent));
    }
}
