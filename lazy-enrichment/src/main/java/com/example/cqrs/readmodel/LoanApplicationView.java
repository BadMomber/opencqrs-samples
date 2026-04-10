package com.example.cqrs.readmodel;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LoanApplicationView {

    @Id
    private String applicationId;
    private String applicant;
    private String amount;
    private String manualReviewResult;
    private boolean approved;

    protected LoanApplicationView() {}

    public LoanApplicationView(String applicationId, String applicant, String amount) {
        this.applicationId = applicationId;
        this.applicant = applicant;
        this.amount = amount;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicant() {
        return applicant;
    }

    public String getAmount() {
        return amount;
    }

    public String getManualReviewResult() {
        return manualReviewResult;
    }

    public void setManualReviewResult(String manualReviewResult) {
        this.manualReviewResult = manualReviewResult;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }
}
