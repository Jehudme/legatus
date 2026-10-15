package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.Account;
import dev.legatus.server.service.dto.AccountDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Account} and its DTO {@link AccountDTO}.
 */
@Mapper(componentModel = "spring")
public interface AccountMapper extends EntityMapper<AccountDTO, Account> {}
