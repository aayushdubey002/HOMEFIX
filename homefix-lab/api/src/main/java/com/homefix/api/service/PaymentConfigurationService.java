package com.homefix.api.service;

import com.homefix.api.model.PlatformSetting;
import com.homefix.api.repository.PlatformSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class PaymentConfigurationService {

    private static final String UPI_ID_KEY = "homefix.upi-id";

    private final PlatformSettingRepository settingRepository;
    private final RazorpayPaymentService razorpayPaymentService;
    private final PricingConfigurationService pricingConfigurationService;

    public PaymentConfigurationService(
            PlatformSettingRepository settingRepository,
            RazorpayPaymentService razorpayPaymentService,
            PricingConfigurationService pricingConfigurationService) {
        this.settingRepository = settingRepository;
        this.razorpayPaymentService = razorpayPaymentService;
        this.pricingConfigurationService = pricingConfigurationService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicConfiguration() {
        boolean gatewayEnabled = razorpayPaymentService.isConfigured();
        return Map.of(
                "upiEnabled", gatewayEnabled,
                "gatewayEnabled", gatewayEnabled,
                "keyId", razorpayPaymentService.publicKeyId(),
                "testMode", razorpayPaymentService.isTestMode(),
                "configurationMessage", razorpayPaymentService.configurationStatus(),
                "upiId", "",
                "merchantName", "HomeFix",
                "maximumProviderAdditionalCharge",
                pricingConfigurationService.getConfiguration().maximumProviderAdditionalCharge()
        );
    }

    @Transactional
    public String updateUpiId(String upiId) {
        PlatformSetting setting = settingRepository.findBySettingKey(UPI_ID_KEY)
                .orElseGet(() -> new PlatformSetting(UPI_ID_KEY, ""));
        setting.setSettingValue(upiId.trim());
        settingRepository.save(setting);
        return setting.getSettingValue();
    }
}