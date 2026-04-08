package com.hcare.api.v1.clients;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.clients.dto.CarePlanResponse;
import com.hcare.api.v1.clients.dto.ClientResponse;
import com.hcare.api.v1.clients.dto.CreateCarePlanRequest;
import com.hcare.api.v1.clients.dto.CreateClientRequest;
import com.hcare.api.v1.clients.dto.UpdateClientRequest;
import com.hcare.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE adl_task_completions, adl_tasks, goals, care_plans, " +
    "documents, family_portal_users, authorizations, client_diagnoses, client_medications, " +
    "clients, caregiver_client_affinities, caregiver_scoring_profiles, " +
    "caregivers, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ClientControllerIT extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD = "correcthorsebatterystaple";

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AgencyRepository agencyRepo;
    @Autowired private AgencyUserRepository userRepo;
    @Autowired private ClientRepository clientRepo;
    @Autowired private CarePlanRepository carePlanRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    private Agency agency;
    private Client client;
    private String token;

    @BeforeEach
    void seed() {
        agency = agencyRepo.save(new Agency("Client IT Agency", "CA"));
        userRepo.save(new AgencyUser(agency.getId(), "admin@clientit.com",
            passwordEncoder.encode(TEST_PASSWORD), UserRole.ADMIN));
        client = clientRepo.save(new Client(agency.getId(), "Alice", "Test", LocalDate.of(1950, 6, 15)));
        token = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("admin@clientit.com", TEST_PASSWORD), LoginResponse.class)
            .getBody().token();
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    // --- GET /clients ---

    @Test
    void listClients_returns_page_for_agency() {
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/clients", HttpMethod.GET,
            new HttpEntity<>(auth()), new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    void listClients_excludes_clients_from_other_agency() {
        Agency other = agencyRepo.save(new Agency("Other Agency", "TX"));
        clientRepo.save(new Client(other.getId(), "Bob", "Other", LocalDate.of(1960, 1, 1)));

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            "/api/v1/clients", HttpMethod.GET,
            new HttpEntity<>(auth()), new ParameterizedTypeReference<>() {});

        assertThat((Integer) resp.getBody().get("totalElements")).isEqualTo(1);
    }

    @Test
    void listClients_returns_401_without_token() {
        HttpHeaders h = new HttpHeaders();
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients", HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- POST /clients ---

    @Test
    void createClient_returns_201_with_id() {
        CreateClientRequest req = new CreateClientRequest(
            "Bob", "Builder", LocalDate.of(1980, 3, 1), null, null, null, null, null, null, null);

        ResponseEntity<ClientResponse> resp = restTemplate.exchange(
            "/api/v1/clients", HttpMethod.POST,
            new HttpEntity<>(req, auth()), ClientResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().id()).isNotNull();
        assertThat(resp.getBody().firstName()).isEqualTo("Bob");
    }

    // --- GET /clients/{id} ---

    @Test
    void getClient_returns_200_for_own_client() {
        ResponseEntity<ClientResponse> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId(), HttpMethod.GET,
            new HttpEntity<>(auth()), ClientResponse.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().id()).isEqualTo(client.getId());
    }

    @Test
    void getClient_returns_404_for_other_agency_client() {
        Agency other = agencyRepo.save(new Agency("Other", "TX"));
        Client otherClient = clientRepo.save(
            new Client(other.getId(), "Eve", "Other", LocalDate.of(1970, 1, 1)));

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + otherClient.getId(), HttpMethod.GET,
            new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- PATCH /clients/{id} ---

    @Test
    void updateClient_patches_first_name() {
        UpdateClientRequest req = new UpdateClientRequest(
            "Alicia", null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ClientResponse> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId(), HttpMethod.PATCH,
            new HttpEntity<>(req, auth()), ClientResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().firstName()).isEqualTo("Alicia");
    }

    // --- POST /clients/{id}/care-plans ---

    @Test
    void createCarePlan_returns_201_with_draft_status() {
        CreateCarePlanRequest req = new CreateCarePlanRequest(null);

        ResponseEntity<CarePlanResponse> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans", HttpMethod.POST,
            new HttpEntity<>(req, auth()), CarePlanResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody().status()).isEqualTo(CarePlanStatus.DRAFT);
        assertThat(resp.getBody().planVersion()).isEqualTo(1);
    }

    @Test
    void createCarePlan_increments_version_for_second_plan() {
        CreateCarePlanRequest req = new CreateCarePlanRequest(null);
        restTemplate.exchange("/api/v1/clients/" + client.getId() + "/care-plans",
            HttpMethod.POST, new HttpEntity<>(req, auth()), CarePlanResponse.class);
        ResponseEntity<CarePlanResponse> second = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans",
            HttpMethod.POST, new HttpEntity<>(req, auth()), CarePlanResponse.class);
        assertThat(second.getBody().planVersion()).isEqualTo(2);
    }

    // --- POST /clients/{id}/care-plans/{planId}/activate ---

    @Test
    void activateCarePlan_transitions_to_active() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));

        ResponseEntity<CarePlanResponse> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans/" + plan.getId() + "/activate",
            HttpMethod.POST, new HttpEntity<>(auth()), CarePlanResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().status()).isEqualTo(CarePlanStatus.ACTIVE);
    }

    @Test
    void activateCarePlan_returns_409_when_already_active() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        plan.activate();
        carePlanRepo.save(plan);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans/" + plan.getId() + "/activate",
            HttpMethod.POST, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // --- GET /clients/{id}/care-plans/active ---

    @Test
    void getActiveCarePlan_returns_200_with_active_plan() {
        CarePlan plan = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        plan.activate();
        carePlanRepo.save(plan);

        ResponseEntity<CarePlanResponse> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans/active",
            HttpMethod.GET, new HttpEntity<>(auth()), CarePlanResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().id()).isEqualTo(plan.getId());
        assertThat(resp.getBody().status()).isEqualTo(CarePlanStatus.ACTIVE);
        assertThat(resp.getBody().planVersion()).isEqualTo(1);
    }

    @Test
    void getActiveCarePlan_returns_404_when_no_plan_exists() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans/active",
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getActiveCarePlan_returns_404_after_plan_is_superseded() {
        CarePlan v1 = carePlanRepo.save(new CarePlan(client.getId(), agency.getId(), 1));
        v1.activate();
        carePlanRepo.save(v1);
        v1.supersede();
        carePlanRepo.save(v1);

        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/clients/" + client.getId() + "/care-plans/active",
            HttpMethod.GET, new HttpEntity<>(auth()), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
