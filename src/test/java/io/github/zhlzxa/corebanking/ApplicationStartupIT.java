package io.github.zhlzxa.corebanking;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class ApplicationStartupIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void connectsToTheSamePostgresMajorVersionAsProduction() {
        String serverVersion =
                jdbcClient.sql("SHOW server_version").query(String.class).single();

        assertThat(serverVersion).startsWith("17.");
    }
}
