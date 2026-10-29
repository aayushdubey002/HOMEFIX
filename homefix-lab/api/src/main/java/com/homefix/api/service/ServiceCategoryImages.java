package com.homefix.api.service;

import java.util.Locale;
import java.util.Set;

public final class ServiceCategoryImages {

    private static final Set<String> IMAGE_KEYS = Set.of(
            "home-services",
            "plumbing",
            "electrical",
            "ac-repair",
            "carpentry",
            "cleaning",
            "painting",
            "appliance-repair"
    );

    private ServiceCategoryImages() {
    }

    public static boolean supports(String imageKey) {
        return imageKey != null
                && IMAGE_KEYS.contains(imageKey.trim().toLowerCase(Locale.ROOT));
    }

    public static String resolve(String imageKey, String name, String slug, String serviceCode) {
        if (imageKey != null && !imageKey.isBlank()) {
            return imageKey.trim().toLowerCase(Locale.ROOT);
        }

        String identity = String.join(" ",
                name == null ? "" : name,
                slug == null ? "" : slug,
                serviceCode == null ? "" : serviceCode).toLowerCase(Locale.ROOT);

        if (identity.contains("plumb")) return "plumbing";
        if (identity.contains("electric")) return "electrical";
        if (identity.matches(".*(air.?condition|\\bac\\b|hvac).*")) return "ac-repair";
        if (identity.contains("carpent") || identity.contains("woodwork")) return "carpentry";
        if (identity.contains("clean")) return "cleaning";
        if (identity.contains("paint")) return "painting";
        if (identity.contains("appliance")) return "appliance-repair";
        return "home-services";
    }
}
