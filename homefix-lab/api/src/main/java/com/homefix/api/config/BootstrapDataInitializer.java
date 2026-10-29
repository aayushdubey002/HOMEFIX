package com.homefix.api.config;

import com.homefix.api.model.ServiceCategory;
import com.homefix.api.model.User;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.ServiceCategoryImages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private final ServiceCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public BootstrapDataInitializer(
            ServiceCategoryRepository categoryRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.bootstrap-email:}") String adminEmail,
            @Value("${app.admin.bootstrap-password:}") String adminPassword) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCategories();
        bootstrapAdmin();
    }

    private void seedCategories() {
        List<ServiceCategory> defaults = List.of(
                category("Electrician", "electrician", "ELECTRICAL",
                        "Wiring, lighting and electrical repairs.", "500.00"),
                category("Plumber", "plumber", "PLUMBING",
                        "Pipes, taps, drains and plumbing repairs.", "300.00"),
                category("Carpenter", "carpenter", "CARPENTER",
                        "Furniture, fittings and carpentry repairs.", "400.00"),
                category("AC Repair", "ac-repair", "AC REPAIR",
                        "Air-conditioner service and repair.", "700.00"),
                category("Appliance Repair", "appliance-repair", "APPLIANCE REPAIR",
                        "Home appliance diagnosis and repair.", "600.00"),
                category("Cleaning", "cleaning", "CLEANING",
                        "Home cleaning services.", "999.00"),
                category("Painting", "painting", "PAINTING",
                        "Interior and exterior home painting.", "1500.00")
        );
        for (ServiceCategory category : defaults) {
            if (!categoryRepository.existsBySlugIgnoreCase(category.getSlug())
                    && !categoryRepository.existsByServiceCodeIgnoreCase(
                            category.getServiceCode())) {
                categoryRepository.save(category);
            }
        }
    }

    private ServiceCategory category(String name, String slug, String code,
                                     String description, String price) {
        ServiceCategory category = new ServiceCategory(
                name, slug, code, description, new BigDecimal(price));
        category.setImageKey(ServiceCategoryImages.resolve(null, name, slug, code));
        return category;
    }

    private void bootstrapAdmin() {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            return;
        }
        if (adminEmail.isBlank() || adminPassword.length() < 12) {
            throw new IllegalStateException(
                    "Configure both ADMIN_BOOTSTRAP_EMAIL and an ADMIN_BOOTSTRAP_PASSWORD "
                            + "of at least 12 characters, or leave both empty.");
        }
        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        User existing = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existing != null) {
            if (!"ADMIN".equalsIgnoreCase(existing.getRole())) {
                throw new IllegalStateException(
                        "The configured bootstrap admin email belongs to a non-admin user.");
            }
            return;
        }
        User admin = new User();
        admin.setFullName("HomeFix Administrator");
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        admin.setProviderApproved(false);
        userRepository.save(admin);
    }
}
