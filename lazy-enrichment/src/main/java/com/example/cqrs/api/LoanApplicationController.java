package com.example.cqrs.api;

import com.example.cqrs.domain.LoanApplication.LoanCommandGateway;
import com.example.cqrs.domain.LoanApplication.commands.ApplyLoanRequestCommand;
import com.example.cqrs.domain.LoanApplication.commands.ApproveLoanCommand;
import com.example.cqrs.readmodel.LoanApplicationView;
import com.example.cqrs.readmodel.LoanApplicationViewRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan")
public class LoanApplicationController {

    private final LoanCommandGateway gateway;
    private final LoanApplicationViewRepository viewRepository;

    public LoanApplicationController(LoanCommandGateway gateway, LoanApplicationViewRepository viewRepository) {
        this.gateway = gateway;
        this.viewRepository = viewRepository;
    }

    public record ApplyRequest(String applicant, String amount) {}

    @PostMapping
    public String apply(@RequestBody ApplyRequest body) {
        var command = new ApplyLoanRequestCommand(body.applicant(), body.amount());
        gateway.send(command);
        return command.getApplicationId();
    }

    public record ApproveRequest(String applicationId) {}

    @PostMapping("/approve")
    public void approve(@RequestBody ApproveRequest body) {
        gateway.send(new ApproveLoanCommand(body.applicationId()));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<LoanApplicationView> get(@PathVariable String applicationId) {
        return viewRepository.findById(applicationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
