package dev.legatus.server.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates compiled guest bytecode against banned capabilities and security invariants (ADR-0004, ADR-0009).
 * <p>
 * Under the Pure In-Memory Compute Sandbox Model:
 * <ul>
 *   <li>Host process spawning ({@code solix.system.Process}) is strictly prohibited.</li>
 *   <li>All disk and host filesystem I/O ({@code solix.io.filesystem}, {@code solix.io.FileStream}) is strictly prohibited.</li>
 * </ul>
 * Any bytecode containing references to these packages or symbols is rejected with {@link BytecodeSecurityException}.
 */
@Service
public class BytecodeSecurityValidator {

    private static final Logger LOG = LoggerFactory.getLogger(BytecodeSecurityValidator.class);

    private static final List<String> BANNED_PATTERNS = List.of(
        "solix.system.Process",
        "solix/system/Process",
        "solix.io.filesystem",
        "solix/io/filesystem",
        "solix.io.FileStream",
        "solix/io/FileStream",
        "solix.io.FileMode",
        "solix/io/FileMode",
        "Process_native_start",
        "solix_system_Process",
        "path_native_combine",
        "file_native_read",
        "file_native_write",
        "dir_native_create"
    );

    /**
     * Inspects a bytecode payload for prohibited imports or native calls.
     *
     * @param bytecode raw bytecode bytes
     * @throws BytecodeSecurityException if a banned symbol or capability is detected
     */
    public void validateBytecode(byte[] bytecode) {
        if (bytecode == null || bytecode.length == 0) {
            return;
        }

        // Convert bytecode bytes to string using ISO-8859-1 to safely search byte patterns without decoding errors
        String content = new String(bytecode, StandardCharsets.ISO_8859_1);

        for (String banned : BANNED_PATTERNS) {
            if (content.contains(banned)) {
                LOG.warn("Bytecode rejected: detected prohibited symbol '{}' (ADR-0009)", banned);
                throw new BytecodeSecurityException(
                    banned,
                    "Prohibited capability detected in bytecode: '" + banned +
                    "'. Host process execution and filesystem I/O are strictly banned under the zero-disk compute model (ADR-0009)."
                );
            }
        }
    }
}
