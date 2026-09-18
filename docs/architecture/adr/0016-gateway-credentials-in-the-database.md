# ADR-0016: Payment gateway credentials in the database, encrypted, edited by an admin

- **Status:** Accepted as a decision; the code that implemented it was removed when the
  business modules were cleared out, so treat this as the reasoning to reuse when the
  feature is built again, not as a description of code that exists
- **Date:** 2026-09-18
- **Deciders:** Backend team

## Context

Vendor licensing ([ADR-0015](0015-vendor-subscription-licensing.md)) needs a real
payment gateway. Razorpay was chosen. Its keys have to reach the application
somehow, and the sequence matters: **the software exists before the Razorpay
account does**. The platform owner will sign up later, paste a test key, try it,
and swap in live keys on launch day. After that, Razorpay occasionally asks for a
rotation, and an outage may mean switching the gateway off for an afternoon.

The default answer — an environment variable — makes every one of those a
deployment, performed by whoever can deploy, at a time that suits the deployment
rather than the business. It also puts a live secret in a place that is easy to
copy: a CI variable, a compose file, a shell history, a screenshot of a terminal.

Against that, a secret in a database row is a secret in backups, in replicas, in
any dump a developer takes for debugging, and in reach of any SQL injection flaw
that gets past the DML-only runtime role ([ADR-0013](0013-least-privilege-database-roles.md)).

The security standard for this project says plainly: never store secrets in Git,
never log them, never return them to clients. It does not say never store them —
it says never store them *carelessly*.

## Decision

**Credentials live in `payment_gateway_credentials`, encrypted with a key that does
not, and are managed by an admin through the API.**

- **Encrypted at rest with AES-256-GCM** (`platform.crypto.SecretCipher`). GCM
  authenticates as well as encrypts, so an altered row fails to decrypt instead of
  quietly yielding a different key. A fresh nonce per encryption, stored with the
  ciphertext.
- **The master key is not in the database.** It comes from `SECRETS_MASTER_KEY` in
  the environment, so a dump, a backup or a replica is useless on its own. The
  database and the key that protects it never travel together.
- **The key id is not encrypted.** Razorpay's key id is public — the browser needs
  it to open Checkout — and encrypting a public value only makes it harder to
  support.
- **No API returns a secret, ever.** The admin console sees the last four
  characters of the key id, the mode, whether webhooks can be verified, and who
  changed it last. A secret that can be read back is a secret that leaks through a
  screenshot or an over-broad admin account.
- **Every change is audited** with the admin's id, the provider, the mode and the
  key hint — never the secret itself.
- **The gateway is chosen per payment** from these rows. Installing keys switches
  the platform to Razorpay on the next payment; disabling falls back to bank
  transfer confirmed by an admin, rather than failing vendors' checkouts.
- **`TEST` or `LIVE` is explicit**, because "why has no money arrived?" is almost
  always test keys left in place after launch, and a screen that says so answers it
  in seconds.
- **The webhook secret is stored the same way.** With none stored, webhook
  verification is impossible, so every callback is refused rather than trusted.

## Alternatives considered

| Option | For | Against |
|--------|-----|---------|
| Environment variables only | the standard answer; no secret in the database; no new crypto | every key change is a deployment. The platform owner cannot go live, rotate a key or switch a gateway off without an engineer. Also spreads the secret across CI, compose files and shells, which is not obviously safer than one encrypted column |
| A secrets manager (AWS/GCP/Vault), read at runtime | keys never touch our storage; rotation and access control are the manager's problem | no cloud account is chosen yet, and this would tie the platform to one. Worth revisiting at that point: the `SecretCipher` seam means swapping the store is one class |
| Plaintext column, protected by database permissions alone | simplest possible | every backup, replica and debugging dump then holds live keys. One careless copy is a compromise, and backups are copied by definition |
| Keys in the database, encryption key also in the database | no environment variable to manage | protects nothing: whoever reads one table reads the other |
| A per-vendor gateway account | vendors could take their own payments | this module is about vendors paying *the platform*, not customers paying vendors. That is the planned `payment` module, a different problem |

## Consequences

- Going live, rotating a key and turning the gateway off are screens, and take
  effect on the next payment with no restart.
- **Rotating `SECRETS_MASTER_KEY` makes stored secrets unreadable.** They must be
  entered again in the admin console. That is deliberate — the alternative is a key
  that travels with its own ciphertext — and it is written down in the runbook
  rather than discovered during an incident.
- An `ADMIN` account can install keys, so an admin account takeover can redirect
  future payments. It cannot read the existing keys, and every change is audited.
  Admin accounts are created by hand, not by self-registration.
- The crypto is ours to maintain: 40 lines, one algorithm, one master key. It must
  not grow into a general-purpose utility. Passwords stay hashed with BCrypt and
  tokens stay signed; nothing else may start "just encrypting" things.
- `SECRETS_MASTER_KEY` becomes a required variable in every shared environment,
  alongside `JWT_SECRET`. The local default is published in Git and protects
  nothing, which the configuration comment says in as many words.
