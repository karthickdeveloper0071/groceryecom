# ADR-0017: Vendor payouts by gateway split, with no bank details stored

- **Status:** Accepted
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

A customer buys from a vendor, pays online, and most of that money belongs to the
vendor. The platform keeps a commission — the rate is already on the plan the
store subscribes to ([ADR-0015](0015-vendor-subscription-licensing.md)). Two
things follow, and both had to be decided before the order module is written,
because both change its data model:

1. **Where the money physically goes.** Either the platform collects everything
   into its own account and transfers to vendors later, or the gateway splits the
   payment at the moment it is taken.
2. **What the platform stores to make that possible.** A payout destination means
   bank account numbers, which is the most dangerous class of data this system
   could hold: a leaked account number plus a name is enough for fraud, and a
   changed one is what an account takeover exists to achieve.

There is also a timing problem. A vendor will set up their payout details before
the platform owner has finished signing up with Razorpay, and Razorpay verifies a
linked account on its own schedule.

## Decision

**Razorpay Route splits each payment; the platform stores the gateway's id for the
destination and four digits, and never the account number.**

- `VendorPayoutGateway` registers a vendor's bank account with the provider, which
  returns an id for the destination. The account number and IFSC are passed
  through and **never stored, logged or returned**. `SensitiveData` masks them in
  the request log; `payment_gateway_credentials` keeps them out of the database by
  not having a column for them.
- `vendor_payout_accounts` holds the provider's id, the account holder's name, the
  bank name and the **last four digits** — enough for a vendor to recognise their
  own account and useless to anybody else.
- `VendorPayouts.splitFor(vendorId, orderTotal)` is what the order module will
  call. It returns the commission, the vendor's share and where that share goes.
- **The commission is rounded down and the vendor's share is what remains.** Not a
  second percentage: the two parts then add to exactly what the customer paid,
  whatever the amount. A split that loses a sen per order loses real money at a
  million orders, and nobody can say where it went.
- **A store with no verified destination still sells.** Its share is *held*, not
  lost, and paid when verification completes. Blocking orders over a form would
  cost the vendor customers, and the platform its reputation with vendors.
- **A store that may not trade may not be paid.** `splitFor` calls
  `requireTrading`, so an expired licence fails with the same 402 the storefront
  gives, in the same words.
- **Changing bank details always restarts verification.** A redirected payout is
  the goal of an account takeover, and the audit line records who changed it, when,
  and the last four digits of what it became.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| Store bank details and pay vendors by our own bank transfers | no dependence on a gateway's payout product; works with any bank | the platform then holds account numbers for 100 vendors, which is the data a breach is worth having. It also makes payouts a manual operation with its own reconciliation, and money held on our books is money we are responsible for |
| Collect everything, transfer to vendors on a schedule | one integration; simple accounting at the moment of payment | the platform holds other people's money, which in most jurisdictions is a regulated activity. Route keeps the vendor's share the vendor's from the moment it is paid |
| Store the account number encrypted, like the gateway keys | one mechanism for all secrets; payouts work without a gateway product | encryption protects a secret we do not need to keep. The safest account number is the one that was never stored, and the gateway has to hold it anyway |
| Round the commission to the nearest unit | feels fairer, splits the difference | half the time it takes a sen the vendor never agreed to. Rounding down is a rule that is always defensible, and the vendor is never short-changed |
| Block orders until a payout account is verified | the platform never owes money it cannot send | verification is the gateway's schedule, not the vendor's. A store that is open, selling and being paid next week is better for everyone than a store that cannot open |

## Consequences

- Nothing in the platform's database can be used to move a vendor's money. A dump
  gives an attacker four digits and a bank name.
- Money owed to an unverified vendor accumulates as an obligation with no ledger
  entry yet. The order and payment modules must record it as held, and the first
  payout report must reconcile it. **This is the sharpest edge left open by this
  ADR**, and it belongs to the order module's design.
- The commission rate is read per split, so a vendor who upgrades their plan pays
  the new rate from the next order, with nothing to redeploy.
- Splitting ties the order flow to a gateway that supports it. Route does; a
  provider that does not would force option 2, and the `VendorPayoutGateway` port
  is where that change would be absorbed.
- Razorpay Route linked accounts are an Indian product; the currency question in
  [razorpay-setup.md](../../development/razorpay-setup.md) applies here too, and
  more sharply — payouts settle where the provider settles.
