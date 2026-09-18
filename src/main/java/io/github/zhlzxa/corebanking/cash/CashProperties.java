package io.github.zhlzxa.corebanking.cash;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cash handling policy.
 *
 * @param approvalThreshold withdrawals of this amount or more need a second teller's approval; the
 *     same figure applies in every currency, which is a simplification of per-currency limits
 * @param approvalValidity how long a request waits for a decision before it expires
 * @param atmWithdrawalLimit the largest amount a terminal may pay out in one withdrawal
 */
@Validated
@ConfigurationProperties("corebanking.cash")
public record CashProperties(
        @NotNull @Positive BigDecimal approvalThreshold,
        @NotNull Duration approvalValidity,
        @NotNull @Positive BigDecimal atmWithdrawalLimit) {}
