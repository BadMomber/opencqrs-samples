package com.example.cqrs.domain.LoanApplication.commands;

import com.opencqrs.framework.command.Command;

public interface LoanApplicationCommand extends Command {

    String getApplicationId();

    @Override
    default String getSubject() {
        return "/loan-applications/" + getApplicationId();
    }
}
