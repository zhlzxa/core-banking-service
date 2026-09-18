package io.github.zhlzxa.corebanking.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AccountQueryIT extends AbstractIntegrationIT {

    private static final long ALICE_HKD = 100;
    private static final long ALICE_SAVINGS = 101;
    private static final long BOB_HKD = 200;
    private static final Instant T0 = Instant.parse("2026-09-01T00:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    private final String aliceToken = TestJwts.token("alice-sub", "bank.accounts.read");
    private final String bobToken = TestJwts.token("bob-sub", "bank.accounts.read");

    @BeforeEach
    void seed() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        data.createAccount(ALICE_HKD, alice, "HKD", "1000.00");
        data.createAccount(ALICE_SAVINGS, alice, "HKD", "25.5");
        data.createAccount(BOB_HKD, bob, "HKD", "500.00");
    }

    @Test
    void listsOnlyTheCallersAccounts() {
        MvcTestResult result = get("/accounts", aliceToken);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$[*].accountId").asArray().containsExactly(100, 101);
        assertThat(result).bodyJson().extractingPath("$[1].balance").isEqualTo("25.50");
    }

    @Test
    void anotherCustomersAccountIsNotFound() {
        assertThat(get("/accounts/" + ALICE_HKD, aliceToken)).hasStatus(HttpStatus.OK);
        assertProblem(get("/accounts/" + BOB_HKD, aliceToken), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
        assertProblem(
                get("/accounts/" + BOB_HKD + "/transactions", aliceToken), HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void readingRequiresTheReadScope() {
        String transferOnly = TestJwts.token("alice-sub", "bank.transfer");

        assertProblem(get("/accounts", transferOnly), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void pagesThroughTheWholeHistoryWithoutGapsOrDuplicates() {
        for (int i = 0; i < 120; i++) {
            long from = i % 3 == 0 ? BOB_HKD : ALICE_HKD;
            long to = from == BOB_HKD ? ALICE_HKD : BOB_HKD;
            data.insertCompletedTransfer(from, to, "1.00", T0.plusSeconds(i));
        }

        List<Integer> seen = new ArrayList<>();
        List<Integer> pageSizes = new ArrayList<>();
        String cursor = null;
        do {
            String body = historyPage(ALICE_HKD, 50, cursor);
            List<Integer> ids = JsonPath.read(body, "$.items[*].transactionId");
            seen.addAll(ids);
            pageSizes.add(ids.size());
            cursor = JsonPath.read(body, "$.nextCursor");
        } while (cursor != null);

        assertThat(pageSizes).containsExactly(50, 50, 20);
        assertThat(seen).hasSize(120).doesNotHaveDuplicates();
        assertThat(seen).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
    }

    @Test
    void rowsSharingATimestampAreNeitherSkippedNorRepeatedAtAPageBoundary() {
        long first = data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "1.00", T0);
        long second = data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "2.00", T0);
        long third = data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "3.00", T0);

        String page1 = historyPage(ALICE_HKD, 2, null);
        String page2 = historyPage(ALICE_HKD, 2, JsonPath.read(page1, "$.nextCursor"));

        List<Integer> ids = new ArrayList<>(JsonPath.read(page1, "$.items[*].transactionId"));
        ids.addAll(JsonPath.read(page2, "$.items[*].transactionId"));
        assertThat(ids).containsExactly((int) third, (int) second, (int) first);
        assertThat((Boolean) JsonPath.read(page2, "$.hasMore")).isFalse();
    }

    @Test
    void newTransactionsDoNotShiftLaterPages() {
        for (int i = 0; i < 4; i++) {
            data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "1.00", T0.plusSeconds(i));
        }
        String page1 = historyPage(ALICE_HKD, 2, null);

        data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "9.00", T0.plusSeconds(100));
        String page2 = historyPage(ALICE_HKD, 2, JsonPath.read(page1, "$.nextCursor"));

        List<Integer> ids = new ArrayList<>(JsonPath.read(page1, "$.items[*].transactionId"));
        ids.addAll(JsonPath.read(page2, "$.items[*].transactionId"));
        assertThat(ids).containsExactly(4, 3, 2, 1);
    }

    @Test
    void directionAndCounterpartyAreRelativeToTheViewedAccount() {
        data.insertCompletedTransfer(ALICE_HKD, BOB_HKD, "12.30", T0);

        String alice = historyPage(ALICE_HKD, 10, null);
        String bob = new String(get("/accounts/" + BOB_HKD + "/transactions", bobToken)
                .getResponse()
                .getContentAsByteArray());

        assertThat((String) JsonPath.read(alice, "$.items[0].direction")).isEqualTo("DEBIT");
        assertThat((Integer) JsonPath.read(alice, "$.items[0].counterpartyAccountId"))
                .isEqualTo(200);
        assertThat((String) JsonPath.read(alice, "$.items[0].amount")).isEqualTo("12.30");
        assertThat((String) JsonPath.read(bob, "$.items[0].direction")).isEqualTo("CREDIT");
        assertThat((Integer) JsonPath.read(bob, "$.items[0].counterpartyAccountId"))
                .isEqualTo(100);
    }

    @Test
    void invalidCursorIsABadRequest() {
        MvcTestResult result = get("/accounts/" + ALICE_HKD + "/transactions?cursor=tampered", aliceToken);

        assertProblem(result, HttpStatus.BAD_REQUEST, "INVALID_CURSOR");
    }

    @Test
    void pageSizeIsBounded() {
        MvcTestResult tooBig = get("/accounts/" + ALICE_HKD + "/transactions?size=101", aliceToken);
        MvcTestResult tooSmall = get("/accounts/" + ALICE_HKD + "/transactions?size=0", aliceToken);

        assertProblem(tooBig, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(tooBig).bodyJson().extractingPath("$.errors[0].field").isEqualTo("size");
        assertProblem(tooSmall, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    private String historyPage(long accountId, int size, String cursor) {
        String uri =
                "/accounts/" + accountId + "/transactions?size=" + size + (cursor == null ? "" : "&cursor=" + cursor);
        MvcTestResult result = get(uri, aliceToken);
        assertThat(result).hasStatus(HttpStatus.OK);
        return new String(result.getResponse().getContentAsByteArray());
    }

    private MvcTestResult get(String uri, String token) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
