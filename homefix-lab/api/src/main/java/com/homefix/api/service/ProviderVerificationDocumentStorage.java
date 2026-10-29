package com.homefix.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class ProviderVerificationDocumentStorage {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final Path storageDirectory;

    public ProviderVerificationDocumentStorage(
            @Value("${app.provider-documents.directory:var/homefix-documents/provider-verification}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredDocument store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a document to upload.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Documents must be 10 MB or smaller.");
        }

        byte[] content = file.getBytes();
        if (content.length > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Documents must be 10 MB or smaller.");
        }
        DetectedType type = detectType(content);
        String storageKey = UUID.randomUUID() + type.extension();
        Files.createDirectories(storageDirectory);
        Path target = storageDirectory.resolve(storageKey).normalize();
        if (!target.startsWith(storageDirectory)) {
            throw new IOException("Invalid generated storage path.");
        }
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        return new StoredDocument(
                storageKey,
                type.contentType(),
                content.length,
                cleanFilename(file.getOriginalFilename())
        );
    }

    public Path resolve(String storageKey) throws IOException {
        if (storageKey == null || !storageKey.matches(
                "[0-9a-fA-F-]{36}\\.(pdf|png|jpg)")) {
            throw new IOException("Invalid document storage key.");
        }
        Path resolved = storageDirectory.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageDirectory) || !Files.isRegularFile(resolved)) {
            throw new IOException("Document file was not found.");
        }
        return resolved;
    }

    private DetectedType detectType(byte[] content) {
        if (content.length >= 5
                && content[0] == '%'
                && content[1] == 'P'
                && content[2] == 'D'
                && content[3] == 'F'
                && content[4] == '-') {
            return new DetectedType(".pdf", "application/pdf");
        }
        if (content.length >= 8
                && (content[0] & 0xff) == 0x89
                && content[1] == 'P'
                && content[2] == 'N'
                && content[3] == 'G'
                && (content[4] & 0xff) == 0x0d
                && (content[5] & 0xff) == 0x0a
                && (content[6] & 0xff) == 0x1a
                && (content[7] & 0xff) == 0x0a) {
            return new DetectedType(".png", "image/png");
        }
        if (content.length >= 3
                && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8
                && (content[2] & 0xff) == 0xff) {
            return new DetectedType(".jpg", "image/jpeg");
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Upload a valid PDF, PNG, or JPEG document.");
    }

    private String cleanFilename(String filename) {
        String normalized = StringUtils.cleanPath(filename == null ? "" : filename)
                .replace('\\', '/');
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (basename.isBlank()) {
            return "verification-document";
        }
        return basename.length() <= 255 ? basename : basename.substring(basename.length() - 255);
    }

    public record StoredDocument(
            String storageKey,
            String contentType,
            long sizeBytes,
            String originalFilename) {
    }

    private record DetectedType(String extension, String contentType) {
    }
}