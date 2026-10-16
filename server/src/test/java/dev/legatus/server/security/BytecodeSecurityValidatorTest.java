package dev.legatus.server.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BytecodeSecurityValidatorTest {

    private BytecodeSecurityValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BytecodeSecurityValidator();
    }

    @Test
    @DisplayName("Should accept benign pure-memory bytecode")
    void shouldAcceptBenignBytecode() {
        byte[] safeBytecode = "SOLIX_BC\0some_safe_code\0solix.collections.List\0solix.core.String".getBytes(StandardCharsets.ISO_8859_1);
        validator.validateBytecode(safeBytecode);
    }

    @Test
    @DisplayName("Should accept empty or null bytecode safely")
    void shouldAcceptNullOrEmptyBytecode() {
        validator.validateBytecode(null);
        validator.validateBytecode(new byte[0]);
    }

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    @DisplayName("Should reject bytecode containing banned capability or symbol")
    void shouldRejectBannedSymbols(String bannedSymbol) {
        byte[] maliciousBytecode = ("HEADER\0constant_pool\0" + bannedSymbol + "\0opcode_payload").getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> validator.validateBytecode(maliciousBytecode))
            .isInstanceOf(BytecodeSecurityException.class)
            .hasMessageContaining("Prohibited capability detected in bytecode: '" + bannedSymbol + "'")
            .satisfies(ex -> {
                BytecodeSecurityException bse = (BytecodeSecurityException) ex;
                assertThat(bse.getProhibitedSymbol()).isEqualTo(bannedSymbol);
            });
    }
}
