package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.ExecutionProfile;
import dev.legatus.server.service.dto.ExecutionProfileDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link ExecutionProfile} and its DTO {@link ExecutionProfileDTO}.
 */
@Mapper(componentModel = "spring")
public interface ExecutionProfileMapper extends EntityMapper<ExecutionProfileDTO, ExecutionProfile> {}
