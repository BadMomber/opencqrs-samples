package com.example.cqrs.domain.LoanApplication;

import com.example.cqrs.domain.LoanApplication.commands.ApplyLoanRequestCommand;
import com.example.cqrs.domain.LoanApplication.commands.ApproveLoanCommand;
import com.example.cqrs.domain.LoanApplication.commands.EnsureLoanEnrichmentCommand;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanCommandGatewayTest {

    @Mock
    CommandRouter commandRouter;

    LoanCommandGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new LoanCommandGateway(commandRouter);
    }

    @Test
    void shouldEnrichBeforeApprovingExistingApplication() {
        var command = new ApproveLoanCommand("app-id");

        gateway.send(command);

        // Enrichment uses one-arg send
        var enrichCaptor = ArgumentCaptor.forClass(Command.class);
        verify(commandRouter).send(enrichCaptor.capture());
        assertThat(enrichCaptor.getValue()).isInstanceOf(EnsureLoanEnrichmentCommand.class);

        // Actual command uses two-arg send
        verify(commandRouter).send(eq(command), anyMap());
    }

    @Test
    void shouldSkipEnrichmentForNewApplication() {
        var command = new ApplyLoanRequestCommand("applicant-1", "10000");

        gateway.send(command);

        verify(commandRouter, never()).send(any(Command.class));
        verify(commandRouter).send(eq(command), anyMap());
    }

    @Test
    void shouldSkipEnrichmentForEnrichmentCommand() {
        var command = new EnsureLoanEnrichmentCommand("app-id");

        gateway.send(command);

        verify(commandRouter, never()).send(any(Command.class));
        verify(commandRouter).send(eq(command), anyMap());
    }

    @Test
    void shouldProceedWhenEntityDoesNotExistYet() {
        when(commandRouter.send(any(EnsureLoanEnrichmentCommand.class)))
                .thenThrow(new CommandSubjectDoesNotExistException("/loan-applications/app-id", new EnsureLoanEnrichmentCommand("app-id")));

        var command = new ApproveLoanCommand("app-id");

        gateway.send(command);

        verify(commandRouter).send(any(EnsureLoanEnrichmentCommand.class));
        verify(commandRouter).send(eq(command), anyMap());
    }
}
