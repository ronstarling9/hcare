# Clients & Caregivers REST API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose full CRUD + sub-resources for clients (diagnoses, medications, care plans with ADL tasks and goals, authorizations, family portal users) and caregivers (credentials, background checks, availability, shift history) via REST APIs consumed by the admin web app.

**Architecture:** Two new service+controller pairs: `ClientService`/`ClientController` at `/api/v1/clients` and `CaregiverService`/`CaregiverController` at `/api/v1/caregivers`. Services own all business logic including care plan lifecycle enforcement (one ACTIVE per client) and availability replace-all writes. All domain repositories and entities already exist from Plans 2–3; several entities need additional setters added. Documents are out of scope (file storage infrastructure is P2).

**Tech Stack:** Java 25, Spring Boot 3.4.4, JPA/Hibernate with `@Filter` for multi-tenancy, `jakarta.validation` on request DTOs, JUnit 5 + Mockito for service unit tests.

---

## File Map

**New packages:**

```
api/v1/clients/
  ClientController.java
  ClientService.java
  dto/
    CreateClientRequest.java       UpdateClientRequest.java        ClientResponse.java
    AddDiagnosisRequest.java       DiagnosisResponse.java
    AddMedicationRequest.java      UpdateMedicationRequest.java    MedicationResponse.java
    CreateCarePlanRequest.java     CarePlanResponse.java
    AddAdlTaskRequest.java         AdlTaskResponse.java
    AddGoalRequest.java            UpdateGoalRequest.java          GoalResponse.java
    CreateAuthorizationRequest.java  AuthorizationResponse.java
    AddFamilyPortalUserRequest.java  FamilyPortalUserResponse.java

api/v1/caregivers/
  CaregiverController.java
  CaregiverService.java
  dto/
    CreateCaregiverRequest.java    UpdateCaregiverRequest.java     CaregiverResponse.java
    AddCredentialRequest.java      CredentialResponse.java
    RecordBackgroundCheckRequest.java  BackgroundCheckResponse.java
    SetAvailabilityRequest.java    AvailabilityBlockRequest.java   AvailabilityResponse.java
```

**Modified domain entities** (add setters only — no logic changes):
- `domain/Client.java` — add `setFirstName`, `setLastName`, `setDateOfBirth`, `setAddress`, `setPhone`, `setStatus`, `setPreferredCaregiverGender`
- `domain/Caregiver.java` — add `setFirstName`, `setLastName`, `setEmail`, `setPhone`, `setAddress`, `setHireDate`, `setHasPet`, `setStatus`
- `domain/ClientMedication.java` — add `setName`, `setDosage`, `setRoute`, `setSchedule`, `setPrescriber`
- `domain/AdlTask.java` — add `setName`, `setInstructions`, `setAssistanceLevel`, `setFrequency`, `setSortOrder`
- `domain/Goal.java` — add `setDescription`, `setTargetDate`, `setStatus`
- `domain/FamilyPortalUser.java` — add `setName`

**Test files:**
```
test/java/com/hcare/api/v1/clients/ClientServiceTest.java
test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java
```

---

## Task 1: Client Core CRUD

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/Client.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/CreateClientRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateClientRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/ClientResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`
- Create: `backend/src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java`:

```java
package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import com.hcare.domain.ClientStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clientRepository;

    ClientService service;

    UUID agencyId = UUID.randomUUID();
    UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ClientService(clientRepository);
    }

    private Client makeClient() {
        return new Client(agencyId, "Alice", "Smith", LocalDate.of(1960, 3, 15));
    }

    // --- listClients ---

    @Test
    void listClients_returns_all_clients_for_agency() {
        Client client = makeClient();
        when(clientRepository.findByAgencyId(agencyId)).thenReturn(List.of(client));

        List<ClientResponse> result = service.listClients(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("Alice");
        verify(clientRepository).findByAgencyId(agencyId);
    }

    // --- createClient ---

    @Test
    void createClient_saves_and_returns_response() {
        CreateClientRequest req = new CreateClientRequest(
            "Bob", "Jones", LocalDate.of(1975, 6, 20),
            "123 Main St", "555-1234", null, null, null, "[]", false);
        Client saved = new Client(agencyId, "Bob", "Jones", LocalDate.of(1975, 6, 20));
        when(clientRepository.save(any(Client.class))).thenReturn(saved);

        ClientResponse result = service.createClient(agencyId, req);

        assertThat(result.firstName()).isEqualTo("Bob");
        verify(clientRepository).save(any(Client.class));
    }

    // --- getClient ---

    @Test
    void getClient_returns_client_when_found() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        ClientResponse result = service.getClient(agencyId, clientId);

        assertThat(result.firstName()).isEqualTo("Alice");
    }

    @Test
    void getClient_throws_404_when_not_found() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getClient(agencyId, clientId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void getClient_throws_404_when_belongs_to_other_agency() {
        Client client = new Client(UUID.randomUUID(), "Alice", "Smith", LocalDate.of(1960, 3, 15));
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.getClient(agencyId, clientId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updateClient ---

    @Test
    void updateClient_applies_non_null_fields_and_saves() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(client)).thenReturn(client);

        UpdateClientRequest req = new UpdateClientRequest(
            "Alicia", null, null, "456 Oak Ave", null, null, null, null, null, null, null);

        ClientResponse result = service.updateClient(agencyId, clientId, req);

        assertThat(result.firstName()).isEqualTo("Alicia");
        assertThat(result.address()).isEqualTo("456 Oak Ave");
        assertThat(result.lastName()).isEqualTo("Smith"); // unchanged
        verify(clientRepository).save(client);
    }

    @Test
    void updateClient_throws_404_when_not_found() {
        when(clientRepository.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateClient(agencyId, clientId,
            new UpdateClientRequest(null, null, null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void updateClient_can_set_status_to_discharged() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(clientRepository.save(client)).thenReturn(client);

        UpdateClientRequest req = new UpdateClientRequest(
            null, null, null, null, null, null, null, null, null, null, ClientStatus.DISCHARGED);

        service.updateClient(agencyId, clientId, req);

        assertThat(client.getStatus()).isEqualTo(ClientStatus.DISCHARGED);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE` — `ClientService` does not exist yet.

- [ ] **Step 3: Add missing setters to Client entity**

In `backend/src/main/java/com/hcare/domain/Client.java`, add these setters after the existing ones (after line `public void setMedicaidId(...)`):

```java
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setStatus(ClientStatus status) { this.status = status; }
    public void setPreferredCaregiverGender(String preferredCaregiverGender) { this.preferredCaregiverGender = preferredCaregiverGender; }
```

- [ ] **Step 4: Create DTOs**

`backend/src/main/java/com/hcare/api/v1/clients/dto/CreateClientRequest.java`:

```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateClientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    Boolean noPetCaregiver
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateClientRequest.java`:

```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateClientRequest(
    @Size(min = 1) String firstName,
    @Size(min = 1) String lastName,
    LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    Boolean noPetCaregiver,
    ClientStatus status
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/ClientResponse.java`:

```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Client;
import com.hcare.domain.ClientStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    UUID agencyId,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String address,
    String phone,
    String medicaidId,
    String serviceState,
    String preferredCaregiverGender,
    String preferredLanguages,
    boolean noPetCaregiver,
    ClientStatus status,
    LocalDateTime createdAt
) {
    public static ClientResponse from(Client c) {
        return new ClientResponse(
            c.getId(), c.getAgencyId(), c.getFirstName(), c.getLastName(),
            c.getDateOfBirth(), c.getAddress(), c.getPhone(), c.getMedicaidId(),
            c.getServiceState(), c.getPreferredCaregiverGender(), c.getPreferredLanguages(),
            c.isNoPetCaregiver(), c.getStatus(), c.getCreatedAt());
    }
}
```

- [ ] **Step 5: Create ClientService**

`backend/src/main/java/com/hcare/api/v1/clients/ClientService.java`:

```java
package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.domain.Client;
import com.hcare.domain.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients(UUID agencyId) {
        return clientRepository.findByAgencyId(agencyId).stream()
            .map(ClientResponse::from)
            .toList();
    }

    @Transactional
    public ClientResponse createClient(UUID agencyId, CreateClientRequest req) {
        Client client = new Client(agencyId, req.firstName(), req.lastName(), req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID agencyId, UUID clientId) {
        return ClientResponse.from(requireClient(agencyId, clientId));
    }

    @Transactional
    public ClientResponse updateClient(UUID agencyId, UUID clientId, UpdateClientRequest req) {
        Client client = requireClient(agencyId, clientId);
        if (req.firstName() != null) client.setFirstName(req.firstName());
        if (req.lastName() != null) client.setLastName(req.lastName());
        if (req.dateOfBirth() != null) client.setDateOfBirth(req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        if (req.status() != null) client.setStatus(req.status());
        return ClientResponse.from(clientRepository.save(client));
    }

    // --- helpers (package-private for subclasses/tests in same package) ---

    Client requireClient(UUID agencyId, UUID clientId) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        if (!client.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }
        return client;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 8, Failures: 0`.

- [ ] **Step 7: Create ClientController**

`backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`:

```java
package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ClientResponse>> listClients(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(clientService.listClients(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> createClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateClientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createClient(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> getClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClient(principal.getAgencyId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<ClientResponse> updateClient(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(principal.getAgencyId(), id, request));
    }
}
```

- [ ] **Step 8: Compile check**

```bash
cd backend && mvn compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/Client.java \
  src/main/java/com/hcare/api/v1/clients/ \
  src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java
git commit -m "feat: add Client CRUD REST API with patch-semantics update"
```

---

## Task 2: Client Diagnoses & Medications

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/ClientMedication.java` (add setters)
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AddDiagnosisRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/DiagnosisResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AddMedicationRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateMedicationRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/MedicationResponse.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java` (add sub-resource methods)
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java` (add endpoints)
- Modify: `backend/src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java` (add tests)

- [ ] **Step 1: Add tests for diagnoses and medications**

Append to `ClientServiceTest` (add new fields and imports at top, then add test methods):

New imports to add at the top of `ClientServiceTest.java`:
```java
import com.hcare.api.v1.clients.dto.AddDiagnosisRequest;
import com.hcare.api.v1.clients.dto.AddMedicationRequest;
import com.hcare.api.v1.clients.dto.DiagnosisResponse;
import com.hcare.api.v1.clients.dto.MedicationResponse;
import com.hcare.api.v1.clients.dto.UpdateMedicationRequest;
import com.hcare.domain.ClientDiagnosis;
import com.hcare.domain.ClientDiagnosisRepository;
import com.hcare.domain.ClientMedication;
import com.hcare.domain.ClientMedicationRepository;
```

New mock fields to add inside the test class:
```java
@Mock ClientDiagnosisRepository diagnosisRepository;
@Mock ClientMedicationRepository medicationRepository;
```

Update `setUp()`:
```java
@BeforeEach
void setUp() {
    service = new ClientService(clientRepository, diagnosisRepository, medicationRepository);
}
```

New test methods to add:
```java
    // --- diagnoses ---

    @Test
    void addDiagnosis_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis saved = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.save(any())).thenReturn(saved);

        DiagnosisResponse result = service.addDiagnosis(agencyId, clientId,
            new AddDiagnosisRequest("E11.9", "Type 2 Diabetes", null));

        assertThat(result.icd10Code()).isEqualTo("E11.9");
        verify(diagnosisRepository).save(any(ClientDiagnosis.class));
    }

    @Test
    void listDiagnoses_returns_all_for_client() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.findByClientId(clientId)).thenReturn(List.of(diag));

        List<DiagnosisResponse> result = service.listDiagnoses(agencyId, clientId);

        assertThat(result).hasSize(1);
    }

    @Test
    void deleteDiagnosis_removes_when_belongs_to_client() {
        UUID diagId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, "E11.9");
        when(diagnosisRepository.findById(diagId)).thenReturn(Optional.of(diag));

        service.deleteDiagnosis(agencyId, clientId, diagId);

        verify(diagnosisRepository).delete(diag);
    }

    @Test
    void deleteDiagnosis_throws_404_when_belongs_to_other_client() {
        UUID diagId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientDiagnosis diag = new ClientDiagnosis(UUID.randomUUID(), agencyId, "E11.9");
        when(diagnosisRepository.findById(diagId)).thenReturn(Optional.of(diag));

        assertThatThrownBy(() -> service.deleteDiagnosis(agencyId, clientId, diagId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- medications ---

    @Test
    void addMedication_saves_and_returns_response() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication saved = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.save(any())).thenReturn(saved);

        MedicationResponse result = service.addMedication(agencyId, clientId,
            new AddMedicationRequest("Metformin", "500mg", "oral", "twice daily", "Dr. Brown"));

        assertThat(result.name()).isEqualTo("Metformin");
        verify(medicationRepository).save(any(ClientMedication.class));
    }

    @Test
    void updateMedication_applies_non_null_fields() {
        UUID medId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication med = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(med));
        when(medicationRepository.save(med)).thenReturn(med);

        service.updateMedication(agencyId, clientId, medId,
            new UpdateMedicationRequest(null, "1000mg", null, null, null));

        assertThat(med.getDosage()).isEqualTo("1000mg");
        assertThat(med.getName()).isEqualTo("Metformin"); // unchanged
    }

    @Test
    void deleteMedication_removes_when_belongs_to_client() {
        UUID medId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        ClientMedication med = new ClientMedication(clientId, agencyId, "Metformin");
        when(medicationRepository.findById(medId)).thenReturn(Optional.of(med));

        service.deleteMedication(agencyId, clientId, medId);

        verify(medicationRepository).delete(med);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE` — DTOs and new service constructor do not exist yet.

- [ ] **Step 3: Add setters to ClientMedication entity**

In `backend/src/main/java/com/hcare/domain/ClientMedication.java`, add after the existing getters:

```java
    public void setName(String name) { this.name = name; }
    public void setDosage(String dosage) { this.dosage = dosage; }
    public void setRoute(String route) { this.route = route; }
    public void setSchedule(String schedule) { this.schedule = schedule; }
    public void setPrescriber(String prescriber) { this.prescriber = prescriber; }
```

- [ ] **Step 4: Create ClientDiagnosis and verify its constructor**

Check that `ClientDiagnosis` has a constructor `ClientDiagnosis(UUID clientId, UUID agencyId, String icd10Code)` by reading `backend/src/main/java/com/hcare/domain/ClientDiagnosis.java`. If the constructor signature differs, adjust the test and service accordingly.

- [ ] **Step 5: Create diagnosis and medication DTOs**

`backend/src/main/java/com/hcare/api/v1/clients/dto/AddDiagnosisRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record AddDiagnosisRequest(
    @NotBlank String icd10Code,
    String description,
    LocalDate onsetDate
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/DiagnosisResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientDiagnosis;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DiagnosisResponse(
    UUID id,
    UUID clientId,
    String icd10Code,
    String description,
    LocalDate onsetDate,
    LocalDateTime createdAt
) {
    public static DiagnosisResponse from(ClientDiagnosis d) {
        return new DiagnosisResponse(
            d.getId(), d.getClientId(), d.getIcd10Code(),
            d.getDescription(), d.getOnsetDate(), d.getCreatedAt());
    }
}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AddMedicationRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;

public record AddMedicationRequest(
    @NotBlank String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateMedicationRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

public record UpdateMedicationRequest(
    String name,
    String dosage,
    String route,
    String schedule,
    String prescriber
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/MedicationResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.ClientMedication;
import java.time.LocalDateTime;
import java.util.UUID;

public record MedicationResponse(
    UUID id,
    UUID clientId,
    String name,
    String dosage,
    String route,
    String schedule,
    String prescriber,
    LocalDateTime createdAt
) {
    public static MedicationResponse from(ClientMedication m) {
        return new MedicationResponse(
            m.getId(), m.getClientId(), m.getName(), m.getDosage(),
            m.getRoute(), m.getSchedule(), m.getPrescriber(), m.getCreatedAt());
    }
}
```

- [ ] **Step 6: Extend ClientService with diagnosis/medication methods**

Update `ClientService.java` — change constructor and add methods. The full updated file:

```java
package com.hcare.api.v1.clients;

import com.hcare.api.v1.clients.dto.*;
import com.hcare.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ClientDiagnosisRepository diagnosisRepository;
    private final ClientMedicationRepository medicationRepository;

    public ClientService(ClientRepository clientRepository,
                         ClientDiagnosisRepository diagnosisRepository,
                         ClientMedicationRepository medicationRepository) {
        this.clientRepository = clientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.medicationRepository = medicationRepository;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> listClients(UUID agencyId) {
        return clientRepository.findByAgencyId(agencyId).stream()
            .map(ClientResponse::from)
            .toList();
    }

    @Transactional
    public ClientResponse createClient(UUID agencyId, CreateClientRequest req) {
        Client client = new Client(agencyId, req.firstName(), req.lastName(), req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        return ClientResponse.from(clientRepository.save(client));
    }

    @Transactional(readOnly = true)
    public ClientResponse getClient(UUID agencyId, UUID clientId) {
        return ClientResponse.from(requireClient(agencyId, clientId));
    }

    @Transactional
    public ClientResponse updateClient(UUID agencyId, UUID clientId, UpdateClientRequest req) {
        Client client = requireClient(agencyId, clientId);
        if (req.firstName() != null) client.setFirstName(req.firstName());
        if (req.lastName() != null) client.setLastName(req.lastName());
        if (req.dateOfBirth() != null) client.setDateOfBirth(req.dateOfBirth());
        if (req.address() != null) client.setAddress(req.address());
        if (req.phone() != null) client.setPhone(req.phone());
        if (req.medicaidId() != null) client.setMedicaidId(req.medicaidId());
        if (req.serviceState() != null) client.setServiceState(req.serviceState());
        if (req.preferredCaregiverGender() != null) client.setPreferredCaregiverGender(req.preferredCaregiverGender());
        if (req.preferredLanguages() != null) client.setPreferredLanguages(req.preferredLanguages());
        if (req.noPetCaregiver() != null) client.setNoPetCaregiver(req.noPetCaregiver());
        if (req.status() != null) client.setStatus(req.status());
        return ClientResponse.from(clientRepository.save(client));
    }

    // --- diagnoses ---

    @Transactional
    public DiagnosisResponse addDiagnosis(UUID agencyId, UUID clientId, AddDiagnosisRequest req) {
        requireClient(agencyId, clientId);
        ClientDiagnosis diag = new ClientDiagnosis(clientId, agencyId, req.icd10Code());
        if (req.description() != null) diag.setDescription(req.description());
        if (req.onsetDate() != null) diag.setOnsetDate(req.onsetDate());
        return DiagnosisResponse.from(diagnosisRepository.save(diag));
    }

    @Transactional(readOnly = true)
    public List<DiagnosisResponse> listDiagnoses(UUID agencyId, UUID clientId) {
        requireClient(agencyId, clientId);
        return diagnosisRepository.findByClientId(clientId).stream()
            .map(DiagnosisResponse::from)
            .toList();
    }

    @Transactional
    public void deleteDiagnosis(UUID agencyId, UUID clientId, UUID diagnosisId) {
        requireClient(agencyId, clientId);
        ClientDiagnosis diag = diagnosisRepository.findById(diagnosisId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found"));
        if (!diag.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Diagnosis not found");
        }
        diagnosisRepository.delete(diag);
    }

    // --- medications ---

    @Transactional
    public MedicationResponse addMedication(UUID agencyId, UUID clientId, AddMedicationRequest req) {
        requireClient(agencyId, clientId);
        ClientMedication med = new ClientMedication(clientId, agencyId, req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional(readOnly = true)
    public List<MedicationResponse> listMedications(UUID agencyId, UUID clientId) {
        requireClient(agencyId, clientId);
        return medicationRepository.findByClientId(clientId).stream()
            .map(MedicationResponse::from)
            .toList();
    }

    @Transactional
    public MedicationResponse updateMedication(UUID agencyId, UUID clientId, UUID medicationId,
                                               UpdateMedicationRequest req) {
        requireClient(agencyId, clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        if (req.name() != null) med.setName(req.name());
        if (req.dosage() != null) med.setDosage(req.dosage());
        if (req.route() != null) med.setRoute(req.route());
        if (req.schedule() != null) med.setSchedule(req.schedule());
        if (req.prescriber() != null) med.setPrescriber(req.prescriber());
        return MedicationResponse.from(medicationRepository.save(med));
    }

    @Transactional
    public void deleteMedication(UUID agencyId, UUID clientId, UUID medicationId) {
        requireClient(agencyId, clientId);
        ClientMedication med = medicationRepository.findById(medicationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found"));
        if (!med.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Medication not found");
        }
        medicationRepository.delete(med);
    }

    // --- helpers ---

    Client requireClient(UUID agencyId, UUID clientId) {
        Client client = clientRepository.findById(clientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        if (!client.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found");
        }
        return client;
    }
}
```

Also add the `setDescription` and `setOnsetDate` methods to `ClientDiagnosis` entity if not present. Read `backend/src/main/java/com/hcare/domain/ClientDiagnosis.java` and add:
```java
    public void setDescription(String description) { this.description = description; }
    public void setOnsetDate(LocalDate onsetDate) { this.onsetDate = onsetDate; }
```

- [ ] **Step 7: Add diagnosis/medication endpoints to ClientController**

Add these methods to `ClientController.java` (import `DiagnosisResponse`, `MedicationResponse`, and the request types):

```java
    // --- Diagnoses ---

    @GetMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<DiagnosisResponse>> listDiagnoses(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listDiagnoses(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<DiagnosisResponse> addDiagnosis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddDiagnosisRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addDiagnosis(principal.getAgencyId(), id, request));
    }

    @DeleteMapping("/{id}/diagnoses/{diagnosisId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteDiagnosis(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID diagnosisId) {
        clientService.deleteDiagnosis(principal.getAgencyId(), id, diagnosisId);
        return ResponseEntity.noContent().build();
    }

    // --- Medications ---

    @GetMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<MedicationResponse>> listMedications(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listMedications(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/medications")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> addMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddMedicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addMedication(principal.getAgencyId(), id, request));
    }

    @PatchMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<MedicationResponse> updateMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID medicationId,
            @Valid @RequestBody UpdateMedicationRequest request) {
        return ResponseEntity.ok(
            clientService.updateMedication(principal.getAgencyId(), id, medicationId, request));
    }

    @DeleteMapping("/{id}/medications/{medicationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteMedication(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID medicationId) {
        clientService.deleteMedication(principal.getAgencyId(), id, medicationId);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 8: Run all ClientServiceTest tests**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 16, Failures: 0`.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/Client{Diagnosis,Medication}.java \
  src/main/java/com/hcare/api/v1/clients/ \
  src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java
git commit -m "feat: add client diagnoses and medications sub-resources"
```

---

## Task 3: Care Plans, ADL Tasks & Goals

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/AdlTask.java` (add setters)
- Modify: `backend/src/main/java/com/hcare/domain/Goal.java` (add setters)
- Modify: `backend/src/main/java/com/hcare/domain/CarePlanRepository.java` (add `@Lock` on `findByClientIdAndStatus` for concurrency safety; add `findMaxPlanVersionByClientId` query)
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/CreateCarePlanRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/CarePlanResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AddAdlTaskRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AdlTaskResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AddGoalRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateGoalRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/GoalResponse.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java`

- [ ] **Step 1: Add tests for care plans, ADL tasks, goals**

Add to the imports section of `ClientServiceTest.java`:
```java
import com.hcare.api.v1.clients.dto.AddAdlTaskRequest;
import com.hcare.api.v1.clients.dto.AddGoalRequest;
import com.hcare.api.v1.clients.dto.AdlTaskResponse;
import com.hcare.api.v1.clients.dto.CarePlanResponse;
import com.hcare.api.v1.clients.dto.CreateCarePlanRequest;
import com.hcare.api.v1.clients.dto.GoalResponse;
import com.hcare.api.v1.clients.dto.UpdateGoalRequest;
import com.hcare.domain.AdlTask;
import com.hcare.domain.AdlTaskRepository;
import com.hcare.domain.AssistanceLevel;
import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanRepository;
import com.hcare.domain.CarePlanStatus;
import com.hcare.domain.Goal;
import com.hcare.domain.GoalRepository;
import com.hcare.domain.GoalStatus;
```

Add new mocks:
```java
@Mock CarePlanRepository carePlanRepository;
@Mock AdlTaskRepository adlTaskRepository;
@Mock GoalRepository goalRepository;
```

Update `setUp()` to include new repos:
```java
@BeforeEach
void setUp() {
    service = new ClientService(clientRepository, diagnosisRepository, medicationRepository,
        carePlanRepository, adlTaskRepository, goalRepository);
}
```

Add test methods:
```java
    // --- care plans ---

    @Test
    void createCarePlan_creates_draft_with_next_version_number() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan existingPlan = new CarePlan(clientId, agencyId, 1);
        when(carePlanRepository.findByClientIdOrderByPlanVersionAsc(clientId))
            .thenReturn(List.of(existingPlan));
        CarePlan saved = new CarePlan(clientId, agencyId, 2);
        when(carePlanRepository.save(any())).thenReturn(saved);

        CarePlanResponse result = service.createCarePlan(agencyId, clientId,
            new CreateCarePlanRequest(null));

        assertThat(result.planVersion()).isEqualTo(2);
        assertThat(result.status()).isEqualTo(CarePlanStatus.DRAFT);
    }

    @Test
    void activateCarePlan_supersedes_current_active_and_activates_new_one() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan currentActive = new CarePlan(clientId, agencyId, 1);
        currentActive.activate();
        when(carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE))
            .thenReturn(Optional.of(currentActive));
        CarePlan newPlan = new CarePlan(clientId, agencyId, 2);
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(newPlan));
        when(carePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.activateCarePlan(agencyId, clientId, planId);

        assertThat(currentActive.getStatus()).isEqualTo(CarePlanStatus.SUPERSEDED);
        assertThat(newPlan.getStatus()).isEqualTo(CarePlanStatus.ACTIVE);
    }

    @Test
    void activateCarePlan_throws_409_when_plan_already_active() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        plan.activate();
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE))
            .thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.activateCarePlan(agencyId, clientId, planId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void activateCarePlan_throws_404_when_plan_belongs_to_other_client() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        CarePlan plan = new CarePlan(UUID.randomUUID(), agencyId, 1); // different clientId
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateCarePlan(agencyId, clientId, planId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- ADL tasks ---

    @Test
    void addAdlTask_saves_task_on_care_plan() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        AdlTask saved = new AdlTask(planId, agencyId, "Bathing", AssistanceLevel.MODERATE_ASSIST);
        when(adlTaskRepository.save(any())).thenReturn(saved);

        AdlTaskResponse result = service.addAdlTask(agencyId, clientId, planId,
            new AddAdlTaskRequest("Bathing", AssistanceLevel.MODERATE_ASSIST, null, null, null));

        assertThat(result.name()).isEqualTo("Bathing");
        assertThat(result.assistanceLevel()).isEqualTo(AssistanceLevel.MODERATE_ASSIST);
        verify(adlTaskRepository).save(any(AdlTask.class));
    }

    @Test
    void deleteAdlTask_removes_task_when_belongs_to_plan() {
        UUID planId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        AdlTask task = new AdlTask(planId, agencyId, "Bathing", AssistanceLevel.MINIMAL_ASSIST);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(adlTaskRepository.findById(taskId)).thenReturn(Optional.of(task));

        service.deleteAdlTask(agencyId, clientId, planId, taskId);

        verify(adlTaskRepository).delete(task);
    }

    // --- Goals ---

    @Test
    void addGoal_saves_goal_on_care_plan() {
        UUID planId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        Goal saved = new Goal(planId, agencyId, "Improve mobility");
        when(goalRepository.save(any())).thenReturn(saved);

        GoalResponse result = service.addGoal(agencyId, clientId, planId,
            new AddGoalRequest("Improve mobility", null));

        assertThat(result.description()).isEqualTo("Improve mobility");
        assertThat(result.status()).isEqualTo(GoalStatus.ACTIVE);
    }

    @Test
    void updateGoal_updates_non_null_fields() {
        UUID planId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        Client client = makeClient();
        CarePlan plan = new CarePlan(clientId, agencyId, 1);
        Goal goal = new Goal(planId, agencyId, "Improve mobility");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(carePlanRepository.findById(planId)).thenReturn(Optional.of(plan));
        when(goalRepository.findById(goalId)).thenReturn(Optional.of(goal));
        when(goalRepository.save(goal)).thenReturn(goal);

        service.updateGoal(agencyId, clientId, planId, goalId,
            new UpdateGoalRequest(null, null, GoalStatus.ACHIEVED));

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACHIEVED);
        assertThat(goal.getDescription()).isEqualTo("Improve mobility"); // unchanged
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE`.

- [ ] **Step 3: Add setters to AdlTask and Goal entities**

In `backend/src/main/java/com/hcare/domain/AdlTask.java`, add after the existing getters:
```java
    public void setName(String name) { this.name = name; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
    public void setAssistanceLevel(AssistanceLevel assistanceLevel) { this.assistanceLevel = assistanceLevel; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
```

In `backend/src/main/java/com/hcare/domain/Goal.java`, add after the existing getters:
```java
    public void setDescription(String description) { this.description = description; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public void setStatus(GoalStatus status) { this.status = status; }
```

- [ ] **Step 4: Create care plan / ADL task / goal DTOs**

`backend/src/main/java/com/hcare/api/v1/clients/dto/CreateCarePlanRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import java.util.UUID;

public record CreateCarePlanRequest(UUID reviewedByClinicianId) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/CarePlanResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record CarePlanResponse(
    UUID id,
    UUID clientId,
    UUID agencyId,
    int planVersion,
    CarePlanStatus status,
    UUID reviewedByClinicianId,
    LocalDateTime reviewedAt,
    LocalDateTime activatedAt,
    LocalDateTime createdAt
) {
    public static CarePlanResponse from(CarePlan p) {
        return new CarePlanResponse(
            p.getId(), p.getClientId(), p.getAgencyId(), p.getPlanVersion(),
            p.getStatus(), p.getReviewedByClinicianId(), p.getReviewedAt(),
            p.getActivatedAt(), p.getCreatedAt());
    }
}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AddAdlTaskRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.AssistanceLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddAdlTaskRequest(
    @NotBlank String name,
    @NotNull AssistanceLevel assistanceLevel,
    String instructions,
    String frequency,
    Integer sortOrder
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AdlTaskResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.AdlTask;
import com.hcare.domain.AssistanceLevel;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdlTaskResponse(
    UUID id,
    UUID carePlanId,
    String name,
    AssistanceLevel assistanceLevel,
    String instructions,
    String frequency,
    int sortOrder,
    LocalDateTime createdAt
) {
    public static AdlTaskResponse from(AdlTask t) {
        return new AdlTaskResponse(
            t.getId(), t.getCarePlanId(), t.getName(), t.getAssistanceLevel(),
            t.getInstructions(), t.getFrequency(), t.getSortOrder(), t.getCreatedAt());
    }
}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AddGoalRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record AddGoalRequest(
    @NotBlank String description,
    LocalDate targetDate
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/UpdateGoalRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.GoalStatus;

import java.time.LocalDate;

public record UpdateGoalRequest(
    String description,
    LocalDate targetDate,
    GoalStatus status
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/GoalResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Goal;
import com.hcare.domain.GoalStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record GoalResponse(
    UUID id,
    UUID carePlanId,
    String description,
    LocalDate targetDate,
    GoalStatus status,
    LocalDateTime createdAt
) {
    public static GoalResponse from(Goal g) {
        return new GoalResponse(
            g.getId(), g.getCarePlanId(), g.getDescription(),
            g.getTargetDate(), g.getStatus(), g.getCreatedAt());
    }
}
```

- [ ] **Step 5: Extend ClientService with care plan / ADL task / goal methods**

Add these methods to `ClientService.java` (and add the three new repository constructor parameters and fields):

New constructor parameters: add `CarePlanRepository carePlanRepository, AdlTaskRepository adlTaskRepository, GoalRepository goalRepository`.

New fields:
```java
private final CarePlanRepository carePlanRepository;
private final AdlTaskRepository adlTaskRepository;
private final GoalRepository goalRepository;
```

New service methods:
```java
    // --- care plans ---

    @Transactional(readOnly = true)
    public List<CarePlanResponse> listCarePlans(UUID agencyId, UUID clientId) {
        requireClient(agencyId, clientId);
        return carePlanRepository.findByClientIdOrderByPlanVersionAsc(clientId).stream()
            .map(CarePlanResponse::from)
            .toList();
    }

    @Transactional
    public CarePlanResponse createCarePlan(UUID agencyId, UUID clientId, CreateCarePlanRequest req) {
        requireClient(agencyId, clientId);
        int nextVersion = carePlanRepository.findMaxPlanVersionByClientId(clientId) + 1;
        CarePlan plan = new CarePlan(clientId, agencyId, nextVersion);
        if (req.reviewedByClinicianId() != null) {
            plan.review(req.reviewedByClinicianId());
        }
        return CarePlanResponse.from(carePlanRepository.save(plan));
    }

    @Transactional
    public CarePlanResponse activateCarePlan(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireClient(agencyId, clientId);
        CarePlan plan = carePlanRepository.findById(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found"));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found");
        }
        if (plan.getStatus() == CarePlanStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Care plan is already ACTIVE");
        }
        carePlanRepository.findByClientIdAndStatus(clientId, CarePlanStatus.ACTIVE)
            .ifPresent(current -> {
                current.supersede();
                carePlanRepository.save(current);
            });
        plan.activate();
        return CarePlanResponse.from(carePlanRepository.save(plan));
    }

    // --- ADL tasks ---

    @Transactional(readOnly = true)
    public List<AdlTaskResponse> listAdlTasks(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireCarePlan(agencyId, clientId, carePlanId);
        return adlTaskRepository.findByCarePlanIdOrderBySortOrder(carePlanId).stream()
            .map(AdlTaskResponse::from)
            .toList();
    }

    @Transactional
    public AdlTaskResponse addAdlTask(UUID agencyId, UUID clientId, UUID carePlanId,
                                      AddAdlTaskRequest req) {
        requireCarePlan(agencyId, clientId, carePlanId);
        AdlTask task = new AdlTask(carePlanId, agencyId, req.name(), req.assistanceLevel());
        if (req.instructions() != null) task.setInstructions(req.instructions());
        if (req.frequency() != null) task.setFrequency(req.frequency());
        if (req.sortOrder() != null) task.setSortOrder(req.sortOrder());
        return AdlTaskResponse.from(adlTaskRepository.save(task));
    }

    @Transactional
    public void deleteAdlTask(UUID agencyId, UUID clientId, UUID carePlanId, UUID taskId) {
        requireCarePlan(agencyId, clientId, carePlanId);
        AdlTask task = adlTaskRepository.findById(taskId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ADL task not found"));
        if (!task.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ADL task not found");
        }
        adlTaskRepository.delete(task);
    }

    // --- goals ---

    @Transactional(readOnly = true)
    public List<GoalResponse> listGoals(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireCarePlan(agencyId, clientId, carePlanId);
        return goalRepository.findByCarePlanId(carePlanId).stream()
            .map(GoalResponse::from)
            .toList();
    }

    @Transactional
    public GoalResponse addGoal(UUID agencyId, UUID clientId, UUID carePlanId, AddGoalRequest req) {
        requireCarePlan(agencyId, clientId, carePlanId);
        Goal goal = new Goal(carePlanId, agencyId, req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse updateGoal(UUID agencyId, UUID clientId, UUID carePlanId, UUID goalId,
                                   UpdateGoalRequest req) {
        requireCarePlan(agencyId, clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found");
        }
        if (req.description() != null) goal.setDescription(req.description());
        if (req.targetDate() != null) goal.setTargetDate(req.targetDate());
        if (req.status() != null) goal.setStatus(req.status());
        return GoalResponse.from(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(UUID agencyId, UUID clientId, UUID carePlanId, UUID goalId) {
        requireCarePlan(agencyId, clientId, carePlanId);
        Goal goal = goalRepository.findById(goalId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
        if (!goal.getCarePlanId().equals(carePlanId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found");
        }
        goalRepository.delete(goal);
    }

    // add to helpers section:
    private CarePlan requireCarePlan(UUID agencyId, UUID clientId, UUID carePlanId) {
        requireClient(agencyId, clientId);
        CarePlan plan = carePlanRepository.findById(carePlanId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found"));
        if (!plan.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Care plan not found");
        }
        return plan;
    }
```

Also add missing imports to `ClientService.java`:
```java
import com.hcare.api.v1.clients.dto.AddAdlTaskRequest;
import com.hcare.api.v1.clients.dto.AddGoalRequest;
import com.hcare.api.v1.clients.dto.AdlTaskResponse;
import com.hcare.api.v1.clients.dto.CarePlanResponse;
import com.hcare.api.v1.clients.dto.CreateCarePlanRequest;
import com.hcare.api.v1.clients.dto.GoalResponse;
import com.hcare.api.v1.clients.dto.UpdateGoalRequest;
import com.hcare.domain.AdlTask;
import com.hcare.domain.AdlTaskRepository;
import com.hcare.domain.CarePlan;
import com.hcare.domain.CarePlanRepository;
import com.hcare.domain.CarePlanStatus;
import com.hcare.domain.Goal;
import com.hcare.domain.GoalRepository;
```

- [ ] **Step 6: Modify CarePlanRepository — add activation lock and max-version query**

Replace `backend/src/main/java/com/hcare/domain/CarePlanRepository.java` with:

```java
package com.hcare.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
    List<CarePlan> findByClientIdOrderByPlanVersionAsc(UUID clientId);

    // PESSIMISTIC_WRITE serializes concurrent activateCarePlan calls for the same client,
    // preventing two simultaneous requests from each finding no ACTIVE plan and both activating.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CarePlan> findByClientIdAndStatus(UUID clientId, CarePlanStatus status);

    @Query("SELECT COALESCE(MAX(p.planVersion), 0) FROM CarePlan p WHERE p.clientId = :clientId")
    int findMaxPlanVersionByClientId(UUID clientId);
}
```

- [ ] **Step 7: Add care plan / ADL task / goal endpoints to ClientController**

Add these methods to `ClientController.java`:

```java
    // --- Care Plans ---

    @GetMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CarePlanResponse>> listCarePlans(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listCarePlans(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/care-plans")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> createCarePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateCarePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createCarePlan(principal.getAgencyId(), id, request));
    }

    @PostMapping("/{id}/care-plans/{planId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CarePlanResponse> activateCarePlan(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.activateCarePlan(principal.getAgencyId(), id, planId));
    }

    // --- ADL Tasks ---

    @GetMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AdlTaskResponse>> listAdlTasks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.listAdlTasks(principal.getAgencyId(), id, planId));
    }

    @PostMapping("/{id}/care-plans/{planId}/adl-tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AdlTaskResponse> addAdlTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddAdlTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addAdlTask(principal.getAgencyId(), id, planId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/adl-tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteAdlTask(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID taskId) {
        clientService.deleteAdlTask(principal.getAgencyId(), id, planId, taskId);
        return ResponseEntity.noContent().build();
    }

    // --- Goals ---

    @GetMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<GoalResponse>> listGoals(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId) {
        return ResponseEntity.ok(clientService.listGoals(principal.getAgencyId(), id, planId));
    }

    @PostMapping("/{id}/care-plans/{planId}/goals")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> addGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @Valid @RequestBody AddGoalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addGoal(principal.getAgencyId(), id, planId, request));
    }

    @PatchMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<GoalResponse> updateGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId,
            @Valid @RequestBody UpdateGoalRequest request) {
        return ResponseEntity.ok(
            clientService.updateGoal(principal.getAgencyId(), id, planId, goalId, request));
    }

    @DeleteMapping("/{id}/care-plans/{planId}/goals/{goalId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteGoal(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID planId,
            @PathVariable UUID goalId) {
        clientService.deleteGoal(principal.getAgencyId(), id, planId, goalId);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 8: Run all ClientServiceTest tests**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 26, Failures: 0`.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/AdlTask.java \
  src/main/java/com/hcare/domain/Goal.java \
  src/main/java/com/hcare/domain/CarePlanRepository.java \
  src/main/java/com/hcare/api/v1/clients/ \
  src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java
git commit -m "feat: add care plans, ADL tasks, and goals sub-resources to client API"
```

---

## Task 4: Client Authorizations & Family Portal Users

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/FamilyPortalUser.java` (add setName)
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/CreateAuthorizationRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AuthorizationResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/AddFamilyPortalUserRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/clients/dto/FamilyPortalUserResponse.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientService.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/clients/ClientController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java`

- [ ] **Step 0: Verify ServiceType domain model (C4)**

Read `backend/src/main/java/com/hcare/domain/ServiceType.java`.

- **If `agencyId` field exists on `ServiceType`:** proceed with the `createAuthorization` service code as written in Step 5 — the `serviceType.getAgencyId().equals(agencyId)` ownership check is correct.
- **If `ServiceType` is global (no `agencyId` field, similar to `EvvStateConfig`):** remove the `serviceType.getAgencyId()` check from `createAuthorization` in Step 5; validate only payer ownership. Also remove or adjust the `ServiceType serviceType = new ServiceType(agencyId, ...)` constructor calls in the test stubs (Step 1) to match the actual constructor signature.

Do not proceed to Step 1 until this is confirmed.

- [ ] **Step 1: Add tests for authorizations and family portal users**

Add to imports in `ClientServiceTest.java`:
```java
import com.hcare.api.v1.clients.dto.AddFamilyPortalUserRequest;
import com.hcare.api.v1.clients.dto.AuthorizationResponse;
import com.hcare.api.v1.clients.dto.CreateAuthorizationRequest;
import com.hcare.api.v1.clients.dto.FamilyPortalUserResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.FamilyPortalUser;
import com.hcare.domain.FamilyPortalUserRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.PayerType;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
import com.hcare.domain.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
```

Add new mocks:
```java
@Mock AuthorizationRepository authorizationRepository;
@Mock PayerRepository payerRepository;
@Mock ServiceTypeRepository serviceTypeRepository;
@Mock FamilyPortalUserRepository familyPortalUserRepository;
```

Update `setUp()` to include all repos (full updated call):
```java
@BeforeEach
void setUp() {
    service = new ClientService(clientRepository, diagnosisRepository, medicationRepository,
        carePlanRepository, adlTaskRepository, goalRepository,
        authorizationRepository, payerRepository, serviceTypeRepository,
        familyPortalUserRepository);
}
```

Add test methods:
```java
    // --- authorizations ---

    @Test
    void createAuthorization_validates_payer_belongs_to_agency_and_saves() {
        UUID payerId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        Client client = makeClient();
        Payer payer = new Payer(agencyId, "Medicaid", PayerType.MEDICAID, "CA");
        ServiceType serviceType = new ServiceType(agencyId, "Personal Care", "PC", false, "[]");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(payerRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.of(serviceType));
        Authorization saved = new Authorization(clientId, payerId, serviceTypeId, agencyId,
            "AUTH-001", new BigDecimal("40"), UnitType.HOURS,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(authorizationRepository.save(any())).thenReturn(saved);

        AuthorizationResponse result = service.createAuthorization(agencyId, clientId,
            new CreateAuthorizationRequest(payerId, serviceTypeId, "AUTH-001",
                UnitType.HOURS, new BigDecimal("40"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));

        assertThat(result.authNumber()).isEqualTo("AUTH-001");
        verify(authorizationRepository).save(any(Authorization.class));
    }

    @Test
    void createAuthorization_throws_422_when_payer_belongs_to_other_agency() {
        UUID payerId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        Client client = makeClient();
        Payer payer = new Payer(UUID.randomUUID(), "Medicaid", PayerType.MEDICAID, "CA");
        ServiceType serviceType = new ServiceType(agencyId, "Personal Care", "PC", false, "[]");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(payerRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.of(serviceType));

        assertThatThrownBy(() -> service.createAuthorization(agencyId, clientId,
            new CreateAuthorizationRequest(payerId, serviceTypeId, "AUTH-001",
                UnitType.HOURS, new BigDecimal("40"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
    }

    @Test
    void createAuthorization_throws_400_when_end_date_not_after_start() {
        UUID payerId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        Client client = makeClient();
        Payer payer = new Payer(agencyId, "Medicaid", PayerType.MEDICAID, "CA");
        ServiceType serviceType = new ServiceType(agencyId, "Personal Care", "PC", false, "[]");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(payerRepository.findById(payerId)).thenReturn(Optional.of(payer));
        when(serviceTypeRepository.findById(serviceTypeId)).thenReturn(Optional.of(serviceType));

        assertThatThrownBy(() -> service.createAuthorization(agencyId, clientId,
            new CreateAuthorizationRequest(payerId, serviceTypeId, "AUTH-001",
                UnitType.HOURS, new BigDecimal("40"),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }

    @Test
    void listAuthorizations_returns_all_for_client() {
        Client client = makeClient();
        UUID payerId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        Authorization auth = new Authorization(clientId, payerId, serviceTypeId, agencyId,
            "AUTH-001", new BigDecimal("40"), UnitType.HOURS,
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        when(authorizationRepository.findByClientId(clientId)).thenReturn(List.of(auth));

        List<AuthorizationResponse> result = service.listAuthorizations(agencyId, clientId);

        assertThat(result).hasSize(1);
    }

    // --- family portal users ---

    @Test
    void addFamilyPortalUser_saves_user() {
        Client client = makeClient();
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(familyPortalUserRepository.findByAgencyIdAndEmail(agencyId, "family@example.com"))
            .thenReturn(Optional.empty());
        FamilyPortalUser saved = new FamilyPortalUser(clientId, agencyId, "family@example.com");
        when(familyPortalUserRepository.save(any())).thenReturn(saved);

        FamilyPortalUserResponse result = service.addFamilyPortalUser(agencyId, clientId,
            new AddFamilyPortalUserRequest("family@example.com", "Jane Family"));

        assertThat(result.email()).isEqualTo("family@example.com");
        verify(familyPortalUserRepository).save(any(FamilyPortalUser.class));
    }

    @Test
    void addFamilyPortalUser_throws_409_when_email_already_exists_for_agency() {
        Client client = makeClient();
        FamilyPortalUser existing = new FamilyPortalUser(clientId, agencyId, "family@example.com");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(familyPortalUserRepository.findByAgencyIdAndEmail(agencyId, "family@example.com"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addFamilyPortalUser(agencyId, clientId,
            new AddFamilyPortalUserRequest("family@example.com", null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }

    @Test
    void removeFamilyPortalUser_deletes_when_belongs_to_client() {
        UUID fpuId = UUID.randomUUID();
        Client client = makeClient();
        FamilyPortalUser fpu = new FamilyPortalUser(clientId, agencyId, "family@example.com");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(familyPortalUserRepository.findById(fpuId)).thenReturn(Optional.of(fpu));

        service.removeFamilyPortalUser(agencyId, clientId, fpuId);

        verify(familyPortalUserRepository).delete(fpu);
    }

    @Test
    void removeFamilyPortalUser_throws_404_when_belongs_to_other_client() {
        UUID fpuId = UUID.randomUUID();
        Client client = makeClient();
        FamilyPortalUser fpu = new FamilyPortalUser(UUID.randomUUID(), agencyId, "family@example.com");
        when(clientRepository.findById(clientId)).thenReturn(Optional.of(client));
        when(familyPortalUserRepository.findById(fpuId)).thenReturn(Optional.of(fpu));

        assertThatThrownBy(() -> service.removeFamilyPortalUser(agencyId, clientId, fpuId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE`.

Note: The tests reference `new Payer(agencyId, "Medicaid", PayerType.MEDICAID, "CA")` and `new ServiceType(agencyId, "Personal Care")`. Verify these constructors match the actual domain entities (`domain/Payer.java` and `domain/ServiceType.java`); adjust if the signatures differ.

- [ ] **Step 3: Add setName to FamilyPortalUser**

In `backend/src/main/java/com/hcare/domain/FamilyPortalUser.java`, add after `recordLogin()`:
```java
    public void setName(String name) { this.name = name; }
```

- [ ] **Step 4: Create authorization and family portal user DTOs**

`backend/src/main/java/com/hcare/api/v1/clients/dto/CreateAuthorizationRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAuthorizationRequest(
    @NotNull UUID payerId,
    @NotNull UUID serviceTypeId,
    @NotBlank String authNumber,
    @NotNull UnitType unitType,
    @NotNull @DecimalMin("0.01") BigDecimal authorizedUnits,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AuthorizationResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.Authorization;
import com.hcare.domain.UnitType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AuthorizationResponse(
    UUID id,
    UUID clientId,
    UUID payerId,
    UUID serviceTypeId,
    UUID agencyId,
    String authNumber,
    UnitType unitType,
    BigDecimal authorizedUnits,
    BigDecimal usedUnits,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime createdAt
) {
    public static AuthorizationResponse from(Authorization a) {
        return new AuthorizationResponse(
            a.getId(), a.getClientId(), a.getPayerId(), a.getServiceTypeId(),
            a.getAgencyId(), a.getAuthNumber(), a.getUnitType(),
            a.getAuthorizedUnits(), a.getUsedUnits(),
            a.getStartDate(), a.getEndDate(), a.getCreatedAt());
    }
}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/AddFamilyPortalUserRequest.java`:
```java
package com.hcare.api.v1.clients.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddFamilyPortalUserRequest(
    @NotBlank @Email String email,
    String name
) {}
```

`backend/src/main/java/com/hcare/api/v1/clients/dto/FamilyPortalUserResponse.java`:
```java
package com.hcare.api.v1.clients.dto;

import com.hcare.domain.FamilyPortalUser;

import java.time.LocalDateTime;
import java.util.UUID;

public record FamilyPortalUserResponse(
    UUID id,
    UUID clientId,
    String email,
    String name,
    LocalDateTime lastLoginAt,
    LocalDateTime createdAt
) {
    public static FamilyPortalUserResponse from(FamilyPortalUser u) {
        return new FamilyPortalUserResponse(
            u.getId(), u.getClientId(), u.getEmail(),
            u.getName(), u.getLastLoginAt(), u.getCreatedAt());
    }
}
```

- [ ] **Step 5: Add authorization and family portal user methods to ClientService**

Add new constructor parameters, fields, and methods to `ClientService.java`:

New constructor parameters: `AuthorizationRepository authorizationRepository, PayerRepository payerRepository, ServiceTypeRepository serviceTypeRepository, FamilyPortalUserRepository familyPortalUserRepository`.

New service methods:
```java
    // --- authorizations ---

    @Transactional(readOnly = true)
    public List<AuthorizationResponse> listAuthorizations(UUID agencyId, UUID clientId) {
        requireClient(agencyId, clientId);
        return authorizationRepository.findByClientId(clientId).stream()
            .map(AuthorizationResponse::from)
            .toList();
    }

    @Transactional
    public AuthorizationResponse createAuthorization(UUID agencyId, UUID clientId,
                                                      CreateAuthorizationRequest req) {
        requireClient(agencyId, clientId);
        if (!req.endDate().isAfter(req.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "endDate must be after startDate");
        }
        Payer payer = payerRepository.findById(req.payerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer not found"));
        if (!payer.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Payer does not belong to this agency");
        }
        ServiceType serviceType = serviceTypeRepository.findById(req.serviceTypeId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ServiceType not found"));
        if (!serviceType.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "ServiceType does not belong to this agency");
        }
        Authorization auth = new Authorization(clientId, req.payerId(), req.serviceTypeId(),
            agencyId, req.authNumber(), req.authorizedUnits(), req.unitType(),
            req.startDate(), req.endDate());
        return AuthorizationResponse.from(authorizationRepository.save(auth));
    }

    // --- family portal users ---

    @Transactional(readOnly = true)
    public List<FamilyPortalUserResponse> listFamilyPortalUsers(UUID agencyId, UUID clientId) {
        requireClient(agencyId, clientId);
        return familyPortalUserRepository.findByClientId(clientId).stream()
            .map(FamilyPortalUserResponse::from)
            .toList();
    }

    @Transactional
    public FamilyPortalUserResponse addFamilyPortalUser(UUID agencyId, UUID clientId,
                                                         AddFamilyPortalUserRequest req) {
        requireClient(agencyId, clientId);
        familyPortalUserRepository.findByAgencyIdAndEmail(agencyId, req.email())
            .ifPresent(existing -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A family portal user with this email already exists in the agency");
            });
        FamilyPortalUser user = new FamilyPortalUser(clientId, agencyId, req.email());
        if (req.name() != null) user.setName(req.name());
        return FamilyPortalUserResponse.from(familyPortalUserRepository.save(user));
    }

    @Transactional
    public void removeFamilyPortalUser(UUID agencyId, UUID clientId, UUID userId) {
        requireClient(agencyId, clientId);
        FamilyPortalUser user = familyPortalUserRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Family portal user not found"));
        if (!user.getClientId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Family portal user not found");
        }
        familyPortalUserRepository.delete(user);
    }
```

Also add imports to `ClientService.java`:
```java
import com.hcare.api.v1.clients.dto.AddFamilyPortalUserRequest;
import com.hcare.api.v1.clients.dto.AuthorizationResponse;
import com.hcare.api.v1.clients.dto.CreateAuthorizationRequest;
import com.hcare.api.v1.clients.dto.FamilyPortalUserResponse;
import com.hcare.domain.Authorization;
import com.hcare.domain.AuthorizationRepository;
import com.hcare.domain.FamilyPortalUser;
import com.hcare.domain.FamilyPortalUserRepository;
import com.hcare.domain.Payer;
import com.hcare.domain.PayerRepository;
import com.hcare.domain.ServiceType;
import com.hcare.domain.ServiceTypeRepository;
```

- [ ] **Step 6: Add authorization and family portal user endpoints to ClientController**

Add to `ClientController.java`:
```java
    // --- Authorizations ---

    @GetMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AuthorizationResponse>> listAuthorizations(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listAuthorizations(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/authorizations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<AuthorizationResponse> createAuthorization(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAuthorizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.createAuthorization(principal.getAgencyId(), id, request));
    }

    // --- Family Portal Users ---

    @GetMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<FamilyPortalUserResponse>> listFamilyPortalUsers(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(clientService.listFamilyPortalUsers(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/family-portal-users")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<FamilyPortalUserResponse> addFamilyPortalUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddFamilyPortalUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(clientService.addFamilyPortalUser(principal.getAgencyId(), id, request));
    }

    @DeleteMapping("/{id}/family-portal-users/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> removeFamilyPortalUser(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        clientService.removeFamilyPortalUser(principal.getAgencyId(), id, userId);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 7: Run all ClientServiceTest tests**

```bash
cd backend && mvn test -Dtest=ClientServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 36, Failures: 0`.

- [ ] **Step 8: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/FamilyPortalUser.java \
  src/main/java/com/hcare/api/v1/clients/ \
  src/test/java/com/hcare/api/v1/clients/ClientServiceTest.java
git commit -m "feat: add client authorizations and family portal users sub-resources"
```

---

## Task 5: Caregiver Core CRUD

**Files:**
- Modify: `backend/src/main/java/com/hcare/domain/Caregiver.java` (add setters)
- Modify: `backend/src/main/java/com/hcare/domain/CaregiverRepository.java` (add `findByAgencyIdAndEmail` for update email-uniqueness check)
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/CreateCaregiverRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/UpdateCaregiverRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/CaregiverResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverService.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverController.java`
- Create: `backend/src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java`

- [ ] **Step 1: Write failing tests**

Create `backend/src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java`:

```java
package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import com.hcare.domain.CaregiverStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CaregiverServiceTest {

    @Mock CaregiverRepository caregiverRepository;

    CaregiverService service;

    UUID agencyId = UUID.randomUUID();
    UUID caregiverId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CaregiverService(caregiverRepository);
    }

    private Caregiver makeCaregiver() {
        return new Caregiver(agencyId, "John", "Doe", "john@example.com");
    }

    // --- listCaregivers ---

    @Test
    void listCaregivers_returns_all_for_agency() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findByAgencyId(agencyId)).thenReturn(List.of(caregiver));

        List<CaregiverResponse> result = service.listCaregivers(agencyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).firstName()).isEqualTo("John");
        verify(caregiverRepository).findByAgencyId(agencyId);
    }

    // --- createCaregiver ---

    @Test
    void createCaregiver_saves_and_returns_response() {
        CreateCaregiverRequest req = new CreateCaregiverRequest(
            "Jane", "Smith", "jane@example.com",
            "555-9999", "789 Elm St", LocalDate.of(2024, 1, 15),
            false, "[]", null);
        Caregiver saved = new Caregiver(agencyId, "Jane", "Smith", "jane@example.com");
        when(caregiverRepository.save(any(Caregiver.class))).thenReturn(saved);

        CaregiverResponse result = service.createCaregiver(agencyId, req);

        assertThat(result.firstName()).isEqualTo("Jane");
        verify(caregiverRepository).save(any(Caregiver.class));
    }

    // --- getCaregiver ---

    @Test
    void getCaregiver_returns_caregiver_when_found() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));

        CaregiverResponse result = service.getCaregiver(agencyId, caregiverId);

        assertThat(result.email()).isEqualTo("john@example.com");
    }

    @Test
    void getCaregiver_throws_404_when_not_found() {
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCaregiver(agencyId, caregiverId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void getCaregiver_throws_404_when_belongs_to_other_agency() {
        Caregiver caregiver = new Caregiver(UUID.randomUUID(), "John", "Doe", "john@example.com");
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));

        assertThatThrownBy(() -> service.getCaregiver(agencyId, caregiverId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    // --- updateCaregiver ---

    @Test
    void updateCaregiver_applies_non_null_fields() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(caregiverRepository.save(caregiver)).thenReturn(caregiver);

        UpdateCaregiverRequest req = new UpdateCaregiverRequest(
            "Jonathan", null, null, null, null, null, null, null, null, null);

        service.updateCaregiver(agencyId, caregiverId, req);

        assertThat(caregiver.getFirstName()).isEqualTo("Jonathan");
        assertThat(caregiver.getLastName()).isEqualTo("Doe"); // unchanged
        verify(caregiverRepository).save(caregiver);
    }

    @Test
    void updateCaregiver_can_set_status_to_terminated() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(caregiverRepository.save(caregiver)).thenReturn(caregiver);

        UpdateCaregiverRequest req = new UpdateCaregiverRequest(
            null, null, null, null, null, null, null, null, null, CaregiverStatus.TERMINATED);

        service.updateCaregiver(agencyId, caregiverId, req);

        assertThat(caregiver.getStatus()).isEqualTo(CaregiverStatus.TERMINATED);
    }

    @Test
    void updateCaregiver_throws_409_when_email_already_taken_by_different_caregiver() {
        Caregiver caregiver = makeCaregiver();
        Caregiver other = new Caregiver(agencyId, "Jane", "Doe", "taken@example.com");
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(caregiverRepository.findByAgencyIdAndEmail(agencyId, "taken@example.com"))
            .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateCaregiver(agencyId, caregiverId,
            new UpdateCaregiverRequest(null, null, "taken@example.com", null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("409");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE` — `CaregiverService` does not exist yet.

- [ ] **Step 3: Add missing setters to Caregiver entity and extend CaregiverRepository**

In `backend/src/main/java/com/hcare/domain/Caregiver.java`, add after the existing setters:
```java
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public void setHasPet(boolean hasPet) { this.hasPet = hasPet; }
    public void setStatus(CaregiverStatus status) { this.status = status; }
```

Also replace `backend/src/main/java/com/hcare/domain/CaregiverRepository.java` with:

```java
package com.hcare.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CaregiverRepository extends JpaRepository<Caregiver, UUID> {
    List<Caregiver> findByAgencyId(UUID agencyId);
    boolean existsByIdAndAgencyId(UUID id, UUID agencyId);
    Optional<Caregiver> findByAgencyIdAndEmail(UUID agencyId, String email);
}
```

- [ ] **Step 4: Create caregiver DTOs**

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/CreateCaregiverRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateCaregiverRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank @Email String email,
    String phone,
    String address,
    LocalDate hireDate,
    Boolean hasPet,
    String languages,
    String fcmToken
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/UpdateCaregiverRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateCaregiverRequest(
    @Size(min = 1) String firstName,
    @Size(min = 1) String lastName,
    @Email String email,
    String phone,
    String address,
    LocalDate hireDate,
    Boolean hasPet,
    String languages,
    String fcmToken,
    CaregiverStatus status
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/CaregiverResponse.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CaregiverResponse(
    UUID id,
    UUID agencyId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String address,
    LocalDate hireDate,
    CaregiverStatus status,
    boolean hasPet,
    String languages,
    LocalDateTime createdAt
) {
    public static CaregiverResponse from(Caregiver c) {
        return new CaregiverResponse(
            c.getId(), c.getAgencyId(), c.getFirstName(), c.getLastName(),
            c.getEmail(), c.getPhone(), c.getAddress(), c.getHireDate(),
            c.getStatus(), c.hasPet(), c.getLanguages(), c.getCreatedAt());
    }
}
```

- [ ] **Step 5: Create CaregiverService**

`backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverService.java`:

```java
package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.domain.Caregiver;
import com.hcare.domain.CaregiverRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CaregiverService {

    private final CaregiverRepository caregiverRepository;

    public CaregiverService(CaregiverRepository caregiverRepository) {
        this.caregiverRepository = caregiverRepository;
    }

    @Transactional(readOnly = true)
    public List<CaregiverResponse> listCaregivers(UUID agencyId) {
        return caregiverRepository.findByAgencyId(agencyId).stream()
            .map(CaregiverResponse::from)
            .toList();
    }

    @Transactional
    public CaregiverResponse createCaregiver(UUID agencyId, CreateCaregiverRequest req) {
        Caregiver caregiver = new Caregiver(agencyId, req.firstName(), req.lastName(), req.email());
        if (req.phone() != null) caregiver.setPhone(req.phone());
        if (req.address() != null) caregiver.setAddress(req.address());
        if (req.hireDate() != null) caregiver.setHireDate(req.hireDate());
        if (req.hasPet() != null) caregiver.setHasPet(req.hasPet());
        if (req.languages() != null) caregiver.setLanguages(req.languages());
        if (req.fcmToken() != null) caregiver.setFcmToken(req.fcmToken());
        return CaregiverResponse.from(caregiverRepository.save(caregiver));
    }

    @Transactional(readOnly = true)
    public CaregiverResponse getCaregiver(UUID agencyId, UUID caregiverId) {
        return CaregiverResponse.from(requireCaregiver(agencyId, caregiverId));
    }

    @Transactional
    public CaregiverResponse updateCaregiver(UUID agencyId, UUID caregiverId,
                                              UpdateCaregiverRequest req) {
        Caregiver caregiver = requireCaregiver(agencyId, caregiverId);
        if (req.firstName() != null) caregiver.setFirstName(req.firstName());
        if (req.lastName() != null) caregiver.setLastName(req.lastName());
        if (req.email() != null) {
            caregiverRepository.findByAgencyIdAndEmail(agencyId, req.email())
                .filter(existing -> !existing.getId().equals(caregiverId))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A caregiver with this email already exists in the agency");
                });
            caregiver.setEmail(req.email());
        }
        if (req.phone() != null) caregiver.setPhone(req.phone());
        if (req.address() != null) caregiver.setAddress(req.address());
        if (req.hireDate() != null) caregiver.setHireDate(req.hireDate());
        if (req.hasPet() != null) caregiver.setHasPet(req.hasPet());
        if (req.languages() != null) caregiver.setLanguages(req.languages());
        if (req.fcmToken() != null) caregiver.setFcmToken(req.fcmToken());
        if (req.status() != null) caregiver.setStatus(req.status());
        return CaregiverResponse.from(caregiverRepository.save(caregiver));
    }

    // --- helpers (package-private) ---

    Caregiver requireCaregiver(UUID agencyId, UUID caregiverId) {
        Caregiver caregiver = caregiverRepository.findById(caregiverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found"));
        if (!caregiver.getAgencyId().equals(agencyId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Caregiver not found");
        }
        return caregiver;
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 8, Failures: 0`.

- [ ] **Step 7: Create CaregiverController**

`backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverController.java`:

```java
package com.hcare.api.v1.caregivers;

import com.hcare.api.v1.caregivers.dto.CaregiverResponse;
import com.hcare.api.v1.caregivers.dto.CreateCaregiverRequest;
import com.hcare.api.v1.caregivers.dto.UpdateCaregiverRequest;
import com.hcare.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/caregivers")
public class CaregiverController {

    private final CaregiverService caregiverService;

    public CaregiverController(CaregiverService caregiverService) {
        this.caregiverService = caregiverService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CaregiverResponse>> listCaregivers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(caregiverService.listCaregivers(principal.getAgencyId()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> createCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateCaregiverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.createCaregiver(principal.getAgencyId(), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> getCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.getCaregiver(principal.getAgencyId(), id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CaregiverResponse> updateCaregiver(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCaregiverRequest request) {
        return ResponseEntity.ok(caregiverService.updateCaregiver(principal.getAgencyId(), id, request));
    }
}
```

- [ ] **Step 8: Compile check**

```bash
cd backend && mvn compile -q 2>&1 | tail -10
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/hcare/domain/Caregiver.java \
  src/main/java/com/hcare/domain/CaregiverRepository.java \
  src/main/java/com/hcare/api/v1/caregivers/ \
  src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java
git commit -m "feat: add Caregiver CRUD REST API"
```

---

## Task 6: Caregiver Credentials

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/AddCredentialRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/CredentialResponse.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverService.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java`

- [ ] **Step 1: Add credential tests**

Add to imports in `CaregiverServiceTest.java`:
```java
import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
import com.hcare.domain.CredentialType;
import java.time.LocalDate;
```

Add new mock:
```java
@Mock CaregiverCredentialRepository credentialRepository;
```

Update `setUp()`:
```java
@BeforeEach
void setUp() {
    service = new CaregiverService(caregiverRepository, credentialRepository);
}
```

Add test methods:
```java
    // --- credentials ---

    @Test
    void listCredentials_returns_all_for_caregiver() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        CaregiverCredential cred = new CaregiverCredential(caregiverId, agencyId,
            CredentialType.HHA, LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31));
        when(credentialRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(cred));

        List<CredentialResponse> result = service.listCredentials(agencyId, caregiverId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).credentialType()).isEqualTo(CredentialType.HHA);
    }

    @Test
    void addCredential_saves_credential() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        CaregiverCredential saved = new CaregiverCredential(caregiverId, agencyId,
            CredentialType.CNA, LocalDate.of(2024, 6, 1), LocalDate.of(2026, 6, 1));
        when(credentialRepository.save(any())).thenReturn(saved);

        CredentialResponse result = service.addCredential(agencyId, caregiverId,
            new AddCredentialRequest(CredentialType.CNA,
                LocalDate.of(2024, 6, 1), LocalDate.of(2026, 6, 1)));

        assertThat(result.credentialType()).isEqualTo(CredentialType.CNA);
        verify(credentialRepository).save(any(CaregiverCredential.class));
    }

    @Test
    void verifyCredential_calls_verify_and_saves() {
        UUID credId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        Caregiver caregiver = makeCaregiver();
        CaregiverCredential cred = new CaregiverCredential(caregiverId, agencyId,
            CredentialType.HHA, LocalDate.of(2024, 1, 1), null);
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));
        when(credentialRepository.save(cred)).thenReturn(cred);

        CredentialResponse result = service.verifyCredential(agencyId, caregiverId, credId, adminUserId);

        assertThat(result.verified()).isTrue();
        assertThat(cred.getVerifiedBy()).isEqualTo(adminUserId);
        verify(credentialRepository).save(cred);
    }

    @Test
    void verifyCredential_throws_422_when_belongs_to_other_caregiver() {
        UUID credId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        Caregiver caregiver = makeCaregiver();
        CaregiverCredential cred = new CaregiverCredential(UUID.randomUUID(), agencyId,
            CredentialType.HHA, LocalDate.of(2024, 1, 1), null);
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> service.verifyCredential(agencyId, caregiverId, credId, adminUserId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
    }

    @Test
    void deleteCredential_removes_when_belongs_to_caregiver() {
        UUID credId = UUID.randomUUID();
        Caregiver caregiver = makeCaregiver();
        CaregiverCredential cred = new CaregiverCredential(caregiverId, agencyId,
            CredentialType.CPR, LocalDate.of(2024, 3, 1), LocalDate.of(2026, 3, 1));
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));

        service.deleteCredential(agencyId, caregiverId, credId);

        verify(credentialRepository).delete(cred);
    }

    @Test
    void deleteCredential_throws_422_when_belongs_to_other_caregiver() {
        UUID credId = UUID.randomUUID();
        Caregiver caregiver = makeCaregiver();
        CaregiverCredential cred = new CaregiverCredential(UUID.randomUUID(), agencyId,
            CredentialType.CPR, null, null);
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(credentialRepository.findById(credId)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> service.deleteCredential(agencyId, caregiverId, credId))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("422");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE`.

- [ ] **Step 3: Create credential DTOs**

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/AddCredentialRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CredentialType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AddCredentialRequest(
    @NotNull CredentialType credentialType,
    LocalDate issueDate,
    LocalDate expiryDate
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/CredentialResponse.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CredentialType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CredentialResponse(
    UUID id,
    UUID caregiverId,
    CredentialType credentialType,
    LocalDate issueDate,
    LocalDate expiryDate,
    boolean verified,
    UUID verifiedBy,
    LocalDateTime createdAt
) {
    public static CredentialResponse from(CaregiverCredential c) {
        return new CredentialResponse(
            c.getId(), c.getCaregiverId(), c.getCredentialType(),
            c.getIssueDate(), c.getExpiryDate(),
            c.isVerified(), c.getVerifiedBy(), c.getCreatedAt());
    }
}
```

- [ ] **Step 4: Add credential methods to CaregiverService**

Update `CaregiverService.java` — add `CaregiverCredentialRepository` constructor parameter and field, then add methods:

```java
    // --- credentials ---

    @Transactional(readOnly = true)
    public List<CredentialResponse> listCredentials(UUID agencyId, UUID caregiverId) {
        requireCaregiver(agencyId, caregiverId);
        return credentialRepository.findByCaregiverId(caregiverId).stream()
            .map(CredentialResponse::from)
            .toList();
    }

    @Transactional
    public CredentialResponse addCredential(UUID agencyId, UUID caregiverId,
                                            AddCredentialRequest req) {
        requireCaregiver(agencyId, caregiverId);
        CaregiverCredential cred = new CaregiverCredential(
            caregiverId, agencyId, req.credentialType(), req.issueDate(), req.expiryDate());
        return CredentialResponse.from(credentialRepository.save(cred));
    }

    @Transactional
    public CredentialResponse verifyCredential(UUID agencyId, UUID caregiverId,
                                               UUID credentialId, UUID adminUserId) {
        requireCaregiver(agencyId, caregiverId);
        CaregiverCredential cred = credentialRepository.findById(credentialId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Credential not found"));
        if (!cred.getCaregiverId().equals(caregiverId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Credential does not belong to this caregiver");
        }
        cred.verify(adminUserId);
        return CredentialResponse.from(credentialRepository.save(cred));
    }

    @Transactional
    public void deleteCredential(UUID agencyId, UUID caregiverId, UUID credentialId) {
        requireCaregiver(agencyId, caregiverId);
        CaregiverCredential cred = credentialRepository.findById(credentialId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Credential not found"));
        if (!cred.getCaregiverId().equals(caregiverId)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Credential does not belong to this caregiver");
        }
        credentialRepository.delete(cred);
    }
```

Also add imports:
```java
import com.hcare.api.v1.caregivers.dto.AddCredentialRequest;
import com.hcare.api.v1.caregivers.dto.CredentialResponse;
import com.hcare.domain.CaregiverCredential;
import com.hcare.domain.CaregiverCredentialRepository;
```

- [ ] **Step 5: Add credential endpoints to CaregiverController**

Add to `CaregiverController.java`:
```java
    // --- Credentials ---

    @GetMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<CredentialResponse>> listCredentials(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.listCredentials(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/credentials")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<CredentialResponse> addCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AddCredentialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.addCredential(principal.getAgencyId(), id, request));
    }

    @PostMapping("/{id}/credentials/{credentialId}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CredentialResponse> verifyCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID credentialId) {
        return ResponseEntity.ok(caregiverService.verifyCredential(
            principal.getAgencyId(), id, credentialId, principal.getUserId()));
    }

    @DeleteMapping("/{id}/credentials/{credentialId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<Void> deleteCredential(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID credentialId) {
        caregiverService.deleteCredential(principal.getAgencyId(), id, credentialId);
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 6: Run all CaregiverServiceTest tests**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 13, Failures: 0`.

- [ ] **Step 7: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/caregivers/ \
  src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java
git commit -m "feat: add caregiver credentials sub-resource with verify action"
```

---

## Task 7: Background Checks, Availability & Shift History

**Files:**
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/RecordBackgroundCheckRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/BackgroundCheckResponse.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/SetAvailabilityRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/AvailabilityBlockRequest.java`
- Create: `backend/src/main/java/com/hcare/api/v1/caregivers/dto/AvailabilityResponse.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverService.java`
- Modify: `backend/src/main/java/com/hcare/api/v1/caregivers/CaregiverController.java`
- Modify: `backend/src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java`

- [ ] **Step 1: Add background check, availability, and shift history tests**

Add to imports in `CaregiverServiceTest.java`:
```java
import com.hcare.api.v1.caregivers.dto.AvailabilityBlockRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckRepository;
import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;
import com.hcare.domain.CaregiverAvailability;
import com.hcare.domain.CaregiverAvailabilityRepository;
import com.hcare.domain.Shift;
import com.hcare.domain.ShiftRepository;
import com.hcare.domain.ShiftStatus;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
```

Add new mocks:
```java
@Mock BackgroundCheckRepository backgroundCheckRepository;
@Mock CaregiverAvailabilityRepository availabilityRepository;
@Mock ShiftRepository shiftRepository;
```

Update `setUp()` with the full final constructor:
```java
@BeforeEach
void setUp() {
    service = new CaregiverService(caregiverRepository, credentialRepository,
        backgroundCheckRepository, availabilityRepository, shiftRepository);
}
```

Add test methods:
```java
    // --- background checks ---

    @Test
    void recordBackgroundCheck_saves_check() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        BackgroundCheck saved = new BackgroundCheck(caregiverId, agencyId,
            BackgroundCheckType.OIG, BackgroundCheckResult.CLEAR, LocalDate.of(2026, 1, 15));
        when(backgroundCheckRepository.save(any())).thenReturn(saved);

        BackgroundCheckResponse result = service.recordBackgroundCheck(agencyId, caregiverId,
            new RecordBackgroundCheckRequest(BackgroundCheckType.OIG, BackgroundCheckResult.CLEAR,
                LocalDate.of(2026, 1, 15), null));

        assertThat(result.checkType()).isEqualTo(BackgroundCheckType.OIG);
        assertThat(result.result()).isEqualTo(BackgroundCheckResult.CLEAR);
        verify(backgroundCheckRepository).save(any(BackgroundCheck.class));
    }

    @Test
    void listBackgroundChecks_returns_all_for_caregiver() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        BackgroundCheck check = new BackgroundCheck(caregiverId, agencyId,
            BackgroundCheckType.FBI, BackgroundCheckResult.PENDING, LocalDate.of(2026, 1, 1));
        when(backgroundCheckRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(check));

        List<BackgroundCheckResponse> result = service.listBackgroundChecks(agencyId, caregiverId);

        assertThat(result).hasSize(1);
    }

    // --- availability ---

    @Test
    void setAvailability_deletes_existing_and_saves_new_blocks() {
        Caregiver caregiver = makeCaregiver();
        CaregiverAvailability old = new CaregiverAvailability(caregiverId, agencyId,
            DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0));
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(availabilityRepository.findByCaregiverId(caregiverId)).thenReturn(List.of(old));
        CaregiverAvailability newBlock = new CaregiverAvailability(caregiverId, agencyId,
            DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        when(availabilityRepository.saveAll(any())).thenReturn(List.of(newBlock));

        List<AvailabilityResponse> result = service.setAvailability(agencyId, caregiverId,
            new SetAvailabilityRequest(List.of(
                new AvailabilityBlockRequest(DayOfWeek.TUESDAY,
                    LocalTime.of(9, 0), LocalTime.of(17, 0)))));

        assertThat(result).hasSize(1);
        verify(availabilityRepository).deleteAllInBatch(List.of(old));
        verify(availabilityRepository).saveAll(any());
    }

    @Test
    void setAvailability_rejects_start_time_not_before_end_time() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(availabilityRepository.findByCaregiverId(caregiverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.setAvailability(agencyId, caregiverId,
            new SetAvailabilityRequest(List.of(
                new AvailabilityBlockRequest(DayOfWeek.MONDAY,
                    LocalTime.of(17, 0), LocalTime.of(9, 0))))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }

    @Test
    void setAvailability_rejects_duplicate_blocks() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(availabilityRepository.findByCaregiverId(caregiverId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.setAvailability(agencyId, caregiverId,
            new SetAvailabilityRequest(List.of(
                new AvailabilityBlockRequest(DayOfWeek.MONDAY,
                    LocalTime.of(9, 0), LocalTime.of(17, 0)),
                new AvailabilityBlockRequest(DayOfWeek.MONDAY,
                    LocalTime.of(9, 0), LocalTime.of(17, 0))))))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }

    // --- shift history ---

    @Test
    void listShifts_returns_shifts_for_caregiver_in_date_range() {
        UUID clientId = UUID.randomUUID();
        UUID serviceTypeId = UUID.randomUUID();
        Caregiver caregiver = makeCaregiver();
        LocalDateTime start = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 5, 31, 23, 59);
        Shift shift = new Shift(agencyId, null, clientId, caregiverId, serviceTypeId, null,
            LocalDateTime.of(2026, 5, 10, 9, 0), LocalDateTime.of(2026, 5, 10, 13, 0));
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        when(shiftRepository.findByCaregiverIdAndScheduledStartBetween(
            caregiverId, start, end)).thenReturn(List.of(shift));

        List<ShiftSummaryResponse> result = service.listShifts(agencyId, caregiverId, start, end);

        assertThat(result).hasSize(1);
        verify(shiftRepository).findByCaregiverIdAndScheduledStartBetween(caregiverId, start, end);
    }

    @Test
    void listShifts_throws_400_when_end_not_after_start() {
        Caregiver caregiver = makeCaregiver();
        when(caregiverRepository.findById(caregiverId)).thenReturn(Optional.of(caregiver));
        LocalDateTime t = LocalDateTime.of(2026, 5, 1, 0, 0);

        assertThatThrownBy(() -> service.listShifts(agencyId, caregiverId, t, t))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD FAILURE`.

- [ ] **Step 3: Create remaining DTOs**

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/RecordBackgroundCheckRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordBackgroundCheckRequest(
    @NotNull BackgroundCheckType checkType,
    @NotNull BackgroundCheckResult result,
    @NotNull LocalDate checkedAt,
    LocalDate renewalDueDate
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/BackgroundCheckResponse.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckResult;
import com.hcare.domain.BackgroundCheckType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BackgroundCheckResponse(
    UUID id,
    UUID caregiverId,
    BackgroundCheckType checkType,
    BackgroundCheckResult result,
    LocalDate checkedAt,
    LocalDate renewalDueDate,
    LocalDateTime createdAt
) {
    public static BackgroundCheckResponse from(BackgroundCheck b) {
        return new BackgroundCheckResponse(
            b.getId(), b.getCaregiverId(), b.getCheckType(), b.getResult(),
            b.getCheckedAt(), b.getRenewalDueDate(), b.getCreatedAt());
    }
}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/AvailabilityBlockRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityBlockRequest(
    @NotNull DayOfWeek dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/SetAvailabilityRequest.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SetAvailabilityRequest(
    @NotNull @Valid List<AvailabilityBlockRequest> blocks
) {}
```

`backend/src/main/java/com/hcare/api/v1/caregivers/dto/AvailabilityResponse.java`:
```java
package com.hcare.api.v1.caregivers.dto;

import com.hcare.domain.CaregiverAvailability;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record AvailabilityResponse(
    UUID id,
    UUID caregiverId,
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    LocalDateTime createdAt
) {
    public static AvailabilityResponse from(CaregiverAvailability a) {
        return new AvailabilityResponse(
            a.getId(), a.getCaregiverId(), a.getDayOfWeek(),
            a.getStartTime(), a.getEndTime(), a.getCreatedAt());
    }
}
```

- [ ] **Step 4: Add missing BackgroundCheck setter and verify constructors**

**4a. Add `setRenewalDueDate` to BackgroundCheck (C1):**

In `backend/src/main/java/com/hcare/domain/BackgroundCheck.java`, add after the existing getters:
```java
    public void setRenewalDueDate(LocalDate renewalDueDate) { this.renewalDueDate = renewalDueDate; }
```

**4b. Verify CaregiverAvailability constructor:**

Read `backend/src/main/java/com/hcare/domain/CaregiverAvailability.java` to confirm the constructor signature is `CaregiverAvailability(UUID caregiverId, UUID agencyId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime)` and that getters `getDayOfWeek()`, `getStartTime()`, `getEndTime()` exist. Also confirm the entity validates `startTime < endTime`. Adjust `AvailabilityResponse.from()` if getter names differ.

**4c. Verify ShiftSummaryResponse factory method (C2):**

Read `backend/src/main/java/com/hcare/api/v1/scheduling/dto/ShiftSummaryResponse.java`.
- If a static `from(Shift s)` factory method exists: the `listShifts` code in Step 5 uses `ShiftSummaryResponse::from` — no change needed.
- If no `from()` factory exists (only a public canonical constructor): confirm the field order matches `(UUID id, UUID agencyId, UUID clientId, UUID caregiverId, UUID serviceTypeId, UUID authorizationId, UUID sourcePatternId, LocalDateTime scheduledStart, LocalDateTime scheduledEnd, ShiftStatus status, String notes)` before proceeding.

- [ ] **Step 5: Add remaining methods to CaregiverService**

Update `CaregiverService.java` — add three new repository constructor parameters and fields: `BackgroundCheckRepository backgroundCheckRepository, CaregiverAvailabilityRepository availabilityRepository, ShiftRepository shiftRepository`.

Add these methods:
```java
    // --- background checks ---

    @Transactional(readOnly = true)
    public List<BackgroundCheckResponse> listBackgroundChecks(UUID agencyId, UUID caregiverId) {
        requireCaregiver(agencyId, caregiverId);
        return backgroundCheckRepository.findByCaregiverId(caregiverId).stream()
            .map(BackgroundCheckResponse::from)
            .toList();
    }

    @Transactional
    public BackgroundCheckResponse recordBackgroundCheck(UUID agencyId, UUID caregiverId,
                                                          RecordBackgroundCheckRequest req) {
        requireCaregiver(agencyId, caregiverId);
        BackgroundCheck check = new BackgroundCheck(
            caregiverId, agencyId, req.checkType(), req.result(), req.checkedAt());
        if (req.renewalDueDate() != null) check.setRenewalDueDate(req.renewalDueDate());
        return BackgroundCheckResponse.from(backgroundCheckRepository.save(check));
    }

    // --- availability ---

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> getAvailability(UUID agencyId, UUID caregiverId) {
        requireCaregiver(agencyId, caregiverId);
        return availabilityRepository.findByCaregiverId(caregiverId).stream()
            .map(AvailabilityResponse::from)
            .toList();
    }

    @Transactional
    public List<AvailabilityResponse> setAvailability(UUID agencyId, UUID caregiverId,
                                                       SetAvailabilityRequest req) {
        requireCaregiver(agencyId, caregiverId);
        for (AvailabilityBlockRequest block : req.blocks()) {
            if (!block.startTime().isBefore(block.endTime())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startTime must be before endTime for " + block.dayOfWeek() + " block");
            }
        }
        Set<String> seen = new HashSet<>();
        for (AvailabilityBlockRequest block : req.blocks()) {
            String key = block.dayOfWeek() + "|" + block.startTime() + "|" + block.endTime();
            if (!seen.add(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Duplicate availability block: " + block.dayOfWeek()
                        + " " + block.startTime() + "-" + block.endTime());
            }
        }
        List<AvailabilityBlockRequest> blocks = req.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                AvailabilityBlockRequest a = blocks.get(i);
                AvailabilityBlockRequest b = blocks.get(j);
                if (a.dayOfWeek() == b.dayOfWeek()
                        && a.startTime().isBefore(b.endTime())
                        && b.startTime().isBefore(a.endTime())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Overlapping availability blocks on " + a.dayOfWeek());
                }
            }
        }
        availabilityRepository.deleteAllInBatch(
            availabilityRepository.findByCaregiverId(caregiverId));
        List<CaregiverAvailability> newBlocks = req.blocks().stream()
            .map(b -> new CaregiverAvailability(caregiverId, agencyId,
                b.dayOfWeek(), b.startTime(), b.endTime()))
            .toList();
        return availabilityRepository.saveAll(newBlocks).stream()
            .map(AvailabilityResponse::from)
            .toList();
    }

    // --- shift history ---

    @Transactional(readOnly = true)
    public List<ShiftSummaryResponse> listShifts(UUID agencyId, UUID caregiverId,
                                                  LocalDateTime start, LocalDateTime end) {
        requireCaregiver(agencyId, caregiverId);
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be after start");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Date range cannot exceed 366 days");
        }
        return shiftRepository.findByCaregiverIdAndScheduledStartBetween(
                caregiverId, start, end).stream()
            .map(ShiftSummaryResponse::from)
            .toList();
    }
```

Also add imports to `CaregiverService.java`:
```java
import com.hcare.api.v1.caregivers.dto.AvailabilityBlockRequest;
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import com.hcare.domain.BackgroundCheck;
import com.hcare.domain.BackgroundCheckRepository;
import com.hcare.domain.CaregiverAvailability;
import com.hcare.domain.CaregiverAvailabilityRepository;
import com.hcare.domain.ShiftRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
```

- [ ] **Step 6: Add remaining endpoints to CaregiverController**

Add to `CaregiverController.java`:
```java
    // --- Background Checks ---

    @GetMapping("/{id}/background-checks")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<BackgroundCheckResponse>> listBackgroundChecks(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.listBackgroundChecks(principal.getAgencyId(), id));
    }

    @PostMapping("/{id}/background-checks")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BackgroundCheckResponse> recordBackgroundCheck(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RecordBackgroundCheckRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(caregiverService.recordBackgroundCheck(principal.getAgencyId(), id, request));
    }

    // --- Availability ---

    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AvailabilityResponse>> getAvailability(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(caregiverService.getAvailability(principal.getAgencyId(), id));
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<AvailabilityResponse>> setAvailability(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SetAvailabilityRequest request) {
        return ResponseEntity.ok(caregiverService.setAvailability(principal.getAgencyId(), id, request));
    }

    // --- Shift History ---

    @GetMapping("/{id}/shifts")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCHEDULER')")
    public ResponseEntity<List<ShiftSummaryResponse>> listShifts(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(caregiverService.listShifts(principal.getAgencyId(), id, start, end));
    }
```

Add to `CaregiverController.java` imports:
```java
import com.hcare.api.v1.caregivers.dto.AvailabilityResponse;
import com.hcare.api.v1.caregivers.dto.BackgroundCheckResponse;
import com.hcare.api.v1.caregivers.dto.RecordBackgroundCheckRequest;
import com.hcare.api.v1.caregivers.dto.SetAvailabilityRequest;
import com.hcare.api.v1.scheduling.dto.ShiftSummaryResponse;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
```

- [ ] **Step 7: Run all CaregiverServiceTest tests**

```bash
cd backend && mvn test -Dtest=CaregiverServiceTest -q 2>&1 | tail -5
```

Expected: `BUILD SUCCESS` — `Tests run: 22, Failures: 0`.

- [ ] **Step 8: Run full test suite**

```bash
cd backend && mvn test -q 2>&1 | tail -8
```

Expected: `BUILD SUCCESS` — all tests pass, no regressions.

- [ ] **Step 9: Commit**

```bash
cd backend && git add src/main/java/com/hcare/api/v1/caregivers/ \
  src/test/java/com/hcare/api/v1/caregivers/CaregiverServiceTest.java
git commit -m "feat: add background checks, availability, and shift history to caregiver API"
```

---

## Self-Review Checklist

**Spec coverage (Section 6 MVP Feature Modules):**
- ✅ Clients — profile CRUD: `Task 1`
- ✅ Diagnoses: `Task 2`
- ✅ Medications: `Task 2`
- ✅ Care plans with ADL tasks and goals: `Task 3`
- ✅ Payer assignment (via authorizations): `Task 4`
- ✅ Authorization creation and real-time utilization display: `Task 4`
- ✅ Family portal access management: `Task 4`
- ✅ Caregivers — profile CRUD: `Task 5`
- ✅ Credentials with expiry tracking: `Task 6`
- ✅ Background check status: `Task 7`
- ✅ Availability: `Task 7`
- ✅ Shift history: `Task 7`
- ⚠️ Documents — **explicitly out of scope** (file storage infrastructure is P2)

**Business rules covered:**
- Multi-tenant access: `requireClient`/`requireCaregiver` helpers throw 404 on cross-agency access
- Care plan lifecycle: one ACTIVE per client; `activate` supersedes existing active plan
- Care plan version auto-increments
- Availability replace-all: validates `startTime < endTime` and no duplicates before delete+insert
- Authorization date range: `endDate > startDate` enforced
- Authorization payer/serviceType: validated to belong to same agency
- Family portal user email uniqueness per agency
- Credential verify: 422 if credential belongs to other caregiver (matches pattern in scheduling API)

**Type consistency check:**
- `ClientResponse.from(Client)`, `CaregiverResponse.from(Caregiver)`, etc. — static factory methods used consistently throughout
- `requireClient` / `requireCaregiver` helpers are package-private (not private) — accessible in tests for future subclassing
- `ShiftSummaryResponse` reused from `com.hcare.api.v1.scheduling.dto` in `CaregiverService.listShifts`

**Placeholder check:** None found. All steps contain complete code.
