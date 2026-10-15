package dev.legatus.server.service.mapper;

import static dev.legatus.server.domain.AccountAsserts.*;
import static dev.legatus.server.domain.AccountTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountMapperTest {

    private AccountMapper accountMapper;

    @BeforeEach
    void setUp() {
        accountMapper = new AccountMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAccountSample1();
        var actual = accountMapper.toEntity(accountMapper.toDto(expected));
        assertAccountAllPropertiesEquals(expected, actual);
    }
}
