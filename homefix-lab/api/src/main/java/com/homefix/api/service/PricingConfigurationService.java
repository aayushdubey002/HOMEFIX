package com.homefix.api.service;

import com.homefix.api.dto.PricingConfiguration;
import com.homefix.api.model.PlatformSetting;
import com.homefix.api.repository.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.function.Function;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PricingConfigurationService {

    private static final String PREFIX = "pricing.";

    private final PlatformSettingRepository settingRepository;

    public PricingConfigurationService(PlatformSettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    @Transactional(readOnly = true)
    public PricingConfiguration getConfiguration() {
        return new PricingConfiguration(
                decimal("experience.less-than-one-year", BigDecimal.ONE),
                decimal("experience.one-to-three-years", BigDecimal.ONE),
                decimal("experience.three-to-five-years", BigDecimal.ONE),
                decimal("experience.five-plus-years", BigDecimal.ONE),
                decimal("surcharge.weekend-percent", BigDecimal.ZERO),
                decimal("surcharge.urgent-percent", BigDecimal.ZERO),
                integer("urgent.window-hours", 0),
                decimal("surcharge.late-night-percent", BigDecimal.ZERO),
                time("late-night.start", LocalTime.MIDNIGHT),
                time("late-night.end", LocalTime.MIDNIGHT),
                decimal("provider.maximum-additional-charge", BigDecimal.ZERO),
                text("discount.type", "NONE"),
                decimal("discount.value", BigDecimal.ZERO),
                decimal("discount.minimum-subtotal", BigDecimal.ZERO),
                text("discount.eligible-service-codes", ""),
                decimal("tax.percent", BigDecimal.ZERO)
        );
    }

    @Transactional
    public PricingConfiguration updateConfiguration(PricingConfiguration configuration) {
        if ("PERCENTAGE".equals(configuration.discountType())
                && configuration.discountValue().compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("Percentage discounts cannot exceed 100%.");
        }
        save("experience.less-than-one-year", configuration.lessThanOneYearMultiplier().toPlainString());
        save("experience.one-to-three-years", configuration.oneToThreeYearsMultiplier().toPlainString());
        save("experience.three-to-five-years", configuration.threeToFiveYearsMultiplier().toPlainString());
        save("experience.five-plus-years", configuration.fivePlusYearsMultiplier().toPlainString());
        save("surcharge.weekend-percent", configuration.weekendSurchargePercent().toPlainString());
        save("surcharge.urgent-percent", configuration.urgentSurchargePercent().toPlainString());
        save("urgent.window-hours", configuration.urgentWindowHours().toString());
        save("surcharge.late-night-percent", configuration.lateNightSurchargePercent().toPlainString());
        save("late-night.start", configuration.lateNightStart().toString());
        save("late-night.end", configuration.lateNightEnd().toString());
        save("provider.maximum-additional-charge",
                configuration.maximumProviderAdditionalCharge().toPlainString());
        save("discount.type", configuration.discountType());
        save("discount.value", configuration.discountValue().toPlainString());
        save("discount.minimum-subtotal", configuration.discountMinimumSubtotal().toPlainString());
        save("discount.eligible-service-codes", normalizeServiceCodes(configuration.eligibleServiceCodes()));
        save("tax.percent", configuration.taxPercent().toPlainString());
        return getConfiguration();
    }

    private BigDecimal decimal(String key, BigDecimal defaultValue) {
        return value(key, defaultValue.toPlainString(), BigDecimal::new);
    }

    private int integer(String key, int defaultValue) {
        return value(key, Integer.toString(defaultValue), Integer::parseInt);
    }

    private LocalTime time(String key, LocalTime defaultValue) {
        return value(key, defaultValue.toString(), LocalTime::parse);
    }

    private String text(String key, String defaultValue) {
        return value(key, defaultValue, Function.identity());
    }

    private String normalizeServiceCodes(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        Set<String> normalized = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toUpperCase(Locale.ROOT))
                .filter(code -> code.matches("[A-Z0-9_-]{1,40}"))
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        long nonEmptyTokens = java.util.Arrays.stream(raw.split(","))
                .map(String::trim).filter(code -> !code.isEmpty()).count();
        if (normalized.size() != nonEmptyTokens) {
            throw new IllegalArgumentException(
                    "Eligible service codes must be unique valid codes separated by commas.");
        }
        return String.join(",", normalized);
    }

    private <T> T value(String key, String defaultValue, Function<String, T> parser) {
        String settingValue = settingRepository.findBySettingKey(PREFIX + key)
                .map(PlatformSetting::getSettingValue)
                .orElse(defaultValue);
        try {
            return parser.apply(settingValue);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid persisted pricing setting: " + key, exception);
        }
    }

    private void save(String key, String settingValue) {
        PlatformSetting setting = settingRepository.findBySettingKey(PREFIX + key)
                .orElseGet(() -> new PlatformSetting(PREFIX + key, settingValue));
        setting.setSettingValue(settingValue);
        settingRepository.save(setting);
    }
}
