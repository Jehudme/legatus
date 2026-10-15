package dev.legatus.server.service.mapper;

import static dev.legatus.server.domain.SecretEntryAsserts.*;
import static dev.legatus.server.domain.SecretEntryTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecretEntryMapperTest {

    private SecretEntryMapper secretEntryMapper;

    @BeforeEach
    void setUp() {
        secretEntryMapper = new SecretEntryMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getSecretEntrySample1();
        var actual = secretEntryMapper.toEntity(secretEntryMapper.toDto(expected));
        assertSecretEntryAllPropertiesEquals(expected, actual);
    }
}
