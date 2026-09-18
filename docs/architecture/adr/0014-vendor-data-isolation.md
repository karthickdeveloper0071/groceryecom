# ADR-0014: Vendor data isolation enforced in the application, on a shared schema

- **Status:** Accepted as a decision; the code that implemented it was removed when the
  business modules were cleared out, so treat this as the reasoning to reuse when the
  feature is built again, not as a description of code that exists
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Stage 1 is 100 vendors on one PostgreSQL database
([ADR-0002](0002-postgresql-as-primary-database.md)). Every module still to be
built is vendor-scoped: products, stock, orders, payouts, delivery zones. One
vendor reading or changing another vendor's rows is the failure that ends the
business — worse than downtime, because it is quiet, and because the vendors are
competitors with each other.

The dangerous request is ordinary, not exotic: an authenticated vendor sends a
valid token and a different vendor's id in the URL. Authentication succeeds. The
account's platform role (`VENDOR_OWNER`) is correct. Nothing about the request is
malformed. Only an ownership check stands between that request and someone else's
data, and a role cannot express "this store and no other".

There is a second question underneath: what does "belongs to a vendor" mean for a
user? An account's role says what kind of person it is, not which store it acts
for. A chain could employ staff across stores later.

## Decision

**Isolation is a check in the application, on a shared schema, and it goes through
one class.**

1. Every vendor-owned table carries `vendor_id` from its first migration. Adding
   it later means a rewrite, and a window where old rows have no owner.
2. Membership, not role, decides access: `vendor_members` maps a user to a store
   with a `VendorMemberRole` (`OWNER`, `STAFF`). No row, no access. The platform
   role stays what kind of account it is.
3. Every vendor-scoped request goes through `VendorScope.require(user, vendorId,
   role)`, which loads the membership **and** the vendor in one query keyed by
   both ids, and returns the vendor only if the caller has one. Services do not
   load a vendor by id themselves; that habit is what this class removes.
4. A caller without a membership gets **404, not 403**. 403 confirms the store
   exists, which lets an outsider enumerate the platform's vendors by trying ids.
5. Other modules never read the vendor tables. They ask `VendorDirectory`
   ("may this store trade?", "what is this user to this store?"), which is the
   vendor module's whole public surface.
6. `vendor_members` references the user by the identity module's **public id**,
   with no foreign key to `users`. Modules are joined by what a module publishes,
   never by another module's internal key.

`VendorIsolationIntegrationTest` proves the rule over HTTP with two real stores
and a real admin: editing another store's profile, reading its pending
application, and approving your own store all fail, while the owner's own
operations succeed. Replacing `VendorScope.require` with a plain
`findByPublicId` — the mistake this is all built to prevent — turns two of those
into 200s and fails the build.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| A schema or database per vendor | isolation enforced by the database; a bug cannot cross it | 100 schemas to migrate on every release, connection pools per tenant, and cross-vendor reporting becomes a distributed query. Stage 1 has one primary; this trades a solvable problem for an operational one |
| PostgreSQL row-level security with a session variable | the database refuses the leak even if code forgets | the policy depends on `SET LOCAL` running on exactly the right connection; a pooled connection that misses it silently sees everything. Testing it needs the runtime role to be non-superuser everywhere. Worth revisiting as a **second** layer once the isolation rule is settled — as defence in depth, not as the primary check |
| A Hibernate filter or `@Where` on every entity | one annotation per entity, applied automatically | silently skipped by native queries, `EntityManager.find`, projections and joins from another entity. It hides the check instead of making it visible, and what hides can be forgotten |
| Role-based only (`VENDOR_OWNER` may edit vendors) | no extra table | says nothing about *which* store. Every vendor owner could edit every store. This is the bug, written as a design |

## Consequences

- The check is code, so it can be forgotten. That is the real cost, and it is why
  it is concentrated in one class with a test that fails loudly, rather than
  spread across services. Every new vendor-scoped endpoint must go through
  `VendorScope`, and the code review checklist asks for it.
- Cross-vendor queries (admin views, reporting) stay simple: one database, no
  fan-out.
- No foreign key from `vendor_members` to `users` means a deleted user could
  leave a membership behind. The application keeps this consistent, and identity
  will publish a deletion event the vendor module reacts to when account deletion
  is built. Recorded here so it is a known debt, not a discovery.
- Row-level security remains available later as a second layer, without changing
  any of the above.
- One store per account for now. A second store means staff management, store
  switching and per-store permissions, which is a feature to design rather than a
  side effect of applying twice.
