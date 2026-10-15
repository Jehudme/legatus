package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.service.dto.WorkerNodeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link WorkerNode} and its DTO {@link WorkerNodeDTO}.
 */
@Mapper(componentModel = "spring")
public interface WorkerNodeMapper extends EntityMapper<WorkerNodeDTO, WorkerNode> {}
