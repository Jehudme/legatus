package dev.legatus.server.service.mapper;

import dev.legatus.server.domain.Account;
import dev.legatus.server.service.dto.AccountDTO;
import java.util.ArrayList;
import java.util.List;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Account} and its DTO {@link AccountDTO}.
 */
@Mapper(componentModel = "spring")
public interface AccountMapper extends EntityMapper<AccountDTO, Account> {

    @Override
    default List<Account> toEntity(List<AccountDTO> dtoList) {
        if (dtoList == null) {
            return null;
        }
        List<Account> list = new ArrayList<>(dtoList.size());
        for (AccountDTO accountDTO : dtoList) {
            list.add(toEntity(accountDTO));
        }
        return list;
    }

    @Override
    default List<AccountDTO> toDto(List<Account> entityList) {
        if (entityList == null) {
            return null;
        }
        List<AccountDTO> list = new ArrayList<>(entityList.size());
        for (Account account : entityList) {
            list.add(toDto(account));
        }
        return list;
    }
}
