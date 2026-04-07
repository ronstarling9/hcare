package com.hcare.api.v1.documents;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.documents.dto.DocumentResponse;
import com.hcare.domain.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE documents, clients, caregivers, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class DocumentControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CaregiverRepository caregiverRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";
    private Agency agency;
    private Client client;
    private Caregiver caregiver;

    @BeforeEach
    void seed() throws IOException {
        Files.createDirectories(Path.of("/tmp/hcare-test-docs"));
        agency = agencyRepo.save(new Agency("Doc IT Agency", "TX"));
        userRepo.save(new AgencyUser(agency.getId(), "admin@docit.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Doc", "Client", LocalDate.of(1970, 1, 1)));
        caregiver = caregiverRepo.save(new Caregiver(agency.getId(), "Doc", "CG", "docCg@test.com"));
    }

    @AfterEach
    void cleanup() throws IOException {
        Path dir = Path.of("/tmp/hcare-test-docs");
        if (Files.exists(dir)) {
            Files.walk(dir).sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
        }
    }

    private HttpHeaders auth() {
        String token = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@docit.com", TEST_PASSWORD), LoginResponse.class)
            .getBody().token();
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private HttpEntity<MultiValueMap<String, Object>> multipartUpload(
            String filename, byte[] content, String docType) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.TEXT_PLAIN);
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, fileHeaders));
        if (docType != null) body.add("documentType", docType);
        HttpHeaders headers = auth();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(body, headers);
    }

    @Test
    void uploadAndListDocument_forClient() {
        ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents", HttpMethod.POST,
            multipartUpload("careplan.txt", "content".getBytes(), "CARE_PLAN"),
            DocumentResponse.class);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload.getBody().fileName()).isEqualTo("careplan.txt");
        assertThat(upload.getBody().documentType()).isEqualTo("CARE_PLAN");

        ResponseEntity<List<DocumentResponse>> list = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents", HttpMethod.GET,
            new HttpEntity<>(auth()), new ParameterizedTypeReference<>() {});
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).hasSize(1);
    }

    @Test
    void downloadDocument_returnsFileContent() {
        ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents", HttpMethod.POST,
            multipartUpload("hello.txt", "hello world".getBytes(), null),
            DocumentResponse.class);

        // GET /content — backend streams file bytes directly with 200 OK
        ResponseEntity<byte[]> download = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents/" + upload.getBody().id() + "/content",
            HttpMethod.GET, new HttpEntity<>(auth()), byte[].class);

        assertThat(download.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(download.getBody())).isEqualTo("hello world");
    }

    @Test
    void deleteDocument_removesItFromList() {
        ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents", HttpMethod.POST,
            multipartUpload("todelete.txt", "bye".getBytes(), null), DocumentResponse.class);

        restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents/" + upload.getBody().id(),
            HttpMethod.DELETE, new HttpEntity<>(auth()), Void.class);

        ResponseEntity<List<DocumentResponse>> list = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents", HttpMethod.GET,
            new HttpEntity<>(auth()), new ParameterizedTypeReference<>() {});
        assertThat(list.getBody()).isEmpty();
    }

    @Test
    void uploadDocument_forCaregiver() {
        ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/documents", HttpMethod.POST,
            multipartUpload("bgcheck.txt", "clear".getBytes(), "BACKGROUND_CHECK"),
            DocumentResponse.class);

        assertThat(upload.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload.getBody().documentType()).isEqualTo("BACKGROUND_CHECK");
    }

    @Test
    void document_from_other_agency_client_returns_404() {
        Agency other = agencyRepo.save(new Agency("Other", "CA"));
        Client otherClient = clientRepo.save(new Client(other.getId(), "X", "Y", LocalDate.now()));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + otherClient.getId() + "/documents", HttpMethod.GET,
            new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void downloadDocument_from_wrong_owner_returns_404() {
        ResponseEntity<DocumentResponse> upload = restTemplate.exchange(
            "/api/v1/caregivers/" + caregiver.getId() + "/documents", HttpMethod.POST,
            multipartUpload("secret.txt", "top secret".getBytes(), null), DocumentResponse.class);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/documents/" + upload.getBody().id() + "/content",
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
