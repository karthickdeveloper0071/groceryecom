# ADR-0015: Vendor licensing as a subscription, enforced by two gates

- **Status:** Accepted as a decision; the code that implemented it was removed when the
  business modules were cleared out, so treat this as the reasoning to reuse when the
  feature is built again, not as a description of code that exists
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

The platform's own revenue comes from vendors paying to sell on it. A store buys a
plan, trades while the plan is alive, and stops when it is not. Three things had to
be decided at once, because choosing one badly makes the others impossible:

1. **Who opens a store.** Until now an admin approved every application by hand.
   That does not survive 100 vendors signing up, and it makes the platform's first
   impression a wait. The wanted behaviour is: buy a plan, start selling.
2. **What happens when a plan runs out.** A store must stop selling, be told in
   words it can act on, and be able to come back by paying. "Show that your plan is
   expired" is a product requirement, not a log line.
3. **Which module owns any of this**, given that the vendor module already exists
   and Spring Modulith fails the build on a dependency cycle between two modules.

There is no payment gateway account yet, and there may be several later (Stripe,
Razorpay, Billplz, or a bank transfer for a chain that insists). Whatever is chosen,
settlement arrives out-of-band — a webhook, an admin confirming a transfer — and
arrives **more than once**. A subscription that adds a month per delivery of the
same webhook is the platform quietly overcharging its vendors.

## Decision

**A separate `billing` module owns licensing; the vendor module keeps a two-field
copy of the answer; a store sells only when both gates are open.**

- **Two gates.** `Vendor.isSellable()` is `status == APPROVED && subscriptionActive`.
  They close for different reasons — conduct and payment — and neither overrides the
  other. A payment opens a store that was waiting for approval, and does **not**
  reopen one an admin suspended.
- **Automatic activation.** `VendorPlanState.planActivated` turns a `PENDING`
  application into an approved store. Paying is the approval. It is audited with the
  platform as the actor, because "who let this store on?" must have an answer even
  when nobody clicked anything.
- **One dependency direction.** Billing depends on the vendor module's contract;
  the vendor module knows nothing about billing. The storefront therefore does not
  query billing tables on every read — it reads `subscription_active` on the vendor
  row, which billing maintains through `VendorPlanState`.
- **Grace before expiry.** `TRIALING/ACTIVE → PAST_DUE → EXPIRED`. During grace
  (3 days, configurable) the store keeps selling and is told payment failed. A shop
  switched off the morning a card expires is a shop that leaves the platform.
- **The message is part of the contract.** `BillingMapper` produces the sentence
  shown to the vendor ("Your Growth plan expired on 4 April 2026. Renew it to start
  selling again."), so every client says the same thing and the words change in one
  place. `SubscriptionStatus` is for code; the sentence is for a shopkeeper.
- **402, not 403,** for a store whose licence ran out (`SUBSCRIPTION_EXPIRED`), and
  402 `SUBSCRIPTION_REQUIRED` for one that never bought a plan. A client that cannot
  tell "not allowed" from "plan expired" shows an error page where a renew button
  belongs.
- **A payment gateway port.** `PaymentGateway` has one implementation today,
  `ManualPaymentGateway` (bank transfer, admin confirms). A real provider is another
  implementation plus a webhook endpoint that calls the same
  `ConfirmSubscriptionPaymentService`.
- **Settlement is idempotent by construction.** `provider_reference` is unique, and
  the payment's own status decides: the first confirmation extends the period, every
  repeat returns the same licence and changes nothing.
- **Renewal extends one row.** One `vendor_subscriptions` row per store, so "is this
  store paid up?" cannot return two answers. What was charged lives in
  `subscription_payments`, which is append-only.
- **Plans are data, not code.** Prices, limits and trial days are rows seeded by a
  migration; a price change is an INSERT, not a release.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| Keep manual admin approval as the only way to open a store | a human sees every vendor before customers do | does not scale past a handful of sign-ups, and makes the platform's first impression a wait. Retained as an override: admins can still reject and suspend |
| Vendor module owns subscriptions too | no new module, no cross-module call | licensing has its own lifecycle, its own money and its own failure modes; merging them makes the vendor module the place where everything lands. It also does not remove the dependency question, it hides it |
| Vendor module listens to billing events instead of billing calling it | events are already used elsewhere and the outbox is in place | the vendor module would have to import billing's event types while billing imports vendor's contract: a cycle, which `modules.verify()` rejects. Events flow the other way, to notification and catalog, where the direction is one-way |
| No stored flag; the storefront asks billing on every read | one source of truth, no drift | every public store read becomes a second module's query, for a fact that changes a few times a year. The copy is the cheaper trade, and the expiry pass re-asserts it |
| No grace period: expire exactly at the period end | simpler, and stricter about payment | a card that expires overnight closes a shop during its morning trade. The grace days cost the platform three days of one subscription and save the vendor relationship |
| Charge automatically with a stored card | fewer expiries | needs a gateway, stored payment methods and PCI scope that do not exist yet. The port is in place for when they do |

## Consequences

- A vendor can sign up, choose a plan and be selling in one sitting, with no admin
  in the loop. Admins keep the power to reject and suspend, which is the part that
  needs a human.
- The `subscription_active` flag can drift if a call to the vendor module is lost
  (the process dies between two transactions). The hourly expiry pass re-asserts it
  from billing's own state, so drift is corrected within the hour rather than
  needing a person. Enforcement for **write** paths goes to
  `VendorEntitlements.requireTrading`, which reads billing directly and cannot drift.
- Every module built after this must ask `VendorEntitlements.requireTrading` before
  letting a store act. It is one line, and forgetting it means an unpaid store keeps
  selling; the code review checklist asks for it.
- The expiry pass runs on every instance with no lock. That is safe because each
  transition is guarded by the status it starts from. The first job here that sends
  email or moves money needs a lock (ShedLock) **before** it is written.
- Two kinds of "payment" now exist in the codebase: this module (vendors paying the
  platform) and the planned `payment` module (customers paying vendors, and payouts).
  They are deliberately separate; the naming has to stay clear or they will merge by
  accident.
- Plans reference data lives in a migration, so a price change is a migration too.
  An admin UI for plans is future work; until then, pricing changes are a deploy.
