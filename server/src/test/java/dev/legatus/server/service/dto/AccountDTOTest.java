package dev.legatus.server.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(AccountDTO.class);
        AccountDTO accountDTO1 = new AccountDTO();
        accountDTO1.setAccountId(UUID.randomUUID());
        AccountDTO accountDTO2 = new AccountDTO();
        assertThat(accountDTO1).isNotEqualTo(accountDTO2);
        accountDTO2.setAccountId(accountDTO1.getAccountId());
        assertThat(accountDTO1).isEqualTo(accountDTO2);
        accountDTO2.setAccountId(UUID.randomUUID());
        assertThat(accountDTO1).isNotEqualTo(accountDTO2);
        accountDTO1.setAccountId(null);
        assertThat(accountDTO1).isNotEqualTo(accountDTO2);
    }
}
