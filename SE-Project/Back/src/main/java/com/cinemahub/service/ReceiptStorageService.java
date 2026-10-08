package com.cinemahub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Stores uploaded bank-transfer receipt files on disk (Bank Transfer approval flow -
 * see PaymentService#attachReceipt). Kept outside the OneDrive-synced project folder by
 * default, the same way the local H2 database file already is (see
 * application-h2.properties) - avoids the same kind of cloud-sync file-lock/permission
 * trouble, and receipts don't belong committed into source control anyway.
 */
@Service
public class ReceiptStorageService {

    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp", "application/pdf");

    // Matches spring.servlet.multipart.max-file-size (application.properties) - that limit
    // already rejects anything bigger before this code ever runs, this is just a second,
    // friendlier line of defence with its own clear message.
    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    @Value("${cinemahub.receipts.dir:${user.home}/AppData/Local/cinemahub/uploads/receipts}")
    private String uploadDir;

    /** Validates and saves the given file, returning where it landed. */
    public StoredReceipt store(MultipartFile file, Long paymentId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please choose a receipt file to upload");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException("Only JPG, PNG, WEBP or PDF receipts are accepted");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new IllegalArgumentException("Receipt file is too large - the limit is 5MB");
        }

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String storedName = "payment-" + paymentId + "-" + UUID.randomUUID() + extensionFor(contentType);
        Path target = dir.resolve(storedName);
        file.transferTo(target);

        return new StoredReceipt(target.toString(), file.getOriginalFilename(), contentType);
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    /** Where a just-stored receipt ended up, plus enough metadata to serve it back later. */
    public record StoredReceipt(String path, String originalFilename, String contentType) {
    }
}
