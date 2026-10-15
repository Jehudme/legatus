package dev.legatus.server.domain;

import static dev.legatus.server.domain.AccountTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Account.class);
        Account account1 = getAccountSample1();
        Account account2 = new Account();
        assertThat(account1).isNotEqualTo(account2);

        account2.setAccountId(account1.getAccountId());
        assertThat(account1).isEqualTo(account2);

        account2 = getAccountSample2();
        assertThat(account1).isNotEqualTo(account2);
    }
}
