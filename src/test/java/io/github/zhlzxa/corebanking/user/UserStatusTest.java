package io.github.zhlzxa.corebanking.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserStatusTest {

    @Test
    void onlyActiveUsersCanAuthenticate() {
        assertThat(UserStatus.ACTIVE.canAuthenticate()).isTrue();
        assertThat(UserStatus.LOCKED.canAuthenticate()).isFalse();
        assertThat(UserStatus.DISABLED.canAuthenticate()).isFalse();
    }
}
