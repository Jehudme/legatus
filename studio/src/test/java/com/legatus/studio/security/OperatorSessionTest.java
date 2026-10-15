package com.legatus.studio.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class OperatorSessionTest {

    private OperatorSession session;

    @BeforeEach
    void setUp() {
        session = OperatorSession.getInstance();
        session.clearSession();
    }

    @AfterEach
    void tearDown() {
        session.clearSession();
    }

    @Test
    void testInitialStateUnauthenticated() {
        assertFalse(session.isAuthenticated());
        assertTrue(session.isTokenExpired());
        assertNull(session.getAccessToken());
        assertNull(session.getUsername());
    }

    @Test
    void testActiveSessionAuthentication() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        session.setSession("jwt.access.token", "jwt.refresh.token", future, "ops_lead", "acc-001", "CLUSTER_ADMIN");

        assertTrue(session.isAuthenticated());
        assertFalse(session.isTokenExpired());
        assertEquals("ops_lead", session.getUsername());
        assertEquals("acc-001", session.getAccountId());
        assertEquals("CLUSTER_ADMIN", session.getRole());
        assertTrue(session.hasRole("CLUSTER_ADMIN"));
        assertTrue(session.hasRole("OPERATOR"));
        assertTrue(session.hasRole("AUDITOR"));
    }

    @Test
    void testExpiredSession() {
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        session.setSession("jwt.expired", "jwt.refresh", past, "ops_lead", "acc-001", "OPERATOR");

        assertFalse(session.isAuthenticated());
        assertTrue(session.isTokenExpired());
    }

    @Test
    void testUpdateAccessToken() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        session.setSession("jwt.old", "jwt.refresh", future, "ops_lead", "acc-001", "OPERATOR");

        Instant evenLater = future.plus(1, ChronoUnit.HOURS);
        session.updateAccessToken("jwt.new", evenLater);

        assertEquals("jwt.new", session.getAccessToken());
        assertEquals(evenLater, session.getExpiresAt());
    }

    @Test
    void testClearSession() {
        Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
        session.setSession("jwt.access.token", "jwt.refresh.token", future, "ops_lead", "acc-001", "OPERATOR");

        session.clearSession();
        assertFalse(session.isAuthenticated());
        assertNull(session.getAccessToken());
        assertNull(session.getRefreshToken());
        assertNull(session.getUsername());
    }
}
