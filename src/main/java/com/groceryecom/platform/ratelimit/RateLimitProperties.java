package com.groceryecom.platform.ratelimit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Limits for the endpoints an attacker hits first, under {@code app.security.rate-limit}.
 *
 * <p>Login and registration are public and cheap to call, which makes them the targets for
 * password guessing, credential stuffing and account-enumeration scripts. Limits are per
 * client address, plus a second, tighter limit per account so one account cannot be ground
 * down from many addresses.
 *
 * <p>Numbers are chosen to be invisible to a real person (nobody types their password
 * twenty times a minute) and painful for a script.
 *
 * @param enabled            turn off only for a load test, never in a shared environment
 * @param login              per client address
 * @param loginPerAccount    per username or email being tried, across all addresses
 * @param register           per client address
 * @param refreshToken       per client address; a normal client refreshes a few times an hour
 */
@Validated
@ConfigurationProperties("app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull Policy login,
        @NotNull Policy loginPerAccount,
        @NotNull Policy register,
        @NotNull Policy refreshToken) {

    public RateLimitProperties {
        login = login != null ? login : new Policy(20, Duration.ofMinutes(1));
        loginPerAccount = loginPerAccount != null ? loginPerAccount : new Policy(10, Duration.ofMinutes(5));
        register = register != null ? register : new Policy(5, Duration.ofMinutes(10));
        refreshToken = refreshToken != null ? refreshToken : new Policy(60, Duration.ofMinutes(1));
    }

    /**
     * @param limit  requests allowed per window
     * @param window length of the window
     */
    public record Policy(@Positive int limit, @NotNull Duration window) {
    }
}
