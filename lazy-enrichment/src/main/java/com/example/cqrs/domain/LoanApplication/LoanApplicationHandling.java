package com.example.cqrs.domain.LoanApplication;

import com.example.cqrs.domain.LoanApplication.commands.ApproveLoanCommand;
import com.example.cqrs.domain.LoanApplication.commands.ApplyLoanRequestCommand;
import com.example.cqrs.domain.LoanApplication.commands.EnsureLoanEnrichmentCommand;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationAppliedEvent;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationApprovedEvent;
import com.example.cqrs.domain.LoanApplication.events.LoanApplicationEnrichedEvent;
import com.opencqrs.framework.command.CommandEventPublisher;
import com.opencqrs.framework.command.CommandHandlerConfiguration;
import com.opencqrs.framework.command.CommandHandling;
import com.opencqrs.framework.command.StateRebuilding;

@CommandHandlerConfiguration
public class LoanApplicationHandling {

    // --- Command Handlers ---

    // State parameter omitted: PRISTINE subject condition guarantees no prior state exists.
    @CommandHandling
    public String handle(ApplyLoanRequestCommand command, CommandEventPublisher<LoanRequest> publisher) {
        publisher.publish(
                new LoanApplicationAppliedEvent(
                        command.applicationId(),
                        command.applicant(),
                        command.amount()
                )
        );

        return command.getApplicationId();
    }

    @CommandHandling
    public void handle(LoanRequest request, EnsureLoanEnrichmentCommand command, CommandEventPublisher<LoanRequest> publisher) {
        if (request.manualReviewResult() == null) {
            publisher.publish(
                    new LoanApplicationEnrichedEvent(
                            command.getApplicationId(),
                            "COMPLIANT"
                    )
            );
        }
    }

    @CommandHandling
    public void handle(LoanRequest request, ApproveLoanCommand command, CommandEventPublisher<LoanRequest> publisher) {
        if (request.manualReviewResult() == null) {
            throw new IllegalStateException("Loan must be enriched before approval");
        }
        publisher.publish(
                new LoanApplicationApprovedEvent(command.getApplicationId())
        );
    }

    // --- State Rebuilding ---

    @StateRebuilding
    public LoanRequest on(LoanApplicationAppliedEvent event) {
        return new LoanRequest(event.applicationId(), event.applicant(), event.amount(), null);
    }

    @StateRebuilding
    public LoanRequest on(LoanRequest instance, LoanApplicationEnrichedEvent event) {
        return new LoanRequest(instance.applicationId(), instance.applicant(), instance.amount(), event.manualReviewResult());
    }

    @StateRebuilding
    public LoanRequest on(LoanRequest instance, LoanApplicationApprovedEvent event) {
        return instance;
    }
}
