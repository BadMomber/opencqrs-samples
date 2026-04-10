package com.example.cqrs.domain.LoanApplication;

import com.example.cqrs.domain.LoanApplication.commands.ApplyLoanRequestCommand;
import com.example.cqrs.domain.LoanApplication.commands.ApproveLoanCommand;
import com.example.cqrs.domain.LoanApplication.commands.EnsureLoanEnrichmentCommand;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationAppliedEvent;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationApprovedEvent;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationEnrichedEvent;
import com.opencqrs.framework.command.CommandHandlingTest;
import com.opencqrs.framework.command.CommandHandlingTestFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CommandHandlingTest
class LoanApplicationHandlingTest {

    @Test
    void shouldSubmitNewLoanApplication(@Autowired CommandHandlingTestFixture<ApplyLoanRequestCommand> fixture) {
        var command = new ApplyLoanRequestCommand("applicant-1", "10000");

        fixture.givenNothing()
                .when(command)
                .expectSuccessfulExecution()
                .expectSingleEvent(new LoanApplicationAppliedEvent(
                        command.getApplicationId(), "applicant-1", "10000"
                ));
    }

    @Test
    void shouldRejectDuplicateApplication(@Autowired CommandHandlingTestFixture<ApplyLoanRequestCommand> fixture) {
        fixture.given(new LoanApplicationAppliedEvent("app-id", "applicant-1", "10000"))
                .when(new ApplyLoanRequestCommand("app-id", "applicant-1", "10000"))
                .expectException(IllegalStateException.class);
    }

    @Test
    void shouldEnrichWhenManualReviewResultIsMissing(@Autowired CommandHandlingTestFixture<EnsureLoanEnrichmentCommand> fixture) {
        fixture.given(new LoanApplicationAppliedEvent("app-id", "applicant-1", "10000"))
                .when(new EnsureLoanEnrichmentCommand("app-id"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new LoanApplicationEnrichedEvent("app-id", "COMPLIANT"));
    }

    @Test
    void shouldSkipEnrichmentWhenAlreadyEnriched(@Autowired CommandHandlingTestFixture<EnsureLoanEnrichmentCommand> fixture) {
        fixture.given(
                        new LoanApplicationAppliedEvent("app-id", "applicant-1", "10000"),
                        new LoanApplicationEnrichedEvent("app-id", "COMPLIANT")
                )
                .when(new EnsureLoanEnrichmentCommand("app-id"))
                .expectSuccessfulExecution()
                .expectNoEvents();
    }

    @Test
    void shouldApproveLoanApplication(@Autowired CommandHandlingTestFixture<ApproveLoanCommand> fixture) {
        fixture.given(
                        new LoanApplicationAppliedEvent("app-id", "applicant-1", "10000"),
                        new LoanApplicationEnrichedEvent("app-id", "COMPLIANT")
                )
                .when(new ApproveLoanCommand("app-id"))
                .expectSuccessfulExecution()
                .expectSingleEvent(new LoanApplicationApprovedEvent("app-id"));
    }
}
