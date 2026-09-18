package io.github.zhlzxa.corebanking.user;

import java.util.Optional;

public interface UserRepository {

    /** Finds the user registered for an external identity, identified by issuer and subject. */
    Optional<BankUser> findByExternalIdentity(String issuer, String subject);
}
