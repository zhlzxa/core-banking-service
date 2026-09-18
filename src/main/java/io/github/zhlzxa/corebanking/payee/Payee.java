package io.github.zhlzxa.corebanking.payee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * A destination saved by a customer for future payments.
 *
 * <p>The owner is referenced by id rather than by an entity association: a payee is managed only
 * together with its owner's identity, never navigated from the user, so an association would add
 * lazy-loading behaviour without any benefit.
 *
 * <p>Equality is identity-based (the {@code Object} default). Overriding {@code equals} on a mutable
 * entity with a generated id breaks hash-based collections across persist.
 */
@Entity
@Table(name = "payees")
public class Payee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "account_number", nullable = false, length = 34, updatable = false)
    private String accountNumber;

    @Column(name = "bank_code", length = 20, updatable = false)
    private @Nullable String bankCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    /** Required by JPA. */
    protected Payee() {}

    public Payee(long ownerId, String nickname, String accountNumber, @Nullable String bankCode, String currency) {
        this.ownerId = ownerId;
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.accountNumber = Objects.requireNonNull(accountNumber, "accountNumber");
        this.bankCode = bankCode;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /** Changes the display name. Persisted automatically when the transaction commits. */
    public void rename(String newNickname) {
        this.nickname = Objects.requireNonNull(newNickname, "newNickname");
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public @Nullable String getBankCode() {
        return bankCode;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
