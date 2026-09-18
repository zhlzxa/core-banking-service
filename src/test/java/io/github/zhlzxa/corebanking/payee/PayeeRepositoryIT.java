package io.github.zhlzxa.corebanking.payee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** Verifies that the JPA mapping matches the Flyway schema and that the constraints hold. */
class PayeeRepositoryIT extends AbstractIntegrationIT {

    @Autowired
    private PayeeRepository payeeRepository;

    @Autowired
    private TestDataFactory data;

    private long alice;
    private long bob;

    @BeforeEach
    void seed() {
        alice = data.createCustomer("alice-sub");
        bob = data.createCustomer("bob-sub");
    }

    @Test
    void persistsAndReadsBackAllColumns() {
        Payee saved = payeeRepository.saveAndFlush(new Payee(alice, "Landlord", "123-456789-001", "004", "HKD"));

        Payee loaded = payeeRepository.findByIdAndOwnerId(saved.getId(), alice).orElseThrow();
        assertThat(loaded.getNickname()).isEqualTo("Landlord");
        assertThat(loaded.getBankCode()).isEqualTo("004");
        assertThat(loaded.getCurrency()).isEqualTo("HKD");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getVersion()).isZero();
    }

    @Test
    void lookupsAreScopedToTheOwner() {
        Payee saved = payeeRepository.saveAndFlush(new Payee(alice, "Landlord", "123", null, "HKD"));

        assertThat(payeeRepository.findByIdAndOwnerId(saved.getId(), bob)).isEmpty();
        assertThat(payeeRepository.findByOwnerIdOrderByNicknameAscIdAsc(bob)).isEmpty();
    }

    @Test
    void sameDestinationCannotBeSavedTwiceEvenWithoutBankCode() {
        payeeRepository.saveAndFlush(new Payee(alice, "Mum", "555", null, "HKD"));

        assertThat(payeeRepository.existsDestination(alice, "555", null)).isTrue();
        assertThat(payeeRepository.existsDestination(alice, "555", "004")).isFalse();
        assertThatThrownBy(() -> payeeRepository.saveAndFlush(new Payee(alice, "Mother", "555", null, "HKD")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentCustomersMaySaveTheSameDestination() {
        payeeRepository.saveAndFlush(new Payee(alice, "Shop", "777", "024", "HKD"));

        payeeRepository.saveAndFlush(new Payee(bob, "Shop", "777", "024", "HKD"));

        assertThat(payeeRepository.count()).isEqualTo(2);
    }
}
