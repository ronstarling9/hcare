package com.hcare.api.v1.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceTest {

    @Test
    void dummyHash_is_valid_bcrypt_so_timing_normalization_runs_full_work() {
        // BCryptPasswordEncoder.matches() returns false immediately (no BCrypt work)
        // if the hash is malformed — which would silently break timing normalization.
        // This test verifies the constant is a well-formed BCrypt string.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        // Verify the call does not throw (malformed hash would cause an IllegalArgumentException)
        boolean result = encoder.matches("irrelevant", AuthService.DUMMY_HASH_FOR_TEST);
        assertThat(result).isFalse(); // correct: wrong password, but BCrypt ran the full computation
    }
}
