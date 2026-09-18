# Module architecture

How a business module is laid out, what it may depend on, and how modules talk to
each other. The rules here are checked by `ModularityTest` on every build.

## Layers

```
modules.*  ──►  platform  ──►  shared
     │                            ▲
     └────────────────────────────┘
```

| Package | Contains | May depend on |
|---------|----------|---------------|
| `shared` | `Money`, `BaseEntity`, `ApiResponse`, `ApplicationException` and its subclasses | nothing in this project |
| `platform` | `security` (JWT, `SecurityConfig`, `AuthenticatedUser`), `web` (`GlobalExceptionHandler`, `RequestIdFilter`, `OpenApiConfig`), `cache` (`CacheConfig`) | `shared` |
| `modules.<name>` | one business capability | `shared`, `platform`, and other modules' `contract` packages |

`shared` and `platform` are declared `@ApplicationModule(type = OPEN)`, so any
package inside them is usable. Business modules are closed.

## Standard module layout

Every business module looks like this:

```
modules/<name>/
├── package-info.java      @ApplicationModule
├── contract/              types other modules may use: events, enums,
│                          cross-module service interfaces (@NamedInterface("contract"))
├── api/                   REST layer: controllers + dto/ (request and response records)
├── application/           one service class per use case
│                          (RegisterCustomerService, LoginService, ...)
├── domain/                entities, Spring Data repository interfaces,
│                          domain rules and validators
├── infrastructure/        adapters to external systems (payment gateway,
│                          object storage) — only when the module needs one
└── mapper/                entity → response mapping
```

Rules:

- **Dependency direction inside a module is `api → application → domain`.** A
  controller never touches a repository; `domain` never imports anything from
  `api` or `application`.
- **`mapper` is used by `application` and `api`.** Its job is keeping entities
  out of API responses.
- **`contract` holds nothing that depends on `domain`.** It is a leaf: records,
  enums, interfaces. If a contract type needed an entity, the boundary is wrong.
- **`infrastructure` is created only when a module actually talks to an external
  system.** Do not create an empty package for symmetry.
- **`api` is the REST layer, `contract` is the cross-module contract.** These two
  words are easy to mix up; [ADR-0003](adr/0003-package-and-module-structure.md)
  explains the naming and why it differs from the generic company standard.

Entities are the domain model and Spring Data repository interfaces are the port;
there is no separate `*Entity` / `*RepositoryImpl` / domain-object triple. The
reasoning and the revisit trigger are in
[ADR-0004](adr/0004-jpa-entities-as-domain-model.md).

## Visibility

Keep classes package-private unless another package needs them. `AuthController`
is package-private; so are the service constructors. Only `contract` types are
`public` for other modules' benefit; DTO records in `api/dto` are public because
Jackson and springdoc read them.

## How modules talk

Two ways, and only two.

**1. Calls,** when the caller needs an answer now. The callee publishes an
interface in its `contract` package; the caller injects that interface. The
implementation stays in `application` and is invisible outside the module.

**2. Events,** when the caller only needs to announce that something happened.
Publish a record from the module's `contract` package:

```java
// modules/identity/contract/UserRegisteredEvent.java
public record UserRegisteredEvent(UUID userId, String email, Role role) { }

// inside the publishing service
events.publishEvent(new UserRegisteredEvent(saved.getPublicId(), saved.getEmail(), saved.getRole()));
```

Handle it in another module with `@ApplicationModuleListener`. The publication row
goes into `event_publication` in the same transaction as the business change, so a
listener or broker failure does not lose the event — see
[ADR-0006](adr/0006-transactional-outbox-for-module-events.md).

Prefer events. A call couples two modules at runtime; an event does not.

## Enforcement

`src/test/java/com/groceryecom/ModularityTest.java` fails the build when:

| Check | What it catches |
|-------|-----------------|
| `modules.verify()` | a module reaching past another module's `contract` package; cycles between modules |
| ArchUnit rule | anything in `shared..` or `platform..` depending on `com.groceryecom.modules..` |
| `detectsExpectedModules` | `shared` or `platform` disappearing or being renamed silently |

`spring.modulith.detection-strategy=explicitly-annotated`: only packages annotated
`@ApplicationModule` are modules. A new module without `package-info.java` is
invisible to the checks, which is the most common way to accidentally escape them.

## Modules

| Module | Status | Owns |
|--------|--------|------|
| `identity` | Planned | users, roles, tokens |
| `vendor` | Planned | vendors, branches, membership |
| `billing` | Planned | plans, vendor licences, payments |
| `catalog` | Planned | categories, products, SKUs, images, prices |
| `inventory` | Planned | stock levels, reservations, stock movements |
| `checkout` | Planned | carts (Redis), checkout sessions |
| `order` | Planned | orders, per-vendor orders, items, status history |
| `payment` | Planned | payments, refunds, ledger, payouts, webhook events |
| `delivery` | Planned | addresses, delivery zones (PostGIS), slots, shipments |
| `notification` | Planned | templates, delivery log (events only, no public contract) |

**No business module exists yet.** Every row above is a name and a
scope — do not assume any of its code, tables or endpoints exist. What does exist is the
foundation they will be built on: `shared`, `platform`, the database setup and the rules
in this document.

## Adding a module

Step by step in
[feature-development-process.md](../development/feature-development-process.md).
