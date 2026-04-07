package com.hcare.api.v1.users;

import com.hcare.AbstractIntegrationTest;
import com.hcare.api.v1.agencies.dto.RegisterAgencyRequest;
import com.hcare.api.v1.auth.dto.LoginRequest;
import com.hcare.api.v1.auth.dto.LoginResponse;
import com.hcare.api.v1.users.dto.InviteUserRequest;
import com.hcare.api.v1.users.dto.UpdateUserRoleRequest;
import com.hcare.api.v1.users.dto.UserResponse;
import com.hcare.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(statements = {
    "TRUNCATE TABLE feature_flags, agency_users, agencies RESTART IDENTITY CASCADE"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class UserControllerIT extends AbstractIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    private LoginResponse adminLogin;

    @BeforeEach
    void seed() {
        RegisterAgencyRequest reg = new RegisterAgencyRequest(
            "User IT Agency", "TX", "admin@userit.com", "Str0ngP@ss!");
        adminLogin = restTemplate.postForEntity(
            "/api/v1/agencies/register", reg, LoginResponse.class).getBody();
    }

    private HttpHeaders auth(LoginResponse login) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(login.token());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void listUsers_returns_only_agency_users() {
        ResponseEntity<List<UserResponse>> resp = restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET,
            new HttpEntity<>(auth(adminLogin)), new ParameterizedTypeReference<>() {});
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).email()).isEqualTo("admin@userit.com");
        assertThat(resp.getBody().get(0).role()).isEqualTo(UserRole.ADMIN.name());
    }

    @Test
    void inviteUser_creates_scheduler_and_they_can_log_in() {
        InviteUserRequest req = new InviteUserRequest("sched@userit.com", UserRole.SCHEDULER, "Temp1234!");
        ResponseEntity<UserResponse> invite = restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(req, auth(adminLogin)), UserResponse.class);
        assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invite.getBody().role()).isEqualTo(UserRole.SCHEDULER.name());

        ResponseEntity<LoginResponse> login = restTemplate.postForEntity(
            "/api/v1/auth/login",
            new LoginRequest("sched@userit.com", "Temp1234!"), LoginResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void inviteUser_returns_409_for_duplicate_email() {
        InviteUserRequest req = new InviteUserRequest("admin@userit.com", UserRole.SCHEDULER, "Temp1234!");
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(req, auth(adminLogin)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateUserRole_changes_role_to_admin() {
        InviteUserRequest inv = new InviteUserRequest("sched2@userit.com", UserRole.SCHEDULER, "Temp1234!");
        UserResponse invited = restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(inv, auth(adminLogin)), UserResponse.class).getBody();

        UpdateUserRoleRequest upd = new UpdateUserRoleRequest(UserRole.ADMIN);
        ResponseEntity<UserResponse> resp = restTemplate.exchange(
            "/api/v1/users/" + invited.id(), HttpMethod.PATCH,
            new HttpEntity<>(upd, auth(adminLogin)), UserResponse.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().role()).isEqualTo(UserRole.ADMIN.name());
    }

    @Test
    void updateUserRole_returns_409_when_demoting_last_admin() {
        // adminLogin is the only ADMIN — try to demote them to SCHEDULER
        UpdateUserRoleRequest req = new UpdateUserRoleRequest(UserRole.SCHEDULER);
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/users/" + adminLogin.userId(), HttpMethod.PATCH,
            new HttpEntity<>(req, auth(adminLogin)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteUser_removes_user() {
        InviteUserRequest inv = new InviteUserRequest("todelete@userit.com", UserRole.SCHEDULER, "Temp1234!");
        UserResponse invited = restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(inv, auth(adminLogin)), UserResponse.class).getBody();

        ResponseEntity<Void> del = restTemplate.exchange(
            "/api/v1/users/" + invited.id(), HttpMethod.DELETE,
            new HttpEntity<>(auth(adminLogin)), Void.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List<UserResponse>> list = restTemplate.exchange(
            "/api/v1/users", HttpMethod.GET,
            new HttpEntity<>(auth(adminLogin)), new ParameterizedTypeReference<>() {});
        assertThat(list.getBody()).hasSize(1); // only the original admin
    }

    @Test
    void deleteUser_returns_409_when_deleting_last_admin() {
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/users/" + adminLogin.userId(), HttpMethod.DELETE,
            new HttpEntity<>(auth(adminLogin)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void scheduler_cannot_invite_users() {
        InviteUserRequest inv = new InviteUserRequest("sched3@userit.com", UserRole.SCHEDULER, "Temp1234!");
        restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(inv, auth(adminLogin)), UserResponse.class);
        LoginResponse schedLogin = restTemplate.postForEntity("/api/v1/auth/login",
            new LoginRequest("sched3@userit.com", "Temp1234!"), LoginResponse.class).getBody();

        InviteUserRequest unauthorised = new InviteUserRequest("extra@userit.com", UserRole.SCHEDULER, "Temp1234!");
        ResponseEntity<String> resp = restTemplate.exchange(
            "/api/v1/users", HttpMethod.POST,
            new HttpEntity<>(unauthorised, auth(schedLogin)), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
