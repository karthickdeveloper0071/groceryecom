# Razorpay setup

How the platform takes card payments for vendor plans, what you paste where, and what
the frontend has to do. No key is ever put in a config file or a deployment: you paste
them once in the admin console and they take effect on the next payment.

Design and reasoning: [ADR-0016](../architecture/adr/0016-gateway-credentials-in-the-database.md).

## What you do once, in the Razorpay dashboard

1. Create the Razorpay account and finish KYC.
2. **Settings → API Keys → Generate Key.** You get a **Key Id** (`rzp_test_...` or
   `rzp_live_...`) and a **Key Secret**. The secret is shown once; if you lose it,
   generate a new pair.
3. **Settings → Webhooks → Add New Webhook.**
   - URL: `https://your-api-domain/api/v1/billing/webhooks/razorpay`
   - Secret: any long random string you choose. You will paste the same value into the
     admin console — it is what proves a callback really came from Razorpay.
   - Active events: **`order.paid`** and **`payment.captured`**. The platform ignores
     everything else, so subscribing to more only adds noise.

## What you do once, in the admin console

Sign in as an `ADMIN` and save the keys:

```http
PUT /api/v1/admin/payment-gateways/razorpay
Authorization: Bearer <admin access token>
Content-Type: application/json

{
  "mode": "TEST",
  "keyId": "rzp_test_XXXXXXXXXXXX",
  "keySecret": "<key secret from the dashboard>",
  "webhookSecret": "<the webhook secret you chose>"
}
```

That is the whole switch-on. The next vendor who chooses a paid plan is sent to Razorpay
instead of being asked for a bank transfer. No restart, no deploy.

Going live later is the same call with `"mode": "LIVE"` and the live keys.

| Call | What it does |
|------|--------------|
| `GET /api/v1/admin/payment-gateways` | Which gateways are installed, in which mode, and whether webhooks can be verified. **Never returns a secret** — the key id is shown as its last four characters |
| `PUT /api/v1/admin/payment-gateways/razorpay` | Install or replace keys. Leave `webhookSecret` out when rotating only the API key, and the stored one is kept |
| `POST /api/v1/admin/payment-gateways/razorpay/disable` | Stop using Razorpay without losing the keys. Payments fall back to bank transfer confirmed by an admin — for a gateway outage, or to back out of a launch |
| `POST /api/v1/admin/payment-gateways/razorpay/enable` | Use it again |

Every one of these is audited with the admin who did it.

## What the frontend does

**1. Show the plans.** `GET /api/v1/plans` — public, no token.

**2. The vendor chooses one.**

```http
POST /api/v1/vendors/{vendorId}/subscription
Authorization: Bearer <vendor access token>

{ "planCode": "GROWTH" }
```

The response either has no `payment` (a free trial — the store is already selling) or
carries what Checkout needs:

```json
{
  "data": {
    "subscription": { "status": "PENDING_PAYMENT", "trading": false, "message": "..." },
    "payment": {
      "reference": "order_NpQrSt123",
      "amountMinor": 14900,
      "amount": 149.00,
      "currency": "MYR",
      "provider": "RAZORPAY",
      "publicKey": "rzp_test_XXXXXXXXXXXX",
      "instructions": "Pay 149.00 MYR with card, UPI or netbanking to start your plan."
    }
  }
}
```

**3. Open Razorpay Checkout** with `publicKey` as `key` and `reference` as `order_id`:

```html
<script src="https://checkout.razorpay.com/v1/checkout.js"></script>
```

```js
const { payment, subscription } = response.data;

if (!payment) {
  // A trial: nothing to pay, the store is already selling
  return showDashboard(subscription);
}

new window.Razorpay({
  key: payment.publicKey,          // public; it identifies the merchant, it authorises nothing
  order_id: payment.reference,     // the order the backend created
  amount: payment.amountMinor,     // display only; Razorpay charges the order's amount
  currency: payment.currency,
  name: "GroceryEcom",
  description: "Vendor plan",
  handler() {
    // Do NOT mark the store as paid here. This callback only means the browser got
    // that far. The licence starts when Razorpay's webhook reaches the backend, which
    // is usually within a second or two.
    pollSubscriptionUntilActive();
  },
}).open();
```

**4. Show the licence state.** `GET /api/v1/vendors/{vendorId}/subscription` returns the
status **and the sentence to display**:

| Status | What the vendor sees (`message`) |
|--------|----------------------------------|
| `TRIALING` | "You are on a free trial of the Starter plan until 15 March 2026. Pay before then to keep selling." |
| `ACTIVE` | "Your Growth plan renews on 1 April 2026." |
| `PAST_DUE` | "We have not received payment… Your store stays open until 4 April 2026, then it will stop selling." |
| `EXPIRED` | "Your Growth plan expired on 4 April 2026 and your store is no longer selling. Renew to put it back on the storefront." |

Show `message` rather than writing your own copy per status: the words change in one
place and every screen stays consistent.

**5. Handle 402.** Once a licence lapses, vendor actions return `402` with
`SUBSCRIPTION_EXPIRED` or `SUBSCRIPTION_REQUIRED` and a message carrying the date. Route
that to a renew screen, not to a generic error page. That is the whole reason the status
is 402 and not 403.

## Why the browser is not trusted

The `handler` callback runs in a page the vendor controls. It can be closed before it
fires, refreshed twice, or crafted by hand. The only thing that grants a licence is the
signed webhook, verified server-side against the stored secret. Everything else is
display.

## Environment variables

| Variable | Needed | Notes |
|----------|--------|-------|
| `SECRETS_MASTER_KEY` | **Yes, in every shared environment** | Encrypts the stored gateway secrets. At least 32 bytes: `openssl rand -base64 32`. Keep it in a secrets manager, different per environment |
| `RAZORPAY_API_URL` | No | Defaults to `https://api.razorpay.com`. Exists so a test can point at a stub |

Rotating `SECRETS_MASTER_KEY` makes the stored gateway secrets unreadable — they are
encrypted with the old one. After a rotation, paste the keys again in the admin console.
Nothing else in the platform is affected.

## Currency

Razorpay settles **INR** for a standard Indian account; other currencies require
International Payments on the account. The plans seeded by `V6` are priced in **MYR**, so
before going live either enable international payments or re-price the plans in INR with
a new migration. An order in an unsupported currency is rejected by Razorpay when it is
created, which surfaces as a 503 to the vendor and an error in the log.

## Testing without real money

Use `mode: TEST` with `rzp_test_...` keys and Razorpay's test cards. To test the webhook
without a public URL, use the dashboard's "Send test webhook", or expose your local API
with a tunnel and set that URL in the webhook settings.

The build already covers the parts that are easy to get wrong, without touching Razorpay:
`RazorpaySignatureTest` (forged and altered callbacks), `RazorpayPaymentGatewayTest`
(what is actually sent, and what a Razorpay outage does), and
`RazorpayWebhookIntegrationTest` (keys installed → order → signed webhook → store
selling, plus a duplicate delivery that must not buy a second month).

## When something is wrong

| Symptom | Cause and fix |
|---------|---------------|
| Vendors are asked for a bank transfer, not a card | No keys installed, or the gateway is disabled. `GET /api/v1/admin/payment-gateways` |
| `401` on the webhook, money taken but store not selling | The webhook secret in the console does not match the one in the Razorpay dashboard. Save it again; Razorpay retries, so the licence starts once it matches |
| `503 PAYMENT_GATEWAY_UNAVAILABLE` when choosing a plan | Razorpay rejected the order. The reason is in the application log (never in the response): usually wrong keys, an unsupported currency, or an account not yet activated |
| `SECRET_UNREADABLE` in the logs | `SECRETS_MASTER_KEY` changed. Paste the gateway keys again |
| Payment succeeded, licence still `PENDING_PAYMENT` | The webhook never arrived. Check Razorpay's webhook delivery log, and that the URL is reachable from the internet. An admin can settle it meanwhile with `POST /api/v1/billing/payments/{reference}/confirm` |
