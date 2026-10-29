package com.homefix.api.controller;

import com.homefix.api.dto.AvailabilityRequest;
import com.homefix.api.dto.LocationUpdateRequest;
import com.homefix.api.dto.ProviderProfileUpdateRequest;
import com.homefix.api.model.ProviderAvailability;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderSkill;
import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.ProviderAvailabilityRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.DistanceCalculator;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private static final Map<String, double[]> SIMULATION_LOCATIONS = Map.of(
            "MUMBAI", new double[]{19.0760, 72.8777},
            "PUNE", new double[]{18.5204, 73.8567}
    );

    private final UserRepository userRepository;
    private final ProviderProfileRepository profileRepository;
    private final ProviderSkillRepository skillRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final ProviderAvailabilityRepository availabilityRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final boolean simulationEnabled;

    public ProviderController(
            UserRepository userRepository,
            ProviderProfileRepository profileRepository,
            ProviderSkillRepository skillRepository,
            ServiceCategoryRepository categoryRepository,
            ProviderAvailabilityRepository availabilityRepository,
            BookingRepository bookingRepository,
            ReviewRepository reviewRepository,
            @Value("${app.location.simulation-enabled:false}") boolean simulationEnabled) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.skillRepository = skillRepository;
        this.categoryRepository = categoryRepository;
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository = bookingRepository;
        this.reviewRepository = reviewRepository;
        this.simulationEnabled = simulationEnabled;
    }

    @GetMapping("/profile")
    @Transactional(readOnly = true)
    public ResponseEntity<?> profile(@AuthenticationPrincipal Jwt jwt) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider verification profile is not available."));
        }
        Map<String, Object> view = new HashMap<>();
        view.put("userId", user.getId());
        view.put("fullName", user.getFullName());
        view.put("phone", user.getPhone() == null ? "" : user.getPhone());
        view.put("serviceCategory", user.getServiceCategory() == null ? "" : user.getServiceCategory());
        view.put("experience", user.getExperience() == null ? "" : user.getExperience());
        view.put("serviceArea", user.getServiceArea() == null ? "" : user.getServiceArea());
        view.put("providerApproved", user.isProviderApproved());
        view.put("verificationStatus", profile.getVerificationStatus());
        view.put("online", profile.isOnline());
        view.put("serviceRadiusKm", profile.getServiceRadiusKm());
        view.put("professionalSummary", profile.getProfessionalSummary() == null
                ? "" : profile.getProfessionalSummary());
        view.put("latitude", profile.getLatitude());
        view.put("longitude", profile.getLongitude());
        view.put("skills", skillRepository.findByProvider_Id(profile.getId()).stream()
                .map(skill -> Map.of(
                        "categoryId", skill.getCategory().getId(),
                        "category", skill.getCategory().getName(),
                        "price", skill.getPrice()
                )).toList());
        return ResponseEntity.ok(view);
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProviderProfileUpdateRequest request) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider profile must be registered before editing."));
        }
        if (request.professionalSummary() != null) {
            profile.setProfessionalSummary(request.professionalSummary().trim());
        }
        if (request.serviceRadiusKm() != null) {
            profile.setServiceRadiusKm(request.serviceRadiusKm());
        }
        if (request.online() != null) {
            if (request.online() && (!user.isProviderApproved()
                    || !"APPROVED".equalsIgnoreCase(profile.getVerificationStatus()))) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Provider verification must be approved before going online."));
            }
            profile.setOnline(request.online());
        }
        if (request.serviceArea() != null && !request.serviceArea().isBlank()) {
            user.setServiceArea(request.serviceArea().trim());
        }
        if (request.experience() != null && !request.experience().isBlank()) {
            user.setExperience(request.experience().trim());
        }
        if (request.serviceCategory() != null && !request.serviceCategory().isBlank()) {
            ServiceCategory category = findCategory(request.serviceCategory());
            if (category == null || !category.isActive()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Select an active service category."));
            }
            user.setServiceCategory(category.getServiceCode());
            List<ProviderSkill> existing = skillRepository.findByProvider_Id(profile.getId());
            existing.stream().filter(skill -> !skill.getCategory().getId().equals(category.getId()))
                    .forEach(skillRepository::delete);
            if (existing.stream().noneMatch(skill -> skill.getCategory().getId().equals(category.getId()))) {
                skillRepository.save(new ProviderSkill(profile, category, category.getStartingPrice()));
            }
            user.setProviderApproved(false);
            profile.setVerificationStatus("PENDING");
            profile.setOnline(false);
        }
        userRepository.save(user);
        profileRepository.save(profile);
        return ResponseEntity.ok(Map.of(
                "message", "Provider profile updated. Changes to service category require re-verification.",
                "providerApproved", user.isProviderApproved(),
                "verificationStatus", profile.getVerificationStatus()
        ));
    }

    @PutMapping("/location")
    @Transactional
    public ResponseEntity<?> updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LocationUpdateRequest request) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        Double latitude = request.latitude();
        Double longitude = request.longitude();
        if (request.city() != null && !request.city().isBlank()) {
            if (!simulationEnabled) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Development location simulation is disabled."));
            }
            double[] coordinates = SIMULATION_LOCATIONS.get(
                    request.city().trim().toUpperCase(Locale.ROOT));
            if (coordinates == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Simulation supports Mumbai and Pune."));
            }
            latitude = coordinates[0];
            longitude = coordinates[1];
            user.setServiceArea(request.city().trim());
            userRepository.save(user);
        }
        if (!DistanceCalculator.validCoordinates(latitude, longitude)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Provide valid latitude/longitude or a simulation city."));
        }
        profile.setLatitude(latitude);
        profile.setLongitude(longitude);
        profileRepository.save(profile);
        return ResponseEntity.ok(Map.of("message", "Provider service location saved."));
    }

    @GetMapping("/availability")
    public ResponseEntity<?> availability(@AuthenticationPrincipal Jwt jwt) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(availabilityRepository
                .findByProvider_IdOrderByDayOfWeekAscStartTimeAsc(profile.getId())
                .stream()
                .map(slot -> Map.of(
                        "id", slot.getId(),
                        "dayOfWeek", slot.getDayOfWeek().name(),
                        "startTime", slot.getStartTime().toString(),
                        "endTime", slot.getEndTime().toString(),
                        "enabled", slot.isEnabled()
                )).toList());
    }

    @PutMapping("/availability")
    @Transactional
    public ResponseEntity<?> updateAvailability(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody List<@Valid AvailabilityRequest> requests) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        for (int i = 0; i < requests.size(); i++) {
            AvailabilityRequest slot = requests.get(i);
            if (!slot.endTime().isAfter(slot.startTime())) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "Availability end time must follow its start time."));
            }
            for (int j = i + 1; j < requests.size(); j++) {
                AvailabilityRequest other = requests.get(j);
                if (slot.enabled() && other.enabled()
                        && slot.dayOfWeek() == other.dayOfWeek()
                        && slot.startTime().isBefore(other.endTime())
                        && other.startTime().isBefore(slot.endTime())) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("message", "Availability slots on the same day cannot overlap."));
                }
            }
        }
        availabilityRepository.deleteByProvider_Id(profile.getId());
        List<ProviderAvailability> slots = requests.stream().map(request -> {
            ProviderAvailability slot = new ProviderAvailability();
            slot.setProvider(profile);
            slot.setDayOfWeek(request.dayOfWeek());
            slot.setStartTime(request.startTime());
            slot.setEndTime(request.endTime());
            slot.setEnabled(request.enabled());
            return slot;
        }).toList();
        availabilityRepository.saveAll(slots);
        return ResponseEntity.ok(Map.of("message", "Working hours saved.", "count", slots.size()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal Jwt jwt) {
        User user = currentProvider(jwt);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        return ResponseEntity.ok(Map.of(
                "completedJobs", bookingRepository.countByProviderAndStatusIgnoreCase(user, "COMPLETED"),
                "acceptedJobs", bookingRepository.countByProvider(user),
                "earnings", bookingRepository.sumProviderCompletedBookingPrices(user.getId()),
                "reviewCount", reviewRepository.findByProvider_IdAndVisibleTrueOrderByCreatedAtDesc(
                        user.getId()).size(),
                "verificationStatus", profileRepository.findByUser_Id(user.getId())
                        .map(ProviderProfile::getVerificationStatus).orElse("PENDING")
        ));
    }

    private ServiceCategory findCategory(String value) {
        return categoryRepository.findByServiceCodeIgnoreCase(value)
                .or(() -> categoryRepository.findBySlugIgnoreCase(value))
                .orElse(null);
    }

    private User currentProvider(Jwt jwt) {
        try {
            return userRepository.findById(Long.valueOf(jwt.getSubject()))
                    .filter(user -> user.isActive()
                            && "PROVIDER".equalsIgnoreCase(user.getRole()))
                    .orElse(null);
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }
}
