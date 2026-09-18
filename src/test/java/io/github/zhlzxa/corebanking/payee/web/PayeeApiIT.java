package io.github.zhlzxa.corebanking.payee.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.github.zhlzxa.corebanking.payee.Payee;
import io.github.zhlzxa.corebanking.payee.PayeeRepository;
import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class PayeeApiIT extends AbstractIntegrationIT {

    private static final String LANDLORD = """
            {"nickname": "Landlord", "accountNumber": "123-456789-001", "bankCode": "004", "currency": "HKD"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    @Autowired
    private PayeeRepository payeeRepository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private long alice;
    private final String aliceToken = TestJwts.token("alice-sub", "bank.payees.read", "bank.payees.write");
    private final String bobToken = TestJwts.token("bob-sub", "bank.payees.read", "bank.payees.write");

    @BeforeEach
    void seed() {
        alice = data.createCustomer("alice-sub");
        data.createCustomer("bob-sub");
    }

    @Test
    void addedPayeeIsReturnedMaskedAndAudited() {
        MvcTestResult result = add(aliceToken, LANDLORD);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).matches("/payees/\\d+");
        assertThat(result).bodyJson().extractingPath("$.maskedAccountNumber").isEqualTo("**********-001");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(0);
        assertThat(new String(result.getResponse().getContentAsByteArray())).doesNotContain("123-456789");
        assertThat(jdbc.sql("SELECT actor_user_id FROM audit_events WHERE action = 'PAYEE_ADDED'")
                        .query(Long.class)
                        .single())
                .isEqualTo(alice);
    }

    @Test
    void sameDestinationCannotBeAddedTwice() {
        add(aliceToken, LANDLORD);

        assertProblem(add(aliceToken, LANDLORD), HttpStatus.CONFLICT, "PAYEE_ALREADY_EXISTS");
    }

    @Test
    void listContainsOnlyTheCallersPayeesSortedByNickname() {
        add(aliceToken, LANDLORD);
        add(aliceToken, """
                {"nickname": "Electricity", "accountNumber": "900", "currency": "HKD"}
                """);
        add(bobToken, """
                {"nickname": "Bob's gym", "accountNumber": "555", "currency": "HKD"}
                """);

        MvcTestResult result = get(aliceToken);

        assertThat(result)
                .bodyJson()
                .extractingPath("$[*].nickname")
                .asArray()
                .containsExactly("Electricity", "Landlord");
    }

    @Test
    void renameWithTheCurrentVersionSucceedsAndIncrementsIt() {
        long id = idOf(add(aliceToken, LANDLORD));

        MvcTestResult result = rename(aliceToken, id, "New landlord", 0);

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.nickname").isEqualTo("New landlord");
        assertThat(result).bodyJson().extractingPath("$.version").isEqualTo(1);
    }

    @Test
    void renameBasedOnAStaleVersionIsRejected() {
        long id = idOf(add(aliceToken, LANDLORD));
        rename(aliceToken, id, "From the phone", 0);

        MvcTestResult stale = rename(aliceToken, id, "From the laptop", 0);

        assertProblem(stale, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertThat(payeeRepository.findById(id).orElseThrow().getNickname()).isEqualTo("From the phone");
    }

    @Test
    void anotherCustomersPayeeCannotBeChangedOrRemoved() {
        long id = idOf(add(aliceToken, LANDLORD));

        assertProblem(rename(bobToken, id, "Hijacked", 0), HttpStatus.NOT_FOUND, "PAYEE_NOT_FOUND");
        assertProblem(remove(bobToken, id), HttpStatus.NOT_FOUND, "PAYEE_NOT_FOUND");
        assertThat(payeeRepository.findById(id).orElseThrow().getNickname()).isEqualTo("Landlord");
    }

    @Test
    void removedPayeeDisappearsAndRemovalIsAudited() {
        long id = idOf(add(aliceToken, LANDLORD));

        assertThat(remove(aliceToken, id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(payeeRepository.findById(id)).isEmpty();
        assertThat(data.count("audit_events WHERE action = 'PAYEE_REMOVED'")).isEqualTo(1);
    }

    @Test
    void readOnlyScopeCannotModifyPayees() {
        String readOnly = TestJwts.token("alice-sub", "bank.payees.read");

        assertProblem(add(readOnly, LANDLORD), HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    @Test
    void invalidPayeeDetailsAreRejected() {
        MvcTestResult result = add(aliceToken, """
                {"nickname": " ", "accountNumber": "12 34", "currency": "hkd"}
                """);

        assertProblem(result, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactlyInAnyOrder("nickname", "accountNumber", "currency");
    }

    @Test
    void listingManyPayeesIssuesASingleQuery() {
        for (int i = 0; i < 20; i++) {
            payeeRepository.save(new Payee(alice, "Payee " + i, "ACC" + i, null, "HKD"));
        }
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        MvcTestResult result = get(aliceToken);

        assertThat(result).bodyJson().extractingPath("$.length()").isEqualTo(20);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private MvcTestResult add(String token, String body) {
        return mvc.post()
                .uri("/payees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private MvcTestResult rename(String token, long id, String nickname, long version) {
        return mvc.patch()
                .uri("/payees/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"%s\", \"version\": %d}".formatted(nickname, version))
                .exchange();
    }

    private MvcTestResult remove(String token, long id) {
        return mvc.delete()
                .uri("/payees/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private MvcTestResult get(String token) {
        return mvc.get()
                .uri("/payees")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange();
    }

    private static long idOf(MvcTestResult result) {
        Number id = JsonPath.read(new String(result.getResponse().getContentAsByteArray()), "$.payeeId");
        return id.longValue();
    }

    private static void assertProblem(MvcTestResult result, HttpStatus status, String code) {
        assertThat(result).hasStatus(status);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo(code);
    }
}
