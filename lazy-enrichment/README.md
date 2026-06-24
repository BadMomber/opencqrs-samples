# Lazy Enrichment

-----

**NOTE**

This sample assumes you have completed the official [OpenCQRS tutorial](https://docs.opencqrs.com/tutorials/).

-----

When an event-sourced system has been running for a while, the schema you wrote with on day one is rarely the schema you wish you had today. Old events are immutable — you cannot rewrite history to add a field that did not exist when the event was first appended. **Lazy enrichment** is a technique for filling in that missing data: the first time an entity is touched after the schema change, the system fetches the missing value and appends a fresh enrichment event to the entity's stream. From then on, every state reconstruction sees the complete schema.

This sample shows how to implement lazy enrichment in OpenCQRS in a way that is

- **atomic** — the enrichment and the business event commit together, or neither does;
- **compatible with optimistic locking** — a `SubjectIsOnEventId` precondition on the incoming command is honoured;
- **idempotent on retry** — repeating the command on an already-enriched entity does not produce a second enrichment event.

The example domain is a tiny loan-application service. The `manualReviewResult` field was added to the schema only later; old `LoanApplicationAppliedEvent` instances were written without it. Whenever someone tries to approve such a loan, the handler notices that the field is still empty, fetches the review outcome from a `ManualReviewService` and writes an enrichment event before the approval — all in one transaction.

## The pattern: single-handler enrichment

The cleanest realization keeps both effects inside one command handler. OpenCQRS appends every event published by a single handler invocation as **one** atomic transaction against the subject's stream. That single property removes the entire class of "two-commands-without-a-transaction" problems that a `CommandRouter`-wrapping gateway suffers from.

[`LoanApplicationHandling.handle(ApproveLoanCommand, …)`](src/main/java/com/example/cqrs/domain/LoanApplication/LoanApplicationHandling.java):

```java
@CommandHandling
public void handle(
        LoanRequest request,
        ApproveLoanCommand command,
        CommandEventPublisher<LoanRequest> publisher,
        @Autowired ManualReviewService manualReviewService) {

    String reviewResult = request.manualReviewResult();
    if (reviewResult == null) {
        reviewResult = manualReviewService.fetchReviewResult(command.getApplicationId());
        publisher.publish(new LoanApplicationEnrichedEvent(command.getApplicationId(), reviewResult));
    }

    if (!"COMPLIANT".equals(reviewResult)) {
        throw new IllegalStateException("Loan cannot be approved, review result: " + reviewResult);
    }
    publisher.publish(new LoanApplicationApprovedEvent(command.getApplicationId()));
}
```

What this does step by step:

1. OpenCQRS sources `LoanRequest` by replaying the subject's events. If the stream contains only the legacy `LoanApplicationAppliedEvent`, `request.manualReviewResult()` is `null`.
2. The handler calls the external `ManualReviewService` — this stands in for whatever real source (REST, gRPC, queue, human review) supplies the missing data today.
3. The enrichment event is published. **It is not committed yet** — `CommandEventPublisher.publish()` queues events for the handler's transaction.
4. The business invariant check uses the freshly-fetched value, not a re-sourced state.
5. The approval event is published. Both events go to ESDB in one atomic append starting from the sourced version of the subject.

Subsequent commands on the same entity see both events and skip the enrichment branch (idempotent on retry).

## Commands, events and state

### [`ApplyLoanRequestCommand`](src/main/java/com/example/cqrs/domain/LoanApplication/commands/ApplyLoanRequestCommand.java)

`SubjectCondition.PRISTINE` — must create a fresh subject. Handled without state sourcing; produces `LoanApplicationAppliedEvent`.

### [`ApproveLoanCommand`](src/main/java/com/example/cqrs/domain/LoanApplication/commands/ApproveLoanCommand.java)

`SubjectCondition.EXISTS` — operates on an existing subject. The handler shown above does the inline enrichment and approval.

### Events

| Event | Written by | Carries |
|---|---|---|
| `LoanApplicationAppliedEvent` | initial application | `applicationId`, `applicant`, `amount` |
| `LoanApplicationEnrichedEvent` | inline enrichment in the approve handler | `applicationId`, `manualReviewResult` |
| `LoanApplicationApprovedEvent` | approve handler | `applicationId` |

### [`LoanRequest`](src/main/java/com/example/cqrs/domain/LoanApplication/LoanRequest.java)

The sourced aggregate state. `manualReviewResult` is `null` for entities not yet enriched; set after the enrichment event is replayed.

### [`ManualReviewService`](src/main/java/com/example/cqrs/domain/LoanApplication/ManualReviewService.java) / [`StubManualReviewService`](src/main/java/com/example/cqrs/domain/LoanApplication/StubManualReviewService.java)

External enrichment source. The stub returns `"COMPLIANT"` so the sample is reproducible; a real implementation would call out to the compliance system.

## Why not a gateway that sends two commands

A tempting alternative is to wrap `CommandRouter` in a "gateway" that intercepts incoming commands, dispatches an `EnsureLoanEnrichmentCommand` first, then forwards the original. That construction looks elegant — it abstracts the pre-step away from each handler — but it has three structural defects on the command side:

1. **No transaction over the two `send(…)` calls.** Commit 1 writes the enrichment event; commit 2 writes the business event. If the second send throws (handler exception, subject-condition violation, store error), the enrichment is permanent without the approval.
2. **Breaks `SubjectIsOnEventId` optimistic locking.** A client that reads the entity at version `X` and sends the approval with a `SubjectIsOnEventId(X)` precondition will see the precondition fail — because the gateway has appended an enrichment event between, moving the subject's tip to `Y`.
3. **Idempotency only half-solved.** The enrichment is no-op-on-replay, but the business command is re-dispatched on retry and needs its own state-based guard.

The single-handler enricher removes all three: one append, one transaction, one precondition check.

## When this pattern is *not* enough

Lazy enrichment in a single handler works because both events target the **same subject** and the same handler invocation. As soon as one of those is no longer true, you outgrow this pattern:

- **Cross-aggregate enrichment** — the enrichment writes to one subject, the business effect to another. Atomicity is gone; there is no shared append.
- **Asynchronous or long-running enrichment** — external systems that take seconds, retries, human-in-the-loop steps.
- **Multi-step enrichment with intermediate state** — the enrichment itself is a process, not a single decision.

For these cases, reach for a **Saga** (see the [`implementing-sagas`](../implementing-sagas/) sample). Sagas are designed exactly for orchestrating multiple writes across aggregates with explicit retry, compensation and status tracking — the things that an in-handler enricher cannot give you.

## A note on consistency

In OpenCQRS, sourcing reads an entity's full event stream from the store, and writes are committed before `commandRouter.send(…)` returns. **Within a single subject, this is strongly consistent** — after a successful send, the next sourcing on that subject sees the just-written events. Eventual consistency in OpenCQRS applies to projections (read models like `LoanApplicationView`) and to cross-subject reads, not to follow-up command sourcing on the same subject. The single-handler enricher exploits this guarantee directly: it does not need to wait, retry, or version-check between the two events because they share a single append.

## Running the sample

```bash
docker-compose up -d           # ESDB + Postgres
./gradlew bootRun
```

Try it via the REST API (or the included Bruno collection):

```bash
# 1. Apply for a loan
curl -X POST http://localhost:8080/api/loan \
     -H 'Content-Type: application/json' \
     -d '{"applicant":"Alice","amount":"10000"}'
# → returns the new applicationId

# 2. Approve it — triggers lazy enrichment + approval in one transaction
curl -X POST http://localhost:8080/api/loan/approve \
     -H 'Content-Type: application/json' \
     -d '{"applicationId":"<id-from-step-1>"}'

# 3. Inspect the read model
curl http://localhost:8080/api/loan/<id-from-step-1>
```

## Test fixture walkthrough

[`LoanApplicationHandlingTest`](src/test/java/com/example/cqrs/domain/LoanApplication/LoanApplicationHandlingTest.java) uses OpenCQRS's `@CommandHandlingTest` and `CommandHandlingTestFixture`. The external `ManualReviewService` is provided as a Spring `@MockitoBean`, so each test can program what the stub returns:

```java
@MockitoBean
ManualReviewService manualReviewService;

@Test
void shouldEnrichAndApproveInOneAppendWhenNotYetEnriched(
        @Autowired CommandHandlingTestFixture<ApproveLoanCommand> fixture) {
    when(manualReviewService.fetchReviewResult("app-id")).thenReturn("COMPLIANT");

    fixture.given(new LoanApplicationAppliedEvent("app-id", "applicant-1", "10000"))
            .when(new ApproveLoanCommand("app-id"))
            .expectSuccessfulExecution()
            .expectEvents(
                    new LoanApplicationEnrichedEvent("app-id", "COMPLIANT"),
                    new LoanApplicationApprovedEvent("app-id")
            );
}
```

The fixture replays the legacy `LoanApplicationAppliedEvent` to source the state, the handler runs, and the test asserts that **both** events were produced — confirming that enrichment and approval happen as one append.
