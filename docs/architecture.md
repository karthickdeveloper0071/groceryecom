# Architecture

## Shape

One Spring Boot application (a modular monolith), deployed as several identical, stateless instances behind a load balancer. Business capabilities live in modules with strict walls, so a module can later move into its own service without a rewrite.

Stage 1 sizing, the database comparison and the system diagram are in the architecture plan: https://claude.ai/artifact/RwKXCv3jsPSN79133qDHEt

## Layers and dependency rules

```
modules.*  ──►  platform  ──►  shared
    │                            ▲
    └────────────────────────────┘
```

| Package | Contains | May depend on |
|---------|----------|---------------|
| `shared` | Value types (`Money`), `BaseEntity`, `ApiResponse`, base exceptions | Nothing in this project |
| `platform` | Security, error handling, request ids, OpenAPI, cache and messaging config | `shared` |
| `modules.<name>` | One business capability | `shared`, `platform`, and other modules' `api` packages only |

These rules are checked on every build by `ModularityTest`:

- a module using another module's `internal` or `web` package fails the build;
- cycles between modules fail the build;
- `shared` or `platform` depending on any business module fails the build.

## Module layout

```
modules/<name>/
├── package-info.java     @ApplicationModule
├── api/                  public types: service interfaces, events, enums (@NamedInterface("api"))
├── internal/             entities, repositories, service implementations
└── web/                  controllers; dto/ holds request and response records
```

Modules talk to each other in two ways:

1. **Calls** to another module's `api` types, when the caller needs an answer now.
2. **Events** (records in `api`), published with `ApplicationEventPublisher` and handled with `@ApplicationModuleListener`. Publications are stored in the `event_publication` table in the same transaction as the business change (transactional outbox), so an event is never lost if a listener or the broker fails.

## Modules

| Module | Status | Owns |
|--------|--------|------|
| identity | Built | users, roles, tokens |
| vendor | Planned | vendors, staff membership, KYC, bank accounts, commission plans |
| catalog | Planned | categories, products, SKUs, images, prices |
| inventory | Planned | stock levels, reservations, stock movements |
| checkout | Planned | carts (Redis), checkout sessions |
| order | Planned | orders, per-vendor orders, items, status history |
| payment | Planned | payments, refunds, ledger, payouts, webhook events |
| delivery | Planned | addresses, delivery zones (PostGIS), slots, shipments |
| notification | Planned | templates, delivery log (events only, no public API) |

## Data rules

- **Money:** `BIGINT` in the smallest currency unit plus a currency code, handled with `shared.money.Money`. Never `double`.
- **IDs:** `BIGINT` identity keys inside the database; a random `UUID public_id` in URLs, API responses and tokens.
- **Vendor ownership:** every vendor-owned row has `vendor_id NOT NULL`, and indexes start with it.
- **Time:** `TIMESTAMPTZ` columns, `Instant` in Java, UTC everywhere.
- **History:** orders and payments are never deleted; status changes append to history tables.
- **Order snapshots:** order items copy name, unit, price and tax at purchase time.
- **Schema:** only Flyway changes the schema. Hibernate runs with `ddl-auto=validate`, so a mismatch stops startup.

## Security

- Stateless JWTs signed with HS512. Access tokens last 15 minutes and refresh tokens 7 days. A `token_type` claim stops one being used as the other.
- Every endpoint requires authentication unless listed in `SecurityConfig`.
- Passwords are hashed through Spring's delegating encoder (`{bcrypt}` prefix), so the algorithm can be upgraded later.
- Self-registration always creates a `CUSTOMER`. Vendor and admin accounts come from their own flows.
- Error responses never include stack traces or internal messages.
