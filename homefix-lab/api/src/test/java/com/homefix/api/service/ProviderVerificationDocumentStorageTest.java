package com.homefix.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderVerificationDocumentStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesSignatureValidatedImageWithGeneratedName() throws Exception {
        ProviderVerificationDocumentStorage storage =
                new ProviderVerificationDocumentStorage(temporaryDirectory.toString());
        byte[] pngHeader = {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../identity.png", "image/png", pngHeader);

        ProviderVerificationDocumentStorage.StoredDocument stored = storage.store(file);

        assertEquals("image/png", stored.contentType());
        assertEquals("identity.png", stored.originalFilename());
        assertTrue(stored.storageKey().matches("[0-9a-fA-F-]{36}\\.png"));
        assertTrue(Files.exists(storage.resolve(stored.storageKey())));
    }

    @Test
    void rejectsFileWhoseContentDoesNotMatchAnAllowedSignature() {
        ProviderVerificationDocumentStorage storage =
                new ProviderVerificationDocumentStorage(temporaryDirectory.toString());
        MockMultipartFile spoofedPdf = new MockMultipartFile(
                "file", "identity.pdf", "application/pdf", "not a PDF".getBytes());

        assertThrows(ResponseStatusException.class, () -> storage.store(spoofedPdf));
    }
}