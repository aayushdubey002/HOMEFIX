package com.homefix.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record PricingConfiguration(
        @NotNull @DecimalMin("0.01") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
        BigDecimal lessThanOneYearMultiplier,
        @NotNull @DecimalMin("0.01") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
        BigDecimal oneToThreeYearsMultiplier,
        @NotNull @DecimalMin("0.01") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
        BigDecimal threeToFiveYearsMultiplier,
        @NotNull @DecimalMin("0.01") @DecimalMax("10.00") @Digits(integer = 2, fraction = 2)
        BigDecimal fivePlusYearsMultiplier,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2)
        BigDecimal weekendSurchargePercent,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2)
        BigDecimal urgentSurchargePercent,
        @NotNull @Min(0) @Max(168)
        Integer urgentWindowHours,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000.00") @Digits(integer = 4, fraction = 2)
        BigDecimal lateNightSurchargePercent,
        @NotNull
        LocalTime lateNightStart,
        @NotNull
        LocalTime lateNightEnd,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2)
        BigDecimal maximumProviderAdditionalCharge,
        @NotNull @Pattern(regexp = "NONE|FIXED|PERCENTAGE")
        String discountType,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2)
        BigDecimal discountValue,
        @NotNull @DecimalMin("0.00") @DecimalMax("99999999.99")
        @Digits(integer = 8, fraction = 2)
        BigDecimal discountMinimumSubtotal,
        @Size(max = 500)
        String eligibleServiceCodes,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00")
        @Digits(integer = 3, fraction = 2)
        BigDecimal taxPercent
) {
}
