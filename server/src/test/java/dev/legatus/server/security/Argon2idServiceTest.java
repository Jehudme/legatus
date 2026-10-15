package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.legatus.server.config.LegatusProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Argon2idServiceTest {

    private Argon2idService argon2idService;

    @BeforeEach
    void setUp() {
        LegatusProperties properties = new LegatusProperties();
        // Fast configuration for unit tests while exercising full Argon2id logic
        var argon2 = properties.getSecurity().getArgon2();
        argon2.setMemoryCostKb(4096);
        argon2.setIterations(2);
        argon2.setParallelism(2);
        argon2.setPoolSize(4);
        argon2idService = new Argon2idService(properties);
    }

    @AfterEach
    void tearDown() {
        argon2idService.shutdown();
    }

    @Test
    @DisplayName("Should generate standard Argon2id Modular Crypt Format hash and verify matching secret")
    void testHashAndVerifySuccess() {
        String secret = "SuperSecret_Worker_Token_1234!";
        String hash = argon2idService.hash(secret);

        assertThat(hash).isNotNull().startsWith("$argon2id$");
        assertThat(argon2idService.verify(secret, hash)).isTrue();
    }

    @Test
    @DisplayName("Should reject incorrect secret or corrupted hash")
    void testVerifyFailure() {
        String secret = "ValidSecretToken";
        String hash = argon2idService.hash(secret);

        assertThat(argon2idService.verify("WrongSecretToken", hash)).isFalse();
        assertThat(argon2idService.verify(null, hash)).isFalse();
        assertThat(argon2idService.verify(secret, null)).isFalse();
        assertThat(argon2idService.verify(secret, "corrupted_hash_format")).isFalse();
    }

    @Test
    @DisplayName("Should reject null secret during hash computation")
    void testHashNullSecret() {
        assertThatThrownBy(() -> argon2idService.hash(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Secret cannot be null");
    }

    @Test
    @DisplayName("Should safely handle concurrent hashing operations without carrier pool starvation")
    void testConcurrentHashing() throws Exception {
        int jobCount = 8;
        ExecutorService clientPool = Executors.newFixedThreadPool(jobCount);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < jobCount; i++) {
                final String secret = "Secret_Worker_Concurrency_" + i;
                tasks.add(() -> {
                    String hash = argon2idService.hash(secret);
                    return argon2idService.verify(secret, hash);
                });
            }

            List<Future<Boolean>> futures = clientPool.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertThat(future.get()).isTrue();
            }
        } finally {
            clientPool.shutdown();
        }
    }
}
