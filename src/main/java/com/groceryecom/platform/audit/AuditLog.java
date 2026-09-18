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
 * <p>An audit line answers a question somebody asks later: "was this account taken over?",
 * "who changed this password?", "who let this vendor on to the platform?", "when did these
 * failed logins start?". It carries the actor, the outcome and the trace id, and never a
 * password, token, card number or bank account.
 *
 * <p><b>What to record.</b> Anything a person would want an answer about after the fact:
 * signing in and failing to, changing a password or an email, a permission or role change,
 * money moving, an admin acting on somebody else's data, and anything irreversible. Not
 * ordinary reads, and not every write - an audit log nobody can scan is one nobody uses.
 *
 * <p>Actions are plain strings in {@code SCREAMING_SNAKE_CASE}, named by the module that
 * records them ({@code LOGIN_SUCCEEDED}, {@code ORDER_REFUNDED}). Keep each module's names
 * together as constants in that module rather than listing every action here: this class
 * belongs to the platform and should not have to change every time a feature is added.
 *
 * <p>Today this is log-based. A database-backed trail with retention and an admin screen is
 * a separate decision, needed before the platform handles other people's money.
 */
@Component
public class AuditLog {

    public static final String LOGGER_NAME = "audit";
    private static final String ANONYMOUS = "anonymous";

    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    /**
     * Records that something happened, and who did it.
     *
     * @param action what happened, in SCREAMING_SNAKE_CASE
     * @param actor  the user responsible, or null when nobody was signed in - a failed
     *               login before the account is known, or the platform acting on its own
     * @param detail extra context as {@code key=value} pairs, for example
     *               {@code "vendor=" + vendorId}. Never a secret, and never a full card or
     *               bank number: four digits are enough to recognise something
     */
    public void record(String action, UUID actor, Outcome outcome, String detail) {
        log.info("audit action={} actor={} outcome={} traceId={}{}",
                action,
                actor != null ? actor : ANONYMOUS,
                outcome,
                MDC.get(ApiResponse.TRACE_ID_KEY),
                detail == null || detail.isEmpty() ? "" : " " + detail);
    }

    /** The common case: it worked, and there is nothing more to say about it. */
    public void succeeded(String action, UUID actor) {
        record(action, actor, Outcome.SUCCESS, "");
    }

    public void succeeded(String action, UUID actor, String detail) {
        record(action, actor, Outcome.SUCCESS, detail);
    }

    /**
     * Something was refused. Worth its own method because failures are what somebody
     * searches for first: a run of them is how an attack looks in a log.
     */
    public void failed(String action, UUID actor, String reason) {
        record(action, actor, Outcome.FAILURE, "reason=" + reason);
    }

    public enum Outcome {
        SUCCESS,
        FAILURE
    }
}
