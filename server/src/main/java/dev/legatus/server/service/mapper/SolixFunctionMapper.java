package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.SolixFunction;
import dev.legatus.server.service.dto.SolixFunctionDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link SolixFunction} and its DTO {@link SolixFunctionDTO}.
 */
@Mapper(componentModel = "spring")
public interface SolixFunctionMapper extends EntityMapper<SolixFunctionDTO, SolixFunction> {}
