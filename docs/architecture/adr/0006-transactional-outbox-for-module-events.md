# ADR-0006: Transactional outbox for module events

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Modules need to react to each other without being wired together. When
`identity` registers a customer, `notification` should send a welcome email; when
`order` is placed, `inventory` should commit the reservation and `notification`
should tell the vendor. The caller does not need an answer and should not fail if
the reaction fails.

The dangerous version of this is the pair "write to the database, then publish a
message". If the process dies between the two, the business change happened and
nothing reacted, and there is no record that anything was missed. The mirror
image is publishing first and then failing to commit, which announces something
that never happened.

A broker was configured in `application.yml` and ran in `docker-compose.yml` when this
was written; it was removed in [ADR-0018](0018-no-message-broker-and-no-generic-idempotency-guard.md)
because nothing consumed it. The outbox decision below is unaffected.

## Problem

How does one module announce that something happened, so that the announcement
cannot be lost when the business change succeeds?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Plain Spring `ApplicationEventPublisher`, synchronous listeners | no infrastructure; same transaction | a slow listener slows the caller; a failing listener rolls back the business change; one module's bug breaks another module's write |
| Plain Spring events, `@Async` listeners | caller is not slowed | the event exists only in memory: a crash, a restart or a listener exception loses it silently |
| Publish straight to RabbitMQ after commit | real decoupling; survives an app restart | between commit and publish there is a window where the event is lost; RabbitMQ becomes required for correctness |
| Transactional outbox: write the publication to the database in the same transaction, deliver afterwards | the event is as durable as the business change; delivery can retry; no broker needed on the write path | one more table and its growth to manage; delivery is at-least-once, so listeners must be idempotent |

## Decision

Spring Modulith's event publication registry, which is a transactional outbox.

- A module publishes a record from its own `contract` package using
  `ApplicationEventPublisher`.
- Listeners in other modules are annotated `@ApplicationModuleListener`
  (transactional, after-commit, asynchronous).
- Each listener's publication is written to the `event_publication` table in the
  **same transaction** as the business change, by
  `spring-modulith-starter-jdbc`.
- The table is created by `V2__platform_create_event_publication.sql`, copied
  from `spring-modulith-events-jdbc` 2.1.1 `schemas/v2/schema-postgresql.sql`.
- `spring.modulith.events.completion-mode=delete`, so a row is removed once its
  listener has handled the event.

Example, from `identity`:

```java
// modules/identity/contract/UserRegisteredEvent.java
public record UserRegisteredEvent(UUID userId, String email, Role role) { }

// inside the registration service, in the same @Transactional method as the save
events.publishEvent(new UserRegisteredEvent(saved.getPublicId(), saved.getEmail(), saved.getRole()));
```

`UserRegisteredEvent` is published today. **No module consumes it yet** —
`notification` does not exist.

## Reason

The outbox makes the event exactly as durable as the row it describes, using the
database we already commit to, with no second system on the write path. Modulith
gives it to us as one dependency plus one migration, and the same mechanism keeps
working when a listener eventually lives in another process: only the delivery
step changes.

`completion-mode=delete` rather than keeping completed rows because the table
would otherwise grow with every event forever and we have no audit requirement
that it satisfies. It is not an audit log, and should not be treated as one.

## Consequences

**Positive**

- An event survives a listener exception, a restart or a broker outage; the row
  is still there to retry.
- A failing listener does not roll back the publisher's business change.
- Publisher and listener share no types except a record in `contract`.
- Incomplete rows in `event_publication` are a direct signal that something is
  not being handled.

**Negative**

- Every business write that publishes an event does one extra INSERT per
  listener.
- Delivery is at-least-once, so **listeners must be idempotent**: handling the
  same `UserRegisteredEvent` twice must not send two emails.
- Ordering between events is not guaranteed.
- With `completion-mode=delete` there is no history of what was delivered, only
  what is still pending.
- Incomplete publications are not automatically resubmitted or alerted on today.
  Nothing watches the table; that is a gap, not a feature.

**Revisit when**

- Events must reach another process. Then add `spring-modulith-events-amqp` and
  the existing RabbitMQ instance becomes the transport, without changing
  publishers or the outbox.
- The `event_publication` table shows a steady backlog, which means retries and
  monitoring need to be configured rather than assumed.
- An audit trail is required. That is a separate, purpose-built table, not this
  one.
