# Feature development process

From a ticket to merged code. Setup is in the
[backend developer guide](../onboarding/backend-developer-guide.md).

## The loop

1. **Understand the change.** Which module owns it? If the answer is "two
   modules", one of them owns the data and the other reacts to an event. Decide
   that before writing code.
2. **Branch** from up-to-date `main`: `feature/<short-description>`. See the
   [git standard](git-standard.md).
3. **Write the migration first** if the change needs a schema change. The schema
   shapes the entity, not the other way round.
4. **Work outside-in or inside-out, but land the layers in order:** `domain`
   (entity, repository, rules) → `application` (the use-case service) →
   `mapper` → `api` (controller and DTOs).
5. **Write tests as you go.** A unit test for the rule, a context test for the
   wiring. See the [testing standard](../engineering/testing-standard.md).
6. **Run `./mvnw verify`.** It runs every test, the module boundary rules and
   SpotBugs. A green local `verify` is a green CI build.
7. **Check it by hand** through `/api/swagger-ui.html` for anything with an HTTP
   surface. Automated tests do not catch an unhelpful error message.
8. **Update the docs** that your change makes wrong: a new error code, a new
   environment variable, a new endpoint, a decision worth an
   [ADR](../architecture/adr/README.md).
9. **Open a pull request**, walk through the
   [definition of done](definition-of-done.md), and let CI run.
10. **Merge to `main`** once CI is green and a reviewer has approved.

## Where code goes

| What you are adding | Package |
|---------------------|---------|
| An endpoint | `modules/<name>/api` |
| A request or response record | `modules/<name>/api/dto` |
| A use case | `modules/<name>/application`, one class per use case |
| An entity or repository | `modules/<name>/domain` |
| Something another module needs | `modules/<name>/contract` |
| A call to an external system | `modules/<name>/infrastructure` |
| Entity to response conversion | `modules/<name>/mapper` |

Full rules: [module architecture](../architecture/module-architecture.md).

## Adding an endpoint to an existing module

1. Add the request and response records to `api/dto`, with Bean Validation.
2. Add a service class in `application` for the use case, `@Transactional`, with
   constructor injection.
3. Add the query to the repository interface in `domain` if you need a new one.
4. Add mapping to `mapper` so the entity does not reach the response.
5. Add the controller method: `@Operation`, the right status,
   `@SecurityRequirement` if it needs a token, `ApiResponse` as the return type.
6. Only if the endpoint must work without a token, add it to the matching list in
   `SecurityConfig` — and explain why in the pull request.
7. Tests: unit test for the rule, and a case in the module's context test. If you
   touched `SecurityConfig`, add a case to `SecurityRulesTest`.
8. `./mvnw verify`.

## Adding a module

Only `identity` exists, so this is the path for the second one.

1. **Create the package and declare the module:**

```java
// src/main/java/com/groceryecom/modules/vendor/package-info.java
@ApplicationModule(displayName = "Vendors")
package com.groceryecom.modules.vendor;

import org.springframework.modulith.ApplicationModule;
```

Without this file the package is not a module and none of the boundary rules
apply to it (`spring.modulith.detection-strategy=explicitly-annotated`).

2. **Declare the contract package,** even if it is empty for now:

```java
// src/main/java/com/groceryecom/modules/vendor/contract/package-info.java
@NamedInterface("contract")
package com.groceryecom.modules.vendor.contract;

import org.springframework.modulith.NamedInterface;
```

3. **Create `domain/`, `application/`, `api/dto/` and `mapper/`.** Create
   `infrastructure/` only when the module actually talks to an external system.
4. **Add the Flyway migration** for the module's tables:
   `V<n>__vendor_create_vendors.sql`. Rules in the
   [database standard](../engineering/database-standard.md) — every vendor-owned
   table gets `vendor_id NOT NULL` with indexes starting on it.
5. **Write the entities** in `domain`, extending `shared.persistence.BaseEntity`,
   plus a `public_id` UUID assigned in `@PrePersist`.
6. **Write the repository interfaces** in `domain`, extending `JpaRepository`.
7. **Write one service per use case** in `application`.
8. **Put in `contract` only what another module needs:** event records, enums, a
   service interface. Nothing that depends on `domain`.
9. **Add the module's row** to the table in
   [module-architecture.md](../architecture/module-architecture.md), moving it
   from Planned to Built.
10. **Add `modules.<name>` to the assertion in `ModularityTest.detectsExpectedModules`.**
11. **`./mvnw verify`.** `ModularityTest` now checks the new module's boundaries.

## Cross-module work

Two ways, no others.

**An event,** when the other module only needs to know something happened.
Publish a record from your `contract` package with
`ApplicationEventPublisher`; the listener uses `@ApplicationModuleListener`. The
publication is written to `event_publication` in the same transaction as the
business change
([ADR-0006](../architecture/adr/0006-transactional-outbox-for-module-events.md)).
Delivery is at-least-once, so **the listener must be idempotent** — handling the
same event twice must not send two emails or charge twice.

**A call,** when you need an answer now. The callee publishes an interface in its
`contract` package; you inject the interface. The implementation stays in
`application` and is not visible to you.

Prefer the event. A call couples the two modules at runtime.

## When a boundary check fails

`ModularityTest` failing means a dependency is wrong, not that the test is wrong.
The usual causes:

| Symptom | Cause | Fix |
|---------|-------|-----|
| Module A cannot see a type in module B | the type is not in B's `contract` | move it to `contract`, or use an event instead |
| `shared` or `platform` depends on `modules..` | a business type leaked into a foundation package | move the type into the module |
| A cycle between two modules | both call each other | turn one direction into an event |
| A new module is not listed | `package-info.java` with `@ApplicationModule` is missing | add it |

Never delete or `@Disabled` a case to get a green build.

## When to write an ADR

Write one when the decision is expensive to reverse, or when you deviate from a
documented standard on purpose. Do not write one for a library bump. Format and
the index are in [the ADR README](../architecture/adr/README.md).
