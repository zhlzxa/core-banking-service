package io.github.zhlzxa.corebanking.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcUserRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestDataFactory data;

    @Test
    void findsUserByIssuerAndSubject() {
        long id = data.createUser("alice-sub", "CUSTOMER", "LOCKED");

        assertThat(userRepository.findByExternalIdentity(TestDataFactory.ISSUER, "alice-sub"))
                .hasValueSatisfying(user -> {
                    assertThat(user.id()).isEqualTo(id);
                    assertThat(user.role()).isEqualTo(UserRole.CUSTOMER);
                    assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
                });
    }

    @Test
    void sameSubjectFromAnotherIssuerIsADifferentIdentity() {
        data.createCustomer("alice-sub");

        assertThat(userRepository.findByExternalIdentity("https://other-issuer.test", "alice-sub"))
                .isEmpty();
    }

    @Test
    void externalIdentityIsUnique() {
        data.createCustomer("alice-sub");

        assertThatThrownBy(() -> data.createCustomer("alice-sub"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_users_external_identity");
    }

    @Test
    void unknownRoleIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> data.createUser("mallory", "SUPERUSER", "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_role");
    }
}
