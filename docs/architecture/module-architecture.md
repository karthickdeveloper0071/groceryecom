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
| `identity` | Built | users, roles, tokens |
| `vendor` | Built | vendors, membership, approval; KYC, bank accounts and commission plans still to come |
| `billing` | Built | plans, vendor licences, subscription payments, expiry |
| `catalog` | Planned | categories, products, SKUs, images, prices |
| `inventory` | Planned | stock levels, reservations, stock movements |
| `checkout` | Planned | carts (Redis), checkout sessions |
| `order` | Planned | orders, per-vendor orders, items, status history |
| `payment` | Planned | payments, refunds, ledger, payouts, webhook events |
| `delivery` | Planned | addresses, delivery zones (PostGIS), slots, shipments |
| `notification` | Planned | templates, delivery log (events only, no public contract) |

Only `identity`, `vendor` and `billing` exist. Everything marked Planned is a name and a
scope, nothing more — do not assume any of its code, tables or endpoints exist.

## The identity module today

What it does: register (always as `CUSTOMER`), log in by username or email,
refresh tokens, read the current user, change password.

Roles: `CUSTOMER`, `VENDOR_OWNER`, `VENDOR_STAFF`, `ADMIN`. Vendor and admin
accounts are created through vendor onboarding and the admin console, never through
self-registration — `RegisterRequest` deliberately has no role field.

Its `contract` package publishes `Role` and `UserRegisteredEvent`. Nothing
consumes the event yet; the `notification` module will.

Users carry two identifiers: a `BIGINT` identity primary key that never leaves the
database, and a random `public_id` UUID used in URLs, API responses and token
subjects. Usernames and emails are stored lower-case with unique constraints and
`CHECK (x = lower(x))` guards in `V1__identity_create_users.sql`.

## The vendor module today

What it does: an account applies to open a store, the store's owner manages its
profile, and an admin approves, rejects or suspends it.

A store reaches the storefront only when **both** gates are open: an admin has
approved it, and it holds a live licence (`subscription_active`, maintained by the
billing module through `VendorPlanState`). Buying a plan also approves a store that
was still waiting, so a vendor can sign up and start selling in one sitting —
[ADR-0015](adr/0015-vendor-subscription-licensing.md).

Access to a store's data is decided by **membership**, not by the account's role.
`vendor_members` maps a user to a store as `OWNER` or `STAFF`; a user with no row
there has no access to that store at all. Every vendor-scoped request goes through
`VendorScope`, which looks up the membership and the store together, and returns
404 — not 403 — when there is none, so ids cannot be probed. That rule is
[ADR-0014](adr/0014-vendor-data-isolation.md), and
`VendorIsolationIntegrationTest` fails the build if it is bypassed.

Its `contract` package publishes `VendorStatus`, `VendorMemberRole`,
`VendorRegisteredEvent`, `VendorStatusChangedEvent` and `VendorDirectory` — the
interface every later module uses to ask "may this store trade?" and "what is this
user to this store?". Catalog, inventory, order and payment will use that and
nothing else of this module.

The membership row points at the identity module's `public_id`, with no foreign
key to `users`: modules are joined by what a module publishes, never by another
module's internal key. The cost (a deleted account can leave a membership behind)
is recorded in the ADR.

Not built yet: staff invitations, KYC documents, bank accounts and commission
plans.

## The billing module today

What it does: sells the licence a store needs to trade. Three plans (`STARTER`,
`GROWTH`, `SCALE`) seeded by `V6`, one licence row per store, and an append-only
record of every charge.

The shape of it:

- choosing a plan with trial days starts the store selling immediately and asks for
  nothing; a plan without them creates a charge and the store waits for the money;
- `ConfirmSubscriptionPaymentService` turns a payment into permission. It is written
  to be called twice — an admin clicking again, a gateway retrying a webhook — and
  the second call changes nothing;
- `SubscriptionExpiryService`, run hourly by `SubscriptionExpiryJob`, moves a licence
  to `PAST_DUE` (still selling, through its grace days) and then `EXPIRED` (off the
  storefront). It is safe to run on every instance at once;
- `BillingMapper` writes the sentence the vendor sees about their plan, so every
  client shows the same words.

Its `contract` package publishes `SubscriptionStatus`, the `SubscriptionEvents` and
`VendorEntitlements`. **Every module built after this asks
`VendorEntitlements.requireTrading(vendorId)` before letting a store act** — catalog
before another product, order before taking money. It throws 402 with the date the
plan ended, so the client shows a renew button rather than an error.

Billing depends on the vendor module's contract, and the vendor module knows nothing
about billing: one direction, so the two cannot form a cycle.

**Payments.** `PaymentGateway` is a port with two adapters in `infrastructure`:
Razorpay, and bank transfer confirmed by an admin. Which one takes a payment is
decided per payment from credentials an admin pasted into the console — installing
keys switches the platform to cards on the next payment, disabling falls back to
bank transfer rather than failing checkouts. The keys are stored encrypted with a
master key held in the environment, and no API ever returns one
([ADR-0016](adr/0016-gateway-credentials-in-the-database.md), setup in
[razorpay-setup.md](../development/razorpay-setup.md)).

A licence is granted only by `ConfirmSubscriptionPaymentService`, reached either by
an admin confirming a transfer or by Razorpay's signed webhook. The browser's own
"payment succeeded" callback grants nothing: a page can be closed, refreshed or
faked.

**Vendor payouts.** The other direction of money: a customer pays, and most of it
belongs to the vendor. `VendorPayouts.splitFor(vendorId, orderTotal)` is what the
order module will call — it applies the commission from the store's plan, rounds it
**down**, and gives the vendor what remains, so the two parts always add up to
exactly what the customer paid. The destination is a linked account at the payment
provider: the bank account number is passed to the provider and **never stored
here**, only its id and the last four digits ([ADR-0017](adr/0017-vendor-payouts-and-payment-splitting.md)).
A store whose account is not verified yet keeps selling; its share is held, not
lost.

Not built yet: invoices and receipts, proration when changing plan mid-period,
renewal reminders (they need the notification module), the ledger that records held
vendor earnings, and an admin UI for plan prices — a price change is still a
migration. Neither Razorpay adapter has run against the real Razorpay API.

## Adding a module

Step by step in
[feature-development-process.md](../development/feature-development-process.md).
