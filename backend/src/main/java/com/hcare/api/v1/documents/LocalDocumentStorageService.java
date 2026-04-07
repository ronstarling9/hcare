package com.hcare.api.v1.documents;

import com.hcare.domain.DocumentOwnerType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "hcare.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalDocumentStorageService implements DocumentStorageService {

    private static final long TOKEN_TTL_MS = 15 * 60 * 1000L;

    private final Path baseDir;
    private final String signingKey;
    private final String baseUrl;

    public LocalDocumentStorageService(
            @Value("${hcare.storage.documents-dir}") String documentsDir,
            @Value("${hcare.storage.signing-key}") String signingKey,
            @Value("${hcare.storage.base-url:http://localhost:8080}") String baseUrl) {
        this.baseDir = Path.of(documentsDir);
        this.signingKey = signingKey;
        this.baseUrl = baseUrl;
    }

    @Override
    public String store(MultipartFile file, UUID agencyId, DocumentOwnerType ownerType, UUID ownerId) {
        try {
            String ownerFolder = ownerType == DocumentOwnerType.CLIENT ? "clients" : "caregivers";
            Path dir = baseDir.resolve(agencyId.toString())
                              .resolve(ownerFolder)
                              .resolve(ownerId.toString());
            Files.createDirectories(dir);
            String storedName = UUID.randomUUID() + "-" + sanitize(file.getOriginalFilename());
            Path target = dir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return agencyId + "/" + ownerFolder + "/" + ownerId + "/" + storedName;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store document", e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        long expiry = System.currentTimeMillis() + TOKEN_TTL_MS;
        String encodedKey = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(storageKey.getBytes(StandardCharsets.UTF_8));
        String payload = encodedKey + ":" + expiry;
        String token = payload + "." + hmac(payload);
        return baseUrl + "/api/v1/internal/documents/content?token="
            + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path resolved = baseDir.resolve(storageKey).normalize();
            if (!resolved.startsWith(baseDir.normalize())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid storage key");
            }
            Files.deleteIfExists(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete document", e);
        }
    }

    public String validateAndExtractKey(String token) {
        String[] parts = token.split("\\.", 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token");
        }
        String payload = parts[0];
        if (!java.security.MessageDigest.isEqual(
                hmac(payload).getBytes(StandardCharsets.UTF_8),
                parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token");
        }
        String[] payloadParts = payload.split(":", 2);
        if (payloadParts.length != 2) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token");
        }
        long expiry;
        try {
            expiry = Long.parseLong(payloadParts[1]);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid download token");
        }
        if (System.currentTimeMillis() > expiry) {
            throw new ResponseStatusException(HttpStatus.GONE, "Download link has expired");
        }
        return new String(Base64.getUrlDecoder().decode(payloadParts[0]), StandardCharsets.UTF_8);
    }

    public InputStream loadStream(String storageKey) {
        try {
            Path resolved = baseDir.resolve(storageKey).normalize();
            if (!resolved.startsWith(baseDir.normalize())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid storage key");
            }
            return Files.newInputStream(resolved);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read document", e);
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing error", e);
        }
    }

    private String sanitize(String name) {
        if (name == null) return "upload";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
