package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.ComputeTask;
import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.domain.WorkerNode;
import dev.legatus.server.service.dto.ComputeTaskDTO;
import dev.legatus.server.service.dto.SolixFunctionDTO;
import dev.legatus.server.service.dto.WorkerNodeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ComputeTask} and its DTO {@link ComputeTaskDTO}.
 */
@Mapper(componentModel = "spring")
public interface ComputeTaskMapper extends EntityMapper<ComputeTaskDTO, ComputeTask> {
    @Mapping(target = "function", source = "function", qualifiedByName = "solixFunctionName")
    @Mapping(target = "assignedWorker", source = "assignedWorker", qualifiedByName = "workerNodeWorkerId")
    ComputeTaskDTO toDto(ComputeTask s);

    @Named("solixFunctionName")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "functionId", source = "functionId")
    @Mapping(target = "name", source = "name")
    SolixFunctionDTO toDtoSolixFunctionName(SolixFunction solixFunction);

    @Named("workerNodeWorkerId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "workerId", source = "workerId")
    WorkerNodeDTO toDtoWorkerNodeWorkerId(WorkerNode workerNode);
}
