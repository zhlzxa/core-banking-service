package io.github.zhlzxa.corebanking.account;

import static io.github.zhlzxa.corebanking.account.AccountStatus.ACTIVE;
import static io.github.zhlzxa.corebanking.account.AccountStatus.CLOSED;
import static io.github.zhlzxa.corebanking.account.AccountStatus.DORMANT;
import static io.github.zhlzxa.corebanking.account.AccountStatus.FROZEN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountStatusTest {

    @Test
    void onlyActiveAccountsCanSendMoney() {
        assertThat(ACTIVE.canBeDebited()).isTrue();
        assertThat(FROZEN.canBeDebited()).isFalse();
        assertThat(DORMANT.canBeDebited()).isFalse();
        assertThat(CLOSED.canBeDebited()).isFalse();
    }

    @Test
    void everyAccountExceptAClosedOneCanReceiveMoney() {
        assertThat(ACTIVE.canBeCredited()).isTrue();
        assertThat(FROZEN.canBeCredited()).isTrue();
        assertThat(DORMANT.canBeCredited()).isTrue();
        assertThat(CLOSED.canBeCredited()).isFalse();
    }

    @Test
    void closedIsTerminal() {
        for (AccountStatus target : AccountStatus.values()) {
            assertThat(CLOSED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void frozenAndDormantAccountsCanOnlyBeReactivatedOrClosed() {
        assertThat(FROZEN.canTransitionTo(ACTIVE)).isTrue();
        assertThat(FROZEN.canTransitionTo(CLOSED)).isTrue();
        assertThat(FROZEN.canTransitionTo(DORMANT)).isFalse();
        assertThat(DORMANT.canTransitionTo(FROZEN)).isFalse();
        assertThat(ACTIVE.canTransitionTo(ACTIVE)).isFalse();
    }
}
