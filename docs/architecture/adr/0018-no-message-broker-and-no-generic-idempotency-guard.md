# ADR-0018: Remove the unused broker and the generic idempotency guard

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team
- **Amends:** [ADR-0006](0006-transactional-outbox-for-module-events.md) (the outbox
  stands; the broker it mentioned is gone), [ADR-0010](0010-idempotency-strategy.md)
  (superseded)

## Context

Two pieces of the platform had been built for a future that had not arrived, and
after three business modules it was clear neither was being used the way it was
planned.

**RabbitMQ.** A dependency, a config block, a container, four environment
variables, a health indicator and several paragraphs of documentation — and not one
line of application code that publishes or consumes. Module events go through
Spring Modulith's `event_publication` table in the same transaction as the business
change, which is what actually gives the guarantee ([ADR-0006](0006-transactional-outbox-for-module-events.md)).
The broker was there for a later day when something outside the application would
consume events. Nothing does.

**`IdempotencyGuard`.** A generic "claim a key, replay the stored response"
mechanism keyed by an `Idempotency-Key` header, with a Redis store. No endpoint ever
called it. Meanwhile the billing module met the same requirement twice, and neither
time did it reach for the guard:

- confirming a subscription payment is idempotent because `provider_reference` is
  unique and the payment's own status decides whether there is anything to do;
- choosing a plan twice reuses the open charge, because the gateway's reference
  already identifies it.

Both are stronger than the generic mechanism: they survive a Redis outage, they
work when the retry arrives at a different instance, and they cannot be forgotten by
a caller who omits a header.

## Decision

**Delete both.**

- The AMQP starter, the `spring.rabbitmq` configuration, the container, its
  environment variables and its documentation are removed. Events keep using the
  outbox.
- `platform.idempotency` is removed. Idempotency is achieved with a **natural key
  in the database** — a provider reference, an order number, a request id that the
  domain already has — enforced by a unique constraint, with the state machine
  deciding what a repeat means.

Both come back the moment there is a caller. A broker returns when something outside
this application must consume events, and it is then wired to the outbox rather than
published to directly. A generic guard returns if an endpoint genuinely has no
natural key, which none so far does.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| Keep both for when they are needed | no work; the decision was already made | unused infrastructure is not free. It is started in every environment, appears in health checks, gets upgraded for CVEs, and teaches every new reader that the system uses a broker. The guard is worse: it looks like the platform's idempotency answer while the real answers are elsewhere |
| Keep RabbitMQ, use it for something to justify it | the dependency becomes real | inventing a consumer to justify a container is backwards |
| Keep the guard and apply it to the billing endpoints | one mechanism, header-driven, familiar | it would sit in front of the unique constraints that already do the work, and add a Redis dependency to a path that currently survives Redis being down |

## Consequences

- One less container locally and in every environment; one less dependency to patch;
  a smaller image.
- Any future need for a broker is a decision made with a real consumer in front of
  it, not a default inherited from a template.
- Idempotency is now a **design obligation on each write path** rather than a
  mechanism someone may remember to switch on. The code review checklist asks how a
  retry of this request behaves, and the answer must be a database constraint, not
  "the client sends a key".
- ADR-0010 is superseded. ADR-0006's reasoning about the outbox is unchanged; only
  its remark that RabbitMQ is already available no longer holds.
