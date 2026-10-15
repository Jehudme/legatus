package dev.legatus.server.security;

import dev.legatus.server.config.LegatusProperties;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Bounded carrier pool Argon2id credential verification service.
 * <p>
 * Enforces memory safety by pinning heavy Argon2id computations (64MB RAM each)
 * to a bounded thread pool (default 4 threads), capping peak RAM at 256MB
 * regardless of the number of concurrent Loom Virtual Threads (server-spec.md Section 4.1).
 */
@Service
public class Argon2idService {

    private static final Logger LOG = LoggerFactory.getLogger(Argon2idService.class);

    private final ExecutorService carrierPool;
    private final Argon2PasswordEncoder encoder;

    public Argon2idService(LegatusProperties properties) {
        var argon2Props = properties.getSecurity().getArgon2();
        int poolSize = argon2Props.getPoolSize();
        this.carrierPool = Executors.newFixedThreadPool(poolSize, runnable -> {
            Thread thread = new Thread(runnable, "legatus-argon2-carrier");
            thread.setDaemon(true);
            return thread;
        });

        this.encoder = new Argon2PasswordEncoder(
            16, // saltLength in bytes
            32, // hashLength in bytes
            argon2Props.getParallelism(),
            argon2Props.getMemoryCostKb(),
            argon2Props.getIterations()
        );
        LOG.info(
            "Initialized Argon2idService with bounded carrier pool size={}, memory={}KB, iterations={}, parallelism={}",
            poolSize,
            argon2Props.getMemoryCostKb(),
            argon2Props.getIterations(),
            argon2Props.getParallelism()
        );
    }

    /**
     * Hashes raw secret on the bounded carrier pool.
     *
     * @param rawSecret The plaintext secret to hash
     * @return Standard Argon2id modular crypt format string
     */
    public String hash(CharSequence rawSecret) {
        if (rawSecret == null) {
            throw new IllegalArgumentException("Secret cannot be null");
        }
        return CompletableFuture.supplyAsync(() -> encoder.encode(rawSecret), carrierPool).join();
    }

    /**
     * Verifies raw secret against encoded Argon2id hash on the bounded carrier pool.
     *
     * @param rawSecret The plaintext secret
     * @param encodedHash The stored Argon2id hash
     * @return true if secret matches hash, false otherwise
     */
    public boolean verify(CharSequence rawSecret, String encodedHash) {
        if (encodedHash == null || rawSecret == null) {
            return false;
        }
        return CompletableFuture.supplyAsync(() -> encoder.matches(rawSecret, encodedHash), carrierPool).join();
    }

    @PreDestroy
    public void shutdown() {
        carrierPool.shutdown();
        try {
            if (!carrierPool.awaitTermination(2, TimeUnit.SECONDS)) {
                carrierPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            carrierPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
