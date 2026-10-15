package dev.legatus.server.service.mapper;

import static dev.legatus.server.domain.ComputeTaskAsserts.*;
import static dev.legatus.server.domain.ComputeTaskTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ComputeTaskMapperTest {

    private ComputeTaskMapper computeTaskMapper;

    @BeforeEach
    void setUp() {
        computeTaskMapper = new ComputeTaskMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getComputeTaskSample1();
        var actual = computeTaskMapper.toEntity(computeTaskMapper.toDto(expected));
        assertComputeTaskAllPropertiesEquals(expected, actual);
    }
}
