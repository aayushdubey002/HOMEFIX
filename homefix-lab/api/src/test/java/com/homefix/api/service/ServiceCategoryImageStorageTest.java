package com.homefix.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCategoryImageStorageTest {

    @TempDir
    Path storageDirectory;

    @Test
    void storesValidPngUnderGeneratedKeyAndResolvesIt() throws IOException {
        ServiceCategoryImageStorage storage =
                new ServiceCategoryImageStorage(storageDirectory.toString());
        byte[] content = pngImage();

        ServiceCategoryImageStorage.StoredImage stored = storage.store(
                new MockMultipartFile("image", "service.png", "image/png", content));

        assertTrue(stored.storageKey().matches("[0-9a-f-]{36}\\.png"));
        assertEquals("/api/services/catalog/images/" + stored.storageKey(), stored.imageUrl());
        assertEquals("image/png", stored.contentType());
        assertEquals(stored.storageKey(),
                storage.storageKeyFromUrl(stored.imageUrl()));
        assertArrayEquals(content, Files.readAllBytes(storage.resolve(stored.storageKey())));
    }

    @Test
    void rejectsNonImageFilesAndFilesAboveTheSizeLimit() {
        ServiceCategoryImageStorage storage =
                new ServiceCategoryImageStorage(storageDirectory.toString());
        MockMultipartFile invalidImage = new MockMultipartFile(
                "image", "notes.txt", "text/plain", "not an image".getBytes());
        MockMultipartFile oversizedImage = new MockMultipartFile(
                "image", "large.png", "image/png",
                new byte[(int) ServiceCategoryImageStorage.MAX_IMAGE_SIZE + 1]);

        assertThrows(ResponseStatusException.class, () -> storage.store(invalidImage));
        assertThrows(ResponseStatusException.class, () -> storage.store(oversizedImage));
    }

    private byte[] pngImage() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
