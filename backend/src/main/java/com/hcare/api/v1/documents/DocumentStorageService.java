package com.hcare.api.v1.documents;

import com.hcare.domain.DocumentOwnerType;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.UUID;

/**
 * Abstraction over document storage backends.
 * Local: stores files on filesystem and streams bytes directly.
 * S3 (future): stores in S3, returns pre-signed GET URLs or streams via SDK.
 * Upload always goes through the backend. Download streams bytes directly to the caller.
 */
public interface DocumentStorageService {
    String store(MultipartFile file, UUID agencyId, DocumentOwnerType ownerType, UUID ownerId);
    InputStream loadStream(String storageKey);
    void delete(String storageKey);
}
