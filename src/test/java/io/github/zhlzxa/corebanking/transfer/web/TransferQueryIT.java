package io.github.zhlzxa.corebanking.transfer.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.zhlzxa.corebanking.support.AbstractIntegrationIT;
import io.github.zhlzxa.corebanking.support.TestDataFactory;
import io.github.zhlzxa.corebanking.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class TransferQueryIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TestDataFactory data;

    private String location;

    @BeforeEach
    void createTransfer() {
        long alice = data.createCustomer("alice-sub");
        long bob = data.createCustomer("bob-sub");
        long carol = data.createCustomer("carol-sub");
        data.createAccount(100, alice, "HKD", "1000.00");
        data.createAccount(200, bob, "HKD", "0.00");
        data.createAccount(300, carol, "HKD", "0.00");

        MvcTestResult created = mvc.post()
                .uri("/transfers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token("alice-sub", "bank.transfer"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"requestId": "req-q", "fromAccountId": 100, "toAccountId": 200,
                         "amount": "75.00", "currency": "HKD"}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        location = created.getResponse().getHeader(HttpHeaders.LOCATION);
    }

    @Test
    void creationReturnsTheLocationOfTheTransfer() {
        assertThat(location).matches("/transfers/\\d+");
    }

    @Test
    void senderAndRecipientCanReadTheTransfer() {
        MvcTestResult sender = get(location, "alice-sub");
        MvcTestResult recipient = get(location, "bob-sub");

        assertThat(sender).hasStatus(HttpStatus.OK);
        assertThat(sender).bodyJson().extractingPath("$.amount").isEqualTo("75.00");
        assertThat(recipient).hasStatus(HttpStatus.OK);
    }

    @Test
    void uninvolvedCustomerCannotSeeThatTheTransferExists() {
        MvcTestResult other = get(location, "carol-sub");
        MvcTestResult missing = get("/transfers/999999", "carol-sub");

        assertThat(other).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(other).bodyJson().extractingPath("$.code").isEqualTo("TRANSFER_NOT_FOUND");
        assertThat(missing).hasStatus(HttpStatus.NOT_FOUND);
    }

    private MvcTestResult get(String uri, String subject) {
        return mvc.get()
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestJwts.token(subject, "bank.accounts.read"))
                .exchange();
    }
}
