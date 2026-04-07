package com.hcare.api.v1.documents;

import com.hcare.domain.Document;
import com.hcare.domain.DocumentRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "hcare.storage.provider", havingValue = "local", matchIfMissing = true)
public class InternalDocumentController {

    private final LocalDocumentStorageService storageService;
    private final DocumentRepository documentRepository;

    public InternalDocumentController(LocalDocumentStorageService storageService,
                                      DocumentRepository documentRepository) {
        this.storageService = storageService;
        this.documentRepository = documentRepository;
    }

    @GetMapping("/api/v1/internal/documents/content")
    public ResponseEntity<InputStreamResource> stream(@RequestParam String token) {
        String storageKey = storageService.validateAndExtractKey(token);

        Document document = documentRepository.findByFilePath(storageKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        long fileSize = storageService.getFileSize(storageKey);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + document.getFileName() + "\"")
                .contentLength(fileSize)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(storageService.loadStream(storageKey)));
    }
}
