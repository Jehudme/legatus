package dev.legatus.server.service.mapper;

import static dev.legatus.server.domain.SolixFunctionAsserts.*;
import static dev.legatus.server.domain.SolixFunctionTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SolixFunctionMapperTest {

    private SolixFunctionMapper solixFunctionMapper;

    @BeforeEach
    void setUp() {
        solixFunctionMapper = new SolixFunctionMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getSolixFunctionSample1();
        var actual = solixFunctionMapper.toEntity(solixFunctionMapper.toDto(expected));
        assertSolixFunctionAllPropertiesEquals(expected, actual);
    }
}
