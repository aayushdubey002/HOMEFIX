package com.homefix.api.controller;

import com.homefix.api.model.ProviderProfile;
import com.homefix.api.model.ProviderVerificationDocument;
import com.homefix.api.model.ProviderVerificationDocumentType;
import com.homefix.api.model.User;
import com.homefix.api.repository.ProviderProfileRepository;
import com.homefix.api.repository.ProviderVerificationDocumentRepository;
import com.homefix.api.repository.UserRepository;
import com.homefix.api.service.ProviderVerificationDocumentStorage;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/provider/verification-documents")
public class ProviderVerificationDocumentController {

    private final UserRepository userRepository;
    private final ProviderProfileRepository profileRepository;
    private final ProviderVerificationDocumentRepository documentRepository;
    private final ProviderVerificationDocumentStorage storage;

    public ProviderVerificationDocumentController(
            UserRepository userRepository,
            ProviderProfileRepository profileRepository,
            ProviderVerificationDocumentRepository documentRepository,
            ProviderVerificationDocumentStorage storage) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.documentRepository = documentRepository;
        this.storage = storage;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<?> documents(@AuthenticationPrincipal Jwt jwt) {
        User provider = currentProvider(jwt);
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUser_Id(provider.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider profile is not available."));
        }
        return ResponseEntity.ok(latestDocuments(profile));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam ProviderVerificationDocumentType documentType,
            @RequestParam("file") @NotNull MultipartFile file) throws IOException {
        User provider = currentProvider(jwt);
        if (provider == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Provider account required."));
        }
        ProviderProfile profile = profileRepository.findByUserIdForUpdate(provider.getId()).orElse(null);
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Provider profile is not available."));
        }
        if ("APPROVED".equalsIgnoreCase(profile.getVerificationStatus())
                || provider.isProviderApproved()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Your provider verification is complete. No additional documents are needed."));
        }
        boolean requiredDocumentsApproved = requiredDocumentApproved(
                profile, ProviderVerificationDocumentType.IDENTITY_PROOF)
                && requiredDocumentApproved(
                profile, ProviderVerificationDocumentType.SERVICE_ADDRESS_PROOF);
        if (requiredDocumentsApproved) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message",
                            "Your required documents are approved and awaiting account review. No further upload is needed."));
        }
        ProviderVerificationDocument latestDocument = documentRepository
                .findLatestByProviderAndTypeForUpdate(profile.getId(), documentType)
                .stream().findFirst().orElse(null);
        if (latestDocument != null && !"REJECTED".equalsIgnoreCase(latestDocument.getStatus())) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("message",
                    "This document is already submitted or approved. Upload a replacement only if it was rejected.");
            response.put("document", documentView(latestDocument));
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(response);
        }

        ProviderVerificationDocumentStorage.StoredDocument stored = storage.store(file);
        ProviderVerificationDocument document = documentRepository.save(
                new ProviderVerificationDocument(
                        profile,
                        documentType,
                        stored.originalFilename(),
                        stored.storageKey(),
                        stored.contentType(),
                        stored.sizeBytes()
                ));
        provider.setProviderApproved(false);
        profile.setVerificationStatus("PENDING");
        profile.setOnline(false);
        profileRepository.save(profile);
        userRepository.save(provider);

        return ResponseEntity.status(HttpStatus.CREATED).body(documentView(document));
    }

    private boolean requiredDocumentApproved(
            ProviderProfile profile, ProviderVerificationDocumentType documentType) {
        return documentRepository.findLatestByProviderAndTypeForUpdate(profile.getId(), documentType)
                .stream()
                .findFirst()
                .map(document -> "APPROVED".equalsIgnoreCase(document.getStatus()))
                .orElse(false);
    }

    private List<Map<String, Object>> latestDocuments(ProviderProfile profile) {
        return java.util.Arrays.stream(ProviderVerificationDocumentType.values())
                .map(type -> documentRepository
                        .findFirstByProvider_IdAndDocumentTypeOrderByUploadedAtDescIdDesc(
                                profile.getId(), type))
                .flatMap(Optional::stream)
                .map(ProviderVerificationDocumentController::documentView)
                .toList();
    }

    static Map<String, Object> documentView(ProviderVerificationDocument document) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("documentType", document.getDocumentType().name());
        view.put("originalFilename", document.getOriginalFilename());
        view.put("contentType", document.getContentType());
        view.put("sizeBytes", document.getSizeBytes());
        view.put("status", document.getStatus());
        view.put("reviewNote", document.getReviewNote() == null ? "" : document.getReviewNote());
        view.put("uploadedAt", document.getUploadedAt());
        view.put("reviewedAt", document.getReviewedAt());
        return view;
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