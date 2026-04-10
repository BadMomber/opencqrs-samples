package com.example.cqrs.domain.LoanApplication.commands;

public record EnsureLoanEnrichmentCommand(String applicationId) implements LoanApplicationCommand {

    @Override
    public String getApplicationId() {
        return applicationId;
    }

    @Override
    public SubjectCondition getSubjectCondition() {
        return SubjectCondition.EXISTS;
    }
}
