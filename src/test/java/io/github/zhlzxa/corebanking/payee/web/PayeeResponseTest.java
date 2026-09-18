package io.github.zhlzxa.corebanking.payee.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayeeResponseTest {

    @Test
    void showsOnlyTheLastFourCharacters() {
        assertThat(PayeeResponse.mask("123-456789-001")).isEqualTo("**********-001");
    }

    @Test
    void neverRevealsMoreThanHalfOfAShortNumber() {
        assertThat(PayeeResponse.mask("123456")).isEqualTo("****56");
        assertThat(PayeeResponse.mask("1234")).isEqualTo("****");
        assertThat(PayeeResponse.mask("1")).isEqualTo("*");
    }
}
