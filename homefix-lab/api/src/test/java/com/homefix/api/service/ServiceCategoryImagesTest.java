package com.homefix.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCategoryImagesTest {

    @Test
    void acceptsOnlyBundledImageKeys() {
        assertTrue(ServiceCategoryImages.supports("ac-repair"));
        assertFalse(ServiceCategoryImages.supports("../external-image"));
        assertFalse(ServiceCategoryImages.supports(null));
    }

    @Test
    void infersExistingServiceImagesAndUsesBundledFallbackForNewCategories() {
        assertEquals(
                "ac-repair",
                ServiceCategoryImages.resolve(null, "AC Repair", "ac-repair", "AC REPAIR"));
        assertEquals(
                "home-services",
                ServiceCategoryImages.resolve(null, "Garden Care", "garden-care", "GARDEN CARE"));
    }
}
