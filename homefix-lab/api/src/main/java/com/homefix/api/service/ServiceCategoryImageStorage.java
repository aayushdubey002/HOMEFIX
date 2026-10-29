package com.homefix.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class ServiceCategoryImageStorage {

    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 25_000_000L;

    private final Path storageDirectory;

    public ServiceCategoryImageStorage(
            @Value("${app.service-category-images.directory:var/homefix-images/service-categories}")
            String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredImage store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image to upload.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Service images must be 5 MB or smaller.");
        }

        byte[] content = file.getBytes();
        if (content.length > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Service images must be 5 MB or smaller.");
        }

        DetectedImage image = detectImage(content);
        Files.createDirectories(storageDirectory);
        String storageKey = UUID.randomUUID() + image.extension();
        Path target = resolvePath(storageKey);
        Path temporary = Files.createTempFile(storageDirectory, "service-image-", ".tmp");
        try {
            Files.write(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temporary, target);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }

        return new StoredImage(
                storageKey,
                "/api/services/catalog/images/" + storageKey,
                image.contentType());
    }

    public Path resolve(String storageKey) throws IOException {
        Path image = resolvePath(storageKey);
        if (!Files.isRegularFile(image)) {
            throw new IOException("Service image was not found.");
        }
        return image;
    }

    public String contentType(String storageKey) {
        return storageKey.endsWith(".png") ? "image/png" : "image/jpeg";
    }

    public void delete(String storageKey) throws IOException {
        if (storageKey != null && !storageKey.isBlank()) {
            Files.deleteIfExists(resolvePath(storageKey));
        }
    }

    public String storageKeyFromUrl(String imageUrl) {
        if (imageUrl == null || !imageUrl.matches(
                "/api/services/catalog/images/[0-9a-fA-F-]{36}\\.(png|jpg)")) {
            return null;
        }
        return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
    }

    private Path resolvePath(String storageKey) throws IOException {
        if (storageKey == null || !storageKey.matches(
                "[0-9a-fA-F-]{36}\\.(png|jpg)")) {
            throw new IOException("Invalid service image key.");
        }
        Path image = storageDirectory.resolve(storageKey).normalize();
        if (!image.startsWith(storageDirectory)) {
            throw new IOException("Invalid service image path.");
        }
        return image;
    }

    private DetectedImage detectImage(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content))) {
            if (input == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalidImage();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw invalidImage();
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Image dimensions must be 25 megapixels or smaller.");
                }
                reader.read(0);
                return format.equals("png")
                        ? new DetectedImage(".png", "image/png")
                        : new DetectedImage(".jpg", "image/jpeg");
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload a valid JPEG or PNG image.", error);
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Upload a valid JPEG or PNG image.");
    }

    public record StoredImage(String storageKey, String imageUrl, String contentType) {
    }

    private record DetectedImage(String extension, String contentType) {
    }
}
