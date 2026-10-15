package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.SecretEntry;
import dev.legatus.server.service.dto.SecretEntryDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link SecretEntry} and its DTO {@link SecretEntryDTO}.
 */
@Mapper(componentModel = "spring")
public interface SecretEntryMapper extends EntityMapper<SecretEntryDTO, SecretEntry> {}
