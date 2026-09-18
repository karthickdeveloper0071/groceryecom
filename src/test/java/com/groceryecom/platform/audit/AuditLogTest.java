package com.groceryecom.platform.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.groceryecom.platform.audit.AuditLog.Outcome;
import com.groceryecom.shared.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What an audit line has to contain to be worth keeping.
 *
 * <p>The tests read the line the logger actually produced, rather than trusting the
 * method signature: an audit trail that silently drops the actor or the trace id is worse
 * than none, because people rely on it during an incident.
 */
class AuditLogTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private final AuditLog auditLog = new AuditLog();

    private ListAppender<ILoggingEvent> appender;
    private Logger auditLogger;

    @BeforeEach
    void setUp() {
        auditLogger = (Logger) LoggerFactory.getLogger(AuditLog.LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        auditLogger.addAppender(appender);
        auditLogger.setLevel(Level.INFO);
        MDC.put(ApiResponse.TRACE_ID_KEY, "trace-123");
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    void recordsWhoDidWhatWithTheTraceId() {
        auditLog.succeeded("LOGIN_SUCCEEDED", USER_ID);

        assertThat(loggedLine())
                .contains("action=LOGIN_SUCCEEDED")
                .contains("actor=" + USER_ID)
                .contains("outcome=SUCCESS")
                // The trace id is what ties this line to the request that caused it
                .contains("traceId=trace-123");
    }

    @Test
    void keepsTheDetailItIsGiven() {
        auditLog.succeeded("VENDOR_APPROVED", USER_ID, "vendor=abc-123");

        assertThat(loggedLine()).contains("vendor=abc-123");
    }

    /** A run of failures is what an attack looks like, so the outcome has to be searchable. */
    @Test
    void marksAFailureAsOneAndSaysWhy() {
        auditLog.failed("LOGIN_FAILED", null, "bad credentials");

        assertThat(loggedLine())
                .contains("outcome=FAILURE")
                .contains("reason=bad credentials");
    }

    /**
     * Some events have no signed-in user: a failed login before the account is known, or
     * the platform acting on its own. The line still has to be recorded, with the actor
     * named as anonymous rather than left blank or dropped.
     */
    @Test
    void recordsAnEventWithNoSignedInUser() {
        auditLog.record("PAYMENT_SETTLED", null, Outcome.SUCCESS, "reference=order_1");

        assertThat(loggedLine())
                .contains("actor=anonymous")
                .contains("reference=order_1");
    }

    /** It goes to the dedicated audit logger, which is what makes separate retention possible. */
    @Test
    void writesToTheAuditLoggerAndNotTheApplicationLog() {
        auditLog.succeeded("LOGGED_OUT", USER_ID);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLoggerName()).isEqualTo(AuditLog.LOGGER_NAME);
    }

    private String loggedLine() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getFormattedMessage();
    }
}
