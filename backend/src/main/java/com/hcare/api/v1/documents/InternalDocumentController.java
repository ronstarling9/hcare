package com.hcare.api.v1.documents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "hcare.storage.provider", havingValue = "local", matchIfMissing = true)
public class InternalDocumentController {

    private final LocalDocumentStorageService storageService;

    public InternalDocumentController(LocalDocumentStorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/api/v1/internal/documents/content")
    public ResponseEntity<InputStreamResource> stream(@RequestParam String token) {
        String storageKey = storageService.validateAndExtractKey(token);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new InputStreamResource(storageService.loadStream(storageKey)));
    }
}
