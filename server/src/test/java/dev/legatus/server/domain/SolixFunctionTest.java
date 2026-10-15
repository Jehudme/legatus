package dev.legatus.server.domain;

import static dev.legatus.server.domain.SolixFunctionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.legatus.server.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class SolixFunctionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(SolixFunction.class);
        SolixFunction solixFunction1 = getSolixFunctionSample1();
        SolixFunction solixFunction2 = new SolixFunction();
        assertThat(solixFunction1).isNotEqualTo(solixFunction2);

        solixFunction2.setFunctionId(solixFunction1.getFunctionId());
        assertThat(solixFunction1).isEqualTo(solixFunction2);

        solixFunction2 = getSolixFunctionSample2();
        assertThat(solixFunction1).isNotEqualTo(solixFunction2);
    }
}
