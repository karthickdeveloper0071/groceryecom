package com.groceryecom.platform.scheduling;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} for the whole application.
 *
 * <p>Java 21 virtual threads are enabled, so Spring Boot backs the scheduler with them:
 * a job that waits on the database does not hold a platform thread.
 *
 * <p>Every instance runs every scheduled job - there is no leader election here. Jobs
 * must therefore be written so that running them twice at the same moment changes
 * nothing the second time, as {@code SubscriptionExpiryService} is. A job that sends
 * email or moves money cannot rely on that, and needs a lock (ShedLock) before it is
 * written, not after the first duplicate invoice.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
