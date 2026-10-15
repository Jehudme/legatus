package dev.legatus.server.security;

import java.sql.Connection;
import java.sql.SQLException;
import org.h2.api.Trigger;

/**
 * H2 Database Trigger enforcing statutory Write-Once-Read-Many (WORM) non-deletability.
 * Aborts any SQL DELETE or UPDATE operation on the {@code audit_logs} table immediately with a fatal exception.
 * <p>
 * Complies with {@code server-spec.md} Section 5.3.1 (SOC 2 Type II, ISO 27001, HIPAA, GDPR).
 */
public class WormAuditLogTrigger implements Trigger {

    public static final String VIOLATION_MESSAGE =
        "Statutory Violation: Audit log records are legally immutable under SOC 2 / ISO 27001 / HIPAA / GDPR and cannot be deleted or modified.";

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        throw new SQLException(VIOLATION_MESSAGE, "23000"); // SQLState 23000: integrity constraint violation
    }
}
