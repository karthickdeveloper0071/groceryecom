package com.groceryecom.platform.audit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.groceryecom.shared.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        auditLog.loginSucceeded(USER_ID);

        assertThat(message()).contains(
                "action=LOGIN_SUCCEEDED",
                "actor=" + USER_ID,
                "outcome=SUCCESS",
                "traceId=trace-123");
    }

    @Test
    void recordsAFailedLoginWithoutAnAccountAsAnonymous() {
        auditLog.loginFailed(null, "no such account");

        assertThat(message()).contains("action=LOGIN_FAILED", "actor=anonymous", "outcome=FAILURE",
                "reason=no such account");
    }

    @Test
    void goesToItsOwnLoggerSoItCanBeRoutedSeparately() {
        auditLog.passwordChanged(USER_ID);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.getFirst().getLoggerName()).isEqualTo("audit");
    }

    @Test
    void recordsRegistrationWithTheRole() {
        auditLog.registered(USER_ID, "CUSTOMER");

        assertThat(message()).contains("action=REGISTERED", "role=CUSTOMER");
    }

    private String message() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getFormattedMessage();
    }
}
