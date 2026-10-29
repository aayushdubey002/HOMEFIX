package com.homefix.api.controller;

import com.homefix.api.dto.ProviderMatchResponse;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.ProviderAvailabilityRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.DistanceCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProviderMatchingController {

    private final UserRepository userRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ProviderSkillRepository skillRepository;
    private final ProviderAvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final double maximumServiceRadiusKm;
    private final boolean simulationEnabled;

    public ProviderMatchingController(
            UserRepository userRepository,
            ServiceCategoryRepository categoryRepository,
            ProviderSkillRepository skillRepository,
            ProviderAvailabilityRepository availabilityRepository,
            BookingRepository bookingRepository,
            @Value("${app.location.service-radius-km:25}") double maximumServiceRadiusKm,
            @Value("${app.location.simulation-enabled:false}") boolean simulationEnabled) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.skillRepository = skillRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.maximumServiceRadiusKm = maximumServiceRadiusKm;
        this.simulationEnabled = simulationEnabled;
    }

    @GetMapping("/location/config")
    public Map<String, Object> locationConfig() {
        return Map.of(
                "simulationEnabled", simulationEnabled,
                "serviceRadiusKm", maximumServiceRadiusKm,
                "simulationCities", simulationEnabled ? List.of("Mumbai", "Pune") : List.of()
        );
    }

    @GetMapping("/providers/match")
    public ResponseEntity<?> match(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String service,
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) LocalDateTime requestedAt) {
        User customer = currentCustomer(jwt);
        if (customer == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Customer account required."));
        }
        if (!DistanceCalculator.validCoordinates(latitude, longitude)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "A valid customer location is required for nearby matching."));
        }
        ServiceCategory category = categoryRepository.findByServiceCodeIgnoreCase(service)
                .or(() -> categoryRepository.findBySlugIgnoreCase(service))
                .orElse(null);
        if (category == null || !category.isActive()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Select an active service category."));
        }
        LocalDateTime appointmentTime = requestedAt == null ? LocalDateTime.now() : requestedAt;
        List<ProviderMatchResponse> matches = skillRepository
                .findByCategory_IdAndProvider_VerificationStatusIgnoreCaseAndProvider_OnlineTrue(
                        category.getId(), "APPROVED")
                .stream()
                .filter(skill -> {
                    ProviderProfile profile = skill.getProvider();
                    User provider = profile.getUser();
                    if (!provider.isActive() || !provider.isProviderApproved()
                            || !DistanceCalculator.validCoordinates(
                                    profile.getLatitude(), profile.getLongitude())) {
                        return false;
                    }
                    double distance = DistanceCalculator.kilometers(
                            latitude, longitude, profile.getLatitude(), profile.getLongitude());
                    double radius = Math.min(profile.getServiceRadiusKm(), maximumServiceRadiusKm);
                    if (distance > radius) {
                        return false;
                    }
                    boolean available = availabilityRepository
                            .findByProvider_IdAndDayOfWeekAndEnabledTrue(
                                    profile.getId(), appointmentTime.getDayOfWeek())
                            .stream()
                            .anyMatch(slot -> slot.getStartTime().compareTo(
                                            appointmentTime.toLocalTime()) <= 0
                                    && slot.getEndTime().compareTo(
                                            appointmentTime.toLocalTime().plusHours(1)) >= 0);
                    return available && !bookingRepository.hasOverlappingAppointment(
                            provider.getId(),
                            appointmentTime.plusHours(1),
                            appointmentTime.minusHours(1));
                })
                .map(skill -> {
                    ProviderProfile profile = skill.getProvider();
                    User provider = profile.getUser();
                    double distance = DistanceCalculator.kilometers(
                            latitude, longitude, profile.getLatitude(), profile.getLongitude());
                    return new ProviderMatchResponse(
                            provider.getId(),
                            provider.getFullName(),
                            skill.getCategory().getName(),
                            provider.getExperience(),
                            provider.getServiceArea(),
                            skill.getPrice(),
                            Math.round(distance * 10.0) / 10.0
                    );
                })
                .sorted(java.util.Comparator.comparingDouble(
                        ProviderMatchResponse::distanceKm))
                .toList();
        return ResponseEntity.ok(matches);
    }

    private User currentCustomer(Jwt jwt) {
        try {
            return userRepository.findById(Long.valueOf(jwt.getSubject()))
                    .filter(user -> user.isActive()
                            && "CUSTOMER".equalsIgnoreCase(user.getRole()))
                    .orElse(null);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
