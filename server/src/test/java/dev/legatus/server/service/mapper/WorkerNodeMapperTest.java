package dev.legatus.server.service.mapper;

import static dev.legatus.server.domain.WorkerNodeAsserts.*;
import static dev.legatus.server.domain.WorkerNodeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkerNodeMapperTest {

    private WorkerNodeMapper workerNodeMapper;

    @BeforeEach
    void setUp() {
        workerNodeMapper = new WorkerNodeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getWorkerNodeSample1();
        var actual = workerNodeMapper.toEntity(workerNodeMapper.toDto(expected));
        assertWorkerNodeAllPropertiesEquals(expected, actual);
    }
}
