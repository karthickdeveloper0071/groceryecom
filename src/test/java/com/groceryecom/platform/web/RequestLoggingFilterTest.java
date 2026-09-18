package com.groceryecom.platform.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * The request log is the first thing anyone reads when a client reports a problem, so
 * what it contains is worth testing: the endpoint, the status, the timing, and for a
 * failed request the data that was sent, with secrets removed.
 */
class RequestLoggingFilterTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
        filterLogger.setLevel(Level.INFO);

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                // The same advice the application uses, so a thrown exception becomes a
                // 500 response here too and the filter sees the status a client would get
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestLoggingFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void logsOneInfoLineWithEndpointStatusAndDurationForASuccess() throws Exception {
        mockMvc.perform(get("/things"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("GET", "/things", "-> 200", "ms", "user=anonymous");
    }

    @Test
    void logsAWarningWithTheMaskedBodyWhenTheRequestFails() throws Exception {
        mockMvc.perform(post("/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"hunter2\"}"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage()).contains("POST", "/things", "-> 400");
        // The useful part: what the client sent, without the secret
        assertThat(event.getFormattedMessage()).contains("\"username\":\"alice\"", "\"password\":\"***\"");
        assertThat(event.getFormattedMessage()).doesNotContain("hunter2");
    }

    @Test
    void logsAnErrorWhenTheServerFails() throws Exception {
        mockMvc.perform(get("/boom"));

        ILoggingEvent event = onlyEvent();
        assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        assertThat(event.getFormattedMessage()).contains("GET", "/boom", "-> 500");
    }

    @Test
    void doesNotLogTheBodyOfASuccessfulRequest() throws Exception {
        mockMvc.perform(post("/things")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ok\",\"password\":\"hunter2\"}")
                .param("valid", "true"));

        assertThat(onlyEvent().getFormattedMessage()).doesNotContain("body=", "hunter2");
    }

    @Test
    void doesNotLogActuatorTraffic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"));

        assertThat(appender.list).isEmpty();
    }

    private ILoggingEvent onlyEvent() {
        List<ILoggingEvent> events = appender.list;
        assertThat(events).hasSize(1);
        return events.getFirst();
    }

    @RestController
    static class TestController {

        @GetMapping("/things")
        String list() {
            return "ok";
        }

        @GetMapping("/actuator/health/liveness")
        String liveness() {
            return "UP";
        }

        @PostMapping("/things")
        org.springframework.http.ResponseEntity<String> create(@RequestBody String body,
                                                               @org.springframework.web.bind.annotation.RequestParam(
                                                                       defaultValue = "false") boolean valid) {
            return valid
                    ? org.springframework.http.ResponseEntity.ok("created")
                    : org.springframework.http.ResponseEntity.badRequest().body("invalid");
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("something broke");
        }
    }
}
