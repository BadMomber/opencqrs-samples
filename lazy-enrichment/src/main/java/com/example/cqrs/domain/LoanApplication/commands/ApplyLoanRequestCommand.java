package com.example.cqrs.domain.LoanApplication.commands;

import java.util.UUID;

public record ApplyLoanRequestCommand(
        String applicationId,
        String applicant,
        String amount
) implements LoanApplicationCommand {

    public ApplyLoanRequestCommand(String applicant, String amount) {
        this(UUID.randomUUID().toString(), applicant, amount);
    }

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.PRISTINE;
    }
}
