-- What the vendor module needs to know about a store's licence, and no more.
--
-- Billing owns the licence; these two columns are a copy of the one fact the vendor
-- module needs on every storefront read: may this store trade today? Keeping it here
-- means the shop window does not query the billing tables, and that the dependency
-- runs one way only - billing tells vendor, vendor never asks billing
-- (docs/architecture/adr/0015-vendor-subscription-licensing.md).
--
-- Both columns are written only through VendorPlanState, never by a vendor's own
-- request: a store cannot extend its own licence by editing its profile.
ALTER TABLE vendors
    -- FALSE until a plan is paid for or a trial starts. Existing rows are stores that
    -- were approved before licensing existed; they must buy a plan like everybody else.
    ADD COLUMN subscription_active BOOLEAN NOT NULL DEFAULT FALSE,
    -- When the current licence period (including its grace days) runs out. NULL until
    -- the store has ever had one; shown to the vendor as "your plan expires on ...".
    ADD COLUMN plan_expires_at TIMESTAMPTZ;

-- The storefront asks for approved, licensed stores; the admin queue asks for the rest
CREATE INDEX idx_vendors_sellable ON vendors (status, subscription_active);
