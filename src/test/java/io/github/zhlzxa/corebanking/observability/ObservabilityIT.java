package io.github.zhlzxa.corebanking.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import io.github.zhlzxa.corebanking.web.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Probes, metrics and logs as seen by the platform that runs the service. */
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityIT extends AbstractIntegrationIT {

    private static final String APP = "application=\"core-banking-service\"";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(100, alice, "HKD", "1000.00");
        data.createAccount(200, bob, "HKD", "0.00");
        data.createInternalAccount("FPS-CLEARING-HKD", "HKD");
    }

    @Test
    void probesNeedNoTokenAndRevealNoDetails() {
        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            MvcTestResult result = mvc.get().uri(probe).exchange();

            assertThat(result).hasStatus(HttpStatus.OK);
            assertThat(result).bodyJson().isEqualTo("{\"status\": \"UP\"}");
        }
    }

    @Test
    void infoPublishesTheBuildVersion() {
        assertThat(mvc.get().uri("/actuator/info").exchange())
                .hasStatus(HttpStatus.OK)
                .bodyJson()
                .extractingPath("$.build.artifact")
                .isEqualTo("core-banking-service");
    }

    @Test
    void noOtherActuatorEndpointIsExposed() {
        String token = TestJwts.token("alice-sub", "bank.accounts.read");
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
            assertThat(mvc.get()
                            .uri(endpoint)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .exchange())
                    .hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void metricsExposeBusinessOutcomesFpsCallsAndBacklogs() {
        transfer("obs-1", "10.00");
        payFps("obs-2", "TIMEOUT-1");

        MvcTestResult result = mvc.get().uri("/actuator/prometheus").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result)
                .bodyText()
                .contains("corebanking_business_events_total{action=\"TRANSFER_COMPLETED\"," + APP
                        + ",outcome=\"SUCCESS\",reason=\"none\"}")
                .contains("corebanking_business_events_total{action=\"FPS_PAYMENT_OUTCOME_UNKNOWN\"")
                .contains("corebanking_fps_calls_seconds_count{" + APP + ",operation=\"send\",outcome=\"no_response\"}")
                .contains("corebanking_fps_payments_processing{" + APP + "} 1.0")
                .contains("corebanking_fps_payments_needs_investigation{" + APP + "} 0.0")
                .contains("corebanking_fps_payments_processing_oldest_age_seconds{" + APP + "}")
                .contains("corebanking_outbox_unpublished{" + APP + "} 1.0")
                .contains("corebanking_outbox_oldest_unpublished_age_seconds{" + APP + "}")
                .contains("http_server_requests_seconds_bucket{");
    }

    @Test
    void logsAreStructuredCarryTheCorrelationIdAndContainNoSecrets(CapturedOutput output) throws Exception {
        String transferToken = TestJwts.token("alice-sub", "bank.transfer");
        String strangerToken = TestJwts.token("mallory-sub", "bank.transfer");

        assertThat(mvc.post()
                        .uri("/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + transferToken)
                        .header(CorrelationId.HEADER, "obs-corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody("obs-1", "10.00"))
                        .exchange())
                .hasStatus(HttpStatus.CREATED);
        payFps("obs-2", "TIMEOUT-55501234");
        mvc.get()
                .uri("/accounts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken)
                .exchange();

        String completedLine = output.getOut()
                .lines()
                .filter(line -> line.contains("Transfer completed"))
                .findFirst()
                .orElseThrow();
        JsonNode event = jsonMapper.readTree(completedLine);
        assertThat(event.get("correlationId").asString()).isEqualTo("obs-corr-1");
        assertThat(event.get("level").asString()).isEqualTo("INFO");
        assertThat(output.getAll())
                .contains("No response from FPS")
                .contains("Rejected token")
                .doesNotContain(transferToken)
                .doesNotContain(strangerToken)
                .doesNotContain("55501234");
    }

    private void transfer(String requestId, String amount) {
        assertThat(mvc.post()
                        .uri("/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferBody(requestId, amount))
                        .exchange())
                .hasStatus(HttpStatus.CREATED);
    }

    private void payFps(String requestId, String creditorAccount) {
        assertThat(mvc.post()
                        .uri("/fps/payments")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId": "%s", "fromAccountId": 100, "creditorBankCode": "004",
                                 "creditorAccount": "%s", "amount": "20.00", "currency": "HKD"}
                                """.formatted(requestId, creditorAccount))
                        .exchange())
                .hasStatus(HttpStatus.ACCEPTED);
    }

    private static String transferBody(String requestId, String amount) {
        return """
                {"requestId": "%s", "fromAccountId": 100, "toAccountId": 200,
                 "amount": "%s", "currency": "HKD"}
                """.formatted(requestId, amount);
    }
}
