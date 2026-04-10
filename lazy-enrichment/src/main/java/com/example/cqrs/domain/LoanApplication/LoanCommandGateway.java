package com.example.cqrs.domain.LoanApplication;

import com.example.cqrs.domain.LoanApplication.commands.EnsureLoanEnrichmentCommand;
import com.example.cqrs.domain.LoanApplication.commands.LoanApplicationCommand;
import com.opencqrs.framework.command.Command;
import com.opencqrs.framework.command.CommandRouter;
import com.opencqrs.framework.command.CommandSubjectDoesNotExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LoanCommandGateway {

    private static final Logger log = LoggerFactory.getLogger(LoanCommandGateway.class);

    private final CommandRouter commandRouter;

    public LoanCommandGateway(CommandRouter commandRouter) {
        this.commandRouter = commandRouter;
    }

    public <R> R send(Command command) {
        return send(command, Map.of());
    }

    public <R> R send(Command command, Map<String, ?> metaData) {
        if (command instanceof LoanApplicationCommand lac
                && !(command instanceof EnsureLoanEnrichmentCommand)
                && lac.getSubjectCondition() != Command.SubjectCondition.PRISTINE) {
            try {
                log.info("Ensuring loan application {} is enriched before processing {}", lac.getApplicationId(), command.getClass().getSimpleName());
                commandRouter.send(new EnsureLoanEnrichmentCommand(lac.getApplicationId()));
            } catch (CommandSubjectDoesNotExistException e) {
                log.debug("Loan application {} does not exist yet, skipping enrichment", lac.getApplicationId());
            }
        }
        return commandRouter.send(command, metaData);
    }
}
