package com.hcare.api.v1.documents;

import com.hcare.api.v1.documents.dto.DocumentResponse;
import com.hcare.domain.*;
import com.hcare.multitenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ClientRepository clientRepository;
    private final CaregiverRepository caregiverRepository;
    private final DocumentStorageService storageService;

    public DocumentService(DocumentRepository documentRepository,
                           ClientRepository clientRepository,
                           CaregiverRepository caregiverRepository,
                           DocumentStorageService storageService) {
        this.documentRepository = documentRepository;
        this.clientRepository = clientRepository;
        this.caregiverRepository = caregiverRepository;
        this.storageService = storageService;
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForClient(UUID clientId) {
        requireClient(clientId);
        UUID agencyId = TenantContext.get();
        return documentRepository
            .findByAgencyIdAndOwnerTypeAndOwnerId(agencyId, DocumentOwnerType.CLIENT, clientId)
            .stream().map(DocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForCaregiver(UUID caregiverId) {
        requireCaregiver(caregiverId);
        UUID agencyId = TenantContext.get();
        return documentRepository
            .findByAgencyIdAndOwnerTypeAndOwnerId(agencyId, DocumentOwnerType.CAREGIVER, caregiverId)
            .stream().map(DocumentResponse::from).toList();
    }

    @Transactional
    public DocumentResponse uploadForClient(UUID clientId, MultipartFile file,
                                             String documentType, UUID uploadedBy) {
        requireClient(clientId);
        return upload(DocumentOwnerType.CLIENT, clientId, file, documentType, uploadedBy);
    }

    @Transactional
    public DocumentResponse uploadForCaregiver(UUID caregiverId, MultipartFile file,
                                                String documentType, UUID uploadedBy) {
        requireCaregiver(caregiverId);
        return upload(DocumentOwnerType.CAREGIVER, caregiverId, file, documentType, uploadedBy);
    }

    @Transactional(readOnly = true)
    public String generateDownloadUrl(UUID documentId, UUID expectedOwnerId) {
        Document doc = requireDocument(documentId, expectedOwnerId);
        return storageService.generateDownloadUrl(doc.getFilePath());
    }

    @Transactional
    public void delete(UUID documentId, UUID expectedOwnerId) {
        Document doc = requireDocument(documentId, expectedOwnerId);
        documentRepository.delete(doc);
        // Note: filesystem delete runs before commit. If storageService.delete throws,
        // the transaction rolls back (DB row survives). Acceptable for MVP local storage.
        storageService.delete(doc.getFilePath());
    }

    private DocumentResponse upload(DocumentOwnerType ownerType, UUID ownerId,
                                     MultipartFile file, String documentType, UUID uploadedBy) {
        UUID agencyId = TenantContext.get();
        // Note: filesystem write runs before DB commit. If documentRepository.save() fails,
        // the file is already stored — orphaned file with no DB entry. Acceptable for MVP local storage.
        String storageKey = storageService.store(file, agencyId, ownerType, ownerId);
        Document doc = new Document(agencyId, ownerType, ownerId,
            file.getOriginalFilename(), storageKey);
        if (documentType != null) doc.setDocumentType(documentType);
        if (uploadedBy != null) doc.setUploadedBy(uploadedBy);
        return DocumentResponse.from(documentRepository.save(doc));
    }

    private void requireClient(UUID clientId) {
        // Hibernate agencyFilter (TenantFilterAspect) scopes findById to the current tenant.
        clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private void requireCaregiver(UUID caregiverId) {
        // Hibernate agencyFilter (TenantFilterAspect) scopes findById to the current tenant.
        caregiverRepository.findById(caregiverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));
    }

    private Document requireDocument(UUID documentId, UUID expectedOwnerId) {
        UUID agencyId = TenantContext.get();
        Document doc = documentRepository.findById(documentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        if (!doc.getAgencyId().equals(agencyId) || !doc.getOwnerId().equals(expectedOwnerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");
        }
        return doc;
    }
}
