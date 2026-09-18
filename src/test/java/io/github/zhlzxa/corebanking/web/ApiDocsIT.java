package io.github.zhlzxa.corebanking.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.common.error.ErrorCode;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The generated API description documents every operation, its errors and its security. */
class ApiDocsIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void describesOperationsWithTheirErrorsAndBearerSecurity() throws Exception {
        MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        String body = result.getResponse().getContentAsString();
        JsonNode api = jsonMapper.readTree(body);

        JsonNode transfer = api.at("/paths/~1transfers/post");
        assertThat(transfer.at("/summary").asString()).contains("idempotent");
        assertThat(transfer.at("/responses/201").isMissingNode()).isFalse();
        assertThat(transfer.at("/responses/409/description").asString())
                .contains("INSUFFICIENT_BALANCE", "IDEMPOTENCY_KEY_REUSED");
        assertThat(transfer.at("/responses/401/content/application~1problem+json/schema/$ref")
                        .asString())
                .isEqualTo("#/components/schemas/Problem");
        assertThat(transfer.at("/parameters").isMissingNode()
                        || transfer.at("/parameters").isEmpty())
                .as("the authenticated principal is not a request parameter")
                .isTrue();

        assertThat(api.at("/paths/~1fps~1payments/post/responses/202").isMissingNode())
                .isFalse();
        assertThat(api.at("/paths/~1teller~1approvals~1{approvalId}~1approve/post/responses/403/description")
                        .asString())
                .contains("FOUR_EYES_REQUIRED");
        assertThat(api.at("/components/securitySchemes/bearer/scheme").asString())
                .isEqualTo("bearer");
        assertThat(api.at("/security/0/bearer").isArray()).isTrue();
        assertThat(api.at("/components/schemas/Problem/properties/code/enum").size())
                .isEqualTo(ErrorCode.values().length);
    }
}
