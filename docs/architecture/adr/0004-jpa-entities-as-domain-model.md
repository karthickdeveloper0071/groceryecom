# ADR-0004: JPA entities as the domain model

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

The generic company standard for a persistence layer is four types per aggregate:
a framework-free domain object, a `*Entity` with the JPA annotations, a
`*Repository` port interface in the domain, a `*RepositoryImpl` adapter, plus a
mapper between domain object and entity.

Most modules in this project are CRUD-shaped. `identity` is a single `User`
aggregate with field validation and two or three rules. `catalog`, `vendor` and
`delivery` look similar: read and write rows, enforce a handful of constraints.
A few future modules are not CRUD-shaped: the payment ledger and inventory
reservation have invariants that must hold across several rows.

Whatever we choose, entities must not leak into API responses: a JSON response
that is a serialized entity couples the public API to the schema and leaks
columns like `password_hash`.

## Problem

Do we model the domain with framework-free objects behind a repository adapter,
or use JPA entities as the domain model directly?

## Options considered

| Option | Pros | Cons |
|--------|------|------|
| Full separation: domain object, `*Entity`, port interface, `*RepositoryImpl`, mapper | domain is testable with no Spring or JPA; persistence can be swapped; invariants live in objects that cannot be half-loaded | four types and a mapper per aggregate; the mapper is pure duplication while the two shapes are identical; every field addition is edited in five places; lazy loading and dirty checking have to be reimplemented or given up |
| JPA entities as the domain model, Spring Data repository interfaces as the port, `mapper/` to build responses | one type per aggregate; dirty checking means a setter inside a transaction is the update; Spring Data generates the queries; entities never reach the API because `mapper/` builds response records | the domain model carries JPA annotations and a no-arg constructor; a detached or lazily-loaded entity can be observed in a partial state; swapping persistence means touching the domain |
| Entities everywhere including API responses | least code | the public API becomes the schema, and any new column is published by accident |

## Decision

JPA entities in `modules/<name>/domain/` **are** the domain model. Spring Data
repository interfaces in the same package are the port; there is no hand-written
`*RepositoryImpl`. Each module's `mapper/` package converts entities into
response records, so entities never appear in a response body.

Concretely, in `identity`: `domain/User.java` extends
`shared.persistence.BaseEntity`, `domain/UserRepository.java` extends
`JpaRepository<User, Long>` with derived queries (`findByPublicId`,
`existsByUsername`), and the response records in `api/dto` are built from the
entity rather than serialized from it.

This deviates from the generic company standard on purpose.

## Reason

For a CRUD-shaped module the separation buys nothing we need and costs a mapper
we have to maintain. The two shapes are identical, so the mapper is mechanical
duplication, and "the domain does not know about JPA" has no payoff while
PostgreSQL is the only datastore we plan to have
([ADR-0002](0002-postgresql-as-primary-database.md)).

Dirty checking is a concrete benefit, not just less typing. `changePassword` sets
the new hash on a managed entity and the update happens at commit, with no
save call to forget:

```java
user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
// persisted at commit by dirty checking
```

The part of the separation that actually earns its keep is keeping entities out
of the public API, and `mapper/` keeps that without the other three types.

## Consequences

**Positive**

- One type per aggregate; a new column is one entity field and one migration.
- No hand-written repository implementations; Spring Data derives the queries.
- `ddl-auto=validate` compares the entity directly against the Flyway schema, so
  drift stops startup instead of surfacing later.
- Response records stay a deliberate choice of fields.

**Negative**

- Domain classes carry JPA annotations, a no-arg constructor and mutable setters,
  so a half-built entity is representable.
- Business rules can end up in a service rather than on the entity, because the
  entity has public setters. Reviewers have to watch for that.
- `open-in-view` is off, so touching a lazy association outside a transaction
  throws; reads must fetch what they need inside the service.
- Replacing JPA in a module would mean rewriting its domain classes.

**Revisit when**

A module has invariants that must hold across several rows and cannot be
expressed as database constraints. The known candidates are the **payment
ledger** (debits and credits must always balance) and **inventory reservation**
(reserved plus available must equal on-hand). For those, introduce a
framework-free aggregate with a repository port, module by module. This ADR is
not a rule that all modules must be built the same way; it is the default for
CRUD-shaped ones.
