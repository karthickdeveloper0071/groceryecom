package com.groceryecom.platform.audit;

import com.groceryecom.shared.web.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Records security-relevant events: who did what, and whether it worked.
 *
 * <p>Written to a dedicated logger named {@code audit}, so these lines can be routed to
 * their own file, index or retention rule without being mixed into application logging.
 * In staging and production they are JSON, one object per line.
 *
 * <p>An audit line answers a question someone will ask later: "was this account taken
 * over?", "who changed this password?", "when did these failed logins start?". It carries
 * the actor, the outcome and the trace id, never a password, token or full card number.
 *
 * <p>Today this is log-based. A database-backed trail with retention and an admin UI is a
 * separate decision, needed before the platform handles other people's money.
 */
@Component
public class AuditLog {

    public static final String LOGGER_NAME = "audit";
    private static final String ANONYMOUS = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /** An account was created. */
    public void registered(UUID userId, String role) {
        record(AuditAction.REGISTERED, userId, Outcome.SUCCESS, "role=" + role);
    }

    public void loginSucceeded(UUID userId) {
        record(AuditAction.LOGIN_SUCCEEDED, userId, Outcome.SUCCESS, "");
    }

    /**
     * @param userId the matched account, or null when no account matched. The attempted
     *               identifier is deliberately not recorded: it is often a mistyped
     *               password in the username field.
     */
    public void loginFailed(UUID userId, String reason) {
        record(AuditAction.LOGIN_FAILED, userId, Outcome.FAILURE, "reason=" + reason);
    }

    public void tokenRefreshed(UUID userId) {
        record(AuditAction.TOKEN_REFRESHED, userId, Outcome.SUCCESS, "");
    }

    public void passwordChanged(UUID userId) {
        record(AuditAction.PASSWORD_CHANGED, userId, Outcome.SUCCESS, "");
    }

    public void loggedOut(UUID userId) {
        record(AuditAction.LOGGED_OUT, userId, Outcome.SUCCESS, "");
    }

    /** One device was signed out from the session list. */
    public void sessionRevoked(UUID userId, UUID sessionId) {
        record(AuditAction.SESSION_REVOKED, userId, Outcome.SUCCESS, "session=" + sessionId);
    }

    /** Every session of the user was ended: "sign out everywhere", or a password change. */
    public void allSessionsRevoked(UUID userId) {
        record(AuditAction.ALL_SESSIONS_REVOKED, userId, Outcome.SUCCESS, "");
    }

    /** An account applied to open a store. The store cannot sell until an admin approves it. */
    public void vendorRegistered(UUID userId, UUID vendorId) {
        record(AuditAction.VENDOR_REGISTERED, userId, Outcome.SUCCESS, "vendor=" + vendorId);
    }

    /**
     * An admin approved, rejected or suspended a store. Who decided, and why, is asked
     * long afterwards by somebody who was not there.
     *
     * @param reason the admin's words for a rejection or a suspension; null for an approval
     */
    public void vendorStatusChanged(UUID adminId, UUID vendorId, String status, String reason) {
        record(AuditAction.VENDOR_STATUS_CHANGED, adminId, Outcome.SUCCESS,
                "vendor=%s status=%s%s".formatted(vendorId, status, reason == null ? "" : " reason=" + reason));
    }

    /** A store chose, changed or cancelled its plan. Money follows from this. */
    public void subscriptionChanged(UUID actorId, UUID vendorId, String planCode, String status) {
        record(AuditAction.SUBSCRIPTION_CHANGED, actorId, Outcome.SUCCESS,
                "vendor=%s plan=%s status=%s".formatted(vendorId, planCode, status));
    }

    /**
     * A subscription payment was settled, which is what lets a store trade.
     *
     * @param actorId the admin who confirmed a transfer, or null when a gateway did
     * @param amount  the amount and currency, never any payment credential
     */
    public void subscriptionPaymentConfirmed(UUID actorId, UUID vendorId, String amount, String reference) {
        record(AuditAction.SUBSCRIPTION_PAYMENT_CONFIRMED, actorId, Outcome.SUCCESS,
                "vendor=%s amount=%s reference=%s".formatted(vendorId, amount, reference));
    }

    /**
     * A store changed where its earnings are paid.
     *
     * <p>Among the most sensitive events on the platform: redirecting a vendor's income
     * is exactly what an account takeover is for. The last four digits are recorded so a
     * change is recognisable afterwards; the account number is not stored anywhere.
     */
    public void payoutAccountChanged(UUID actorId, UUID vendorId, String accountLast4, String status) {
        record(AuditAction.PAYOUT_ACCOUNT_CHANGED, actorId, Outcome.SUCCESS,
                "vendor=%s account=****%s status=%s".formatted(vendorId, accountLast4, status));
    }

    /**
     * An admin installed, replaced or switched a payment gateway's keys.
     *
     * <p>"Who changed the payment configuration, and when?" is the first question after
     * money stops arriving. The key <b>hint</b> is recorded - the last four characters of
     * the public key id - and never the secret.
     */
    public void paymentGatewayConfigured(UUID adminId, String provider, String mode,
                                         String keyIdHint, boolean enabled) {
        record(AuditAction.PAYMENT_GATEWAY_CONFIGURED, adminId, Outcome.SUCCESS,
                "provider=%s mode=%s key=%s enabled=%s".formatted(provider, mode, keyIdHint, enabled));
    }

    /**
     * A refresh token was presented twice. Either it leaked or a client is buggy; both are
     * worth investigating, so this is recorded as a failure.
     */
    public void refreshTokenReused(UUID userId) {
        record(AuditAction.REFRESH_TOKEN_REUSED, userId, Outcome.FAILURE, "action=all sessions revoked");
    }

    private void record(AuditAction action, UUID actor, Outcome outcome, String detail) {
        log.info("audit action={} actor={} outcome={} traceId={}{}",
                action,
                actor != null ? actor : ANONYMOUS,
                outcome,
                MDC.get(ApiResponse.TRACE_ID_KEY),
                detail.isEmpty() ? "" : " " + detail);
    }

    /** Actions worth keeping a permanent record of. Add one per security-relevant change. */
    public enum AuditAction {
        REGISTERED,
        LOGIN_SUCCEEDED,
        LOGIN_FAILED,
        TOKEN_REFRESHED,
        REFRESH_TOKEN_REUSED,
        PASSWORD_CHANGED,
        LOGGED_OUT,
        SESSION_REVOKED,
        ALL_SESSIONS_REVOKED,
        VENDOR_REGISTERED,
        VENDOR_STATUS_CHANGED,
        SUBSCRIPTION_CHANGED,
        SUBSCRIPTION_PAYMENT_CONFIRMED,
        PAYMENT_GATEWAY_CONFIGURED,
        PAYOUT_ACCOUNT_CHANGED
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
