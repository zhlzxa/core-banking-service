package io.github.zhlzxa.corebanking.payee;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data repository for payees.
 *
 * <p>Services must use the owner-scoped finders declared here. The inherited {@code findById}
 * ignores ownership and would allow one customer to read another customer's payee.
 */
public interface PayeeRepository extends JpaRepository<Payee, Long> {

    List<Payee> findByOwnerIdOrderByNicknameAscIdAsc(long ownerId);

    Optional<Payee> findByIdAndOwnerId(long id, long ownerId);

    @Query("""
            select count(p) > 0 from Payee p
            where p.ownerId = :ownerId
              and p.accountNumber = :accountNumber
              and ((:bankCode is null and p.bankCode is null) or p.bankCode = :bankCode)
            """)
    boolean existsDestination(long ownerId, String accountNumber, @Nullable String bankCode);
}
