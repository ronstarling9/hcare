package com.hcare.api.v1.documents;

import com.hcare.api.v1.documents.dto.DocumentResponse;
import com.hcare.security.UserPrincipal;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/api/v1/clients/{clientId}/documents")
    public ResponseEntity<List<DocumentResponse>> listClientDocuments(@PathVariable UUID clientId) {
        return ResponseEntity.ok(documentService.listForClient(clientId));
    }

    @PostMapping(value = "/api/v1/clients/{clientId}/documents",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadClientDocument(
            @PathVariable UUID clientId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "documentType", required = false) String documentType,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(documentService.uploadForClient(clientId, file, documentType, principal.getUserId()));
    }

    @GetMapping("/api/v1/clients/{clientId}/documents/{docId}/content")
    public ResponseEntity<Void> downloadClientDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        String url = documentService.generateDownloadUrl(docId, clientId);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, url)
            .build();
    }

    @DeleteMapping("/api/v1/clients/{clientId}/documents/{docId}")
    public ResponseEntity<Void> deleteClientDocument(
            @PathVariable UUID clientId, @PathVariable UUID docId) {
        documentService.delete(docId, clientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/caregivers/{caregiverId}/documents")
    public ResponseEntity<List<DocumentResponse>> listCaregiverDocuments(@PathVariable UUID caregiverId) {
        return ResponseEntity.ok(documentService.listForCaregiver(caregiverId));
    }

    @PostMapping(value = "/api/v1/caregivers/{caregiverId}/documents",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadCaregiverDocument(
            @PathVariable UUID caregiverId,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "documentType", required = false) String documentType,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(documentService.uploadForCaregiver(caregiverId, file, documentType, principal.getUserId()));
    }

    @GetMapping("/api/v1/caregivers/{caregiverId}/documents/{docId}/content")
    public ResponseEntity<Void> downloadCaregiverDocument(
            @PathVariable UUID caregiverId, @PathVariable UUID docId) {
        String url = documentService.generateDownloadUrl(docId, caregiverId);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, url)
            .build();
    }

    @DeleteMapping("/api/v1/caregivers/{caregiverId}/documents/{docId}")
    public ResponseEntity<Void> deleteCaregiverDocument(
            @PathVariable UUID caregiverId, @PathVariable UUID docId) {
        documentService.delete(docId, caregiverId);
        return ResponseEntity.noContent().build();
    }
}
