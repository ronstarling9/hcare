package com.hcare.api.v1.documents;

import com.hcare.domain.DocumentOwnerType;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

/**
 * Abstraction over document storage backends.
 * Local: stores files on filesystem, returns HMAC-signed backend URLs.
 * S3 (future): stores in S3, returns pre-signed GET URLs.
 * Upload always goes through the backend. Download always returns a redirect URL.
 */
public interface DocumentStorageService {
    String store(MultipartFile file, UUID agencyId, DocumentOwnerType ownerType, UUID ownerId);
    String generateDownloadUrl(String storageKey);
    void delete(String storageKey);
}
