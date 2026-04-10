package com.example.cqrs.domain.LoanApplication;

import com.example.cqrs.domain.LoanApplication.commands.ApplyLoanRequestCommand;
import com.example.cqrs.domain.LoanApplication.commands.ApproveLoanCommand;
import com.example.cqrs.domain.LoanApplication.commands.EnsureLoanEnrichmentCommand;
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

        var captor = ArgumentCaptor.forClass(com.opencqrs.framework.command.Command.class);
        verify(commandRouter, times(2)).send(captor.capture(), anyMap());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(EnsureLoanEnrichmentCommand.class);
        assertThat(captor.getAllValues().get(1)).isEqualTo(command);
    }

    @Test
    void shouldSkipEnrichmentForNewApplication() {
        var command = new ApplyLoanRequestCommand("applicant-1", "10000");

        gateway.send(command);

        var captor = ArgumentCaptor.forClass(com.opencqrs.framework.command.Command.class);
        verify(commandRouter, times(1)).send(captor.capture(), anyMap());

        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    void shouldSkipEnrichmentForEnrichmentCommand() {
        var command = new EnsureLoanEnrichmentCommand("app-id");

        gateway.send(command);

        var captor = ArgumentCaptor.forClass(com.opencqrs.framework.command.Command.class);
        verify(commandRouter, times(1)).send(captor.capture(), anyMap());

        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    void shouldProceedWhenEntityDoesNotExistYet() {
        when(commandRouter.send(any(EnsureLoanEnrichmentCommand.class), anyMap()))
                .thenThrow(new CommandSubjectDoesNotExistException("/loan-applications/app-id", new EnsureLoanEnrichmentCommand("app-id")));

        var command = new ApproveLoanCommand("app-id");

        gateway.send(command);

        verify(commandRouter, times(2)).send(any(), anyMap());
    }
}
