package com.homefix.api.controller;

import com.homefix.api.dto.AdminDecisionRequest;
import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderVerificationDocument;
import com.homefix.api.model.ProviderVerificationDocumentType;
import com.homefix.api.model.User;
import com.homefix.api.repository.AuditLogRepository;
import com.homefix.api.repository.BookingRepository;
import com.homefix.api.repository.PaymentRepository;
import com.homefix.api.repository.PaymentTransactionRepository;
import com.homefix.api.repository.PaymentRefundRepository;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderSkillRepository;
import com.homefix.api.repository.ProviderVerificationDocumentRepository;
import com.homefix.api.repository.ReviewRepository;
import com.homefix.api.repository.ServiceCategoryRepository;
import com.homefix.api.repository.SupportTicketRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.BookingLifecycleService;
import com.homefix.api.service.PaymentConfigurationService;
import com.homefix.api.service.PricingConfigurationService;
import com.homefix.api.service.ProviderVerificationDocumentStorage;
import com.homefix.api.service.ServiceCategoryImageStorage;
import com.homefix.api.service.RazorpayPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminProviderVerificationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProviderProfileRepository providerRepository = mock(ProviderProfileRepository.class);
    private final ProviderVerificationDocumentRepository documentRepository =
            mock(ProviderVerificationDocumentRepository.class);
    private final ProviderVerificationDocumentStorage documentStorage =
            mock(ProviderVerificationDocumentStorage.class);
    private final AdminController controller = new AdminController(
            userRepository,
            providerRepository,
            mock(ProviderSkillRepository.class),
            documentRepository,
            documentStorage,
            mock(PaymentConfigurationService.class),
            mock(ServiceCategoryRepository.class),
            mock(BookingRepository.class),
            mock(ReviewRepository.class),
            mock(SupportTicketRepository.class),
            mock(PaymentRepository.class),
            mock(PaymentTransactionRepository.class),
            mock(PaymentRefundRepository.class),
            mock(AuditLogRepository.class),
            mock(BookingLifecycleService.class),
            mock(PricingConfigurationService.class),
            mock(RazorpayPaymentService.class),
            mock(ServiceCategoryImageStorage.class)
    );

    private final User admin = new User("Admin", "admin@example.test", "unused");
    private final User provider = new User("Provider", "provider@example.test", "unused");
    private final ProviderProfile profile = mock(ProviderProfile.class);
    private final Jwt adminJwt = Jwt.withTokenValue("admin-token")
            .header("alg", "HS256")
            .subject("1")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();

    @BeforeEach
    void setUp() {
        admin.setId(1L);
        admin.setRole("ADMIN");
        provider.setId(2L);
        provider.setRole("PROVIDER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(provider));
        when(providerRepository.findByUser_Id(2L)).thenReturn(Optional.of(profile));
        when(profile.getId()).thenReturn(42L);
    }

    @Test
    void profileApprovalIsBlockedUntilLatestRequiredDocumentsAreApproved() {
        ResponseEntity<?> response = controller.providerApproval(
                2L, adminJwt, new AdminDecisionRequest("APPROVED", ""));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(false, provider.isProviderApproved());
    }

    @Test
    void profileApprovalSucceedsAfterRequiredDocumentsAreApproved() {
        ProviderVerificationDocument identity = mock(ProviderVerificationDocument.class);
        ProviderVerificationDocument address = mock(ProviderVerificationDocument.class);
        when(identity.getStatus()).thenReturn("APPROVED");
        when(address.getStatus()).thenReturn("APPROVED");
        when(documentRepository.findFirstByProvider_IdAndDocumentTypeOrderByUploadedAtDescIdDesc(
                42L, ProviderVerificationDocumentType.IDENTITY_PROOF))
                .thenReturn(Optional.of(identity));
        when(documentRepository.findFirstByProvider_IdAndDocumentTypeOrderByUploadedAtDescIdDesc(
                42L, ProviderVerificationDocumentType.SERVICE_ADDRESS_PROOF))
                .thenReturn(Optional.of(address));

        ResponseEntity<?> response = controller.providerApproval(
                2L, adminJwt, new AdminDecisionRequest("APPROVED", ""));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, provider.isProviderApproved());
        verify(providerRepository).save(profile);
        verify(userRepository).save(provider);
    }

        @Test
        void userListHandlesLegacyRowsWithNullRoleOrCreatedAt() {
                User legacyUser = mock(User.class);
                when(legacyUser.getId()).thenReturn(99L);
                when(userRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(legacyUser));

                var response = controller.users();

                assertEquals("UNKNOWN", response.get(0).get("role"));
                assertEquals("", response.get(0).get("createdAt"));
        }
}