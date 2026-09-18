package io.github.zhlzxa.corebanking.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BankUser> findByExternalIdentity(String issuer, String subject) {
        return jdbc.sql("""
                        SELECT id, identity_issuer, identity_subject, display_name, role, status
                        FROM users
                        WHERE identity_issuer = :issuer AND identity_subject = :subject
                        """)
                .param("issuer", issuer)
                .param("subject", subject)
                .query(JdbcUserRepository::mapUser)
                .optional();
    }

    private static BankUser mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new BankUser(
                rs.getLong("id"),
                rs.getString("identity_issuer"),
                rs.getString("identity_subject"),
                rs.getString("display_name"),
                UserRole.valueOf(rs.getString("role")),
                UserStatus.valueOf(rs.getString("status")));
    }
}
