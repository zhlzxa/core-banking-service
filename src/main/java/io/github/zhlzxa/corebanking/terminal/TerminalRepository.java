package io.github.zhlzxa.corebanking.terminal;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Looks up registered terminals by the identity they authenticate with. */
@Repository
public class TerminalRepository {

    private final JdbcClient jdbc;

    public TerminalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Terminal> findByExternalIdentity(String issuer, String subject) {
        return jdbc.sql("""
                        SELECT id, identity_issuer, identity_subject, branch_code, status
                        FROM terminals
                        WHERE identity_issuer = :issuer AND identity_subject = :subject
                        """)
                .param("issuer", issuer)
                .param("subject", subject)
                .query((rs, rowNum) -> new Terminal(
                        rs.getString("id"),
                        rs.getString("identity_issuer"),
                        rs.getString("identity_subject"),
                        rs.getString("branch_code"),
                        "ACTIVE".equals(rs.getString("status"))))
                .optional();
    }
}
