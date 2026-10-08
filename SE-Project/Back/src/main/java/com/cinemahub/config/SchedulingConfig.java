package com.cinemahub.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's @Scheduled support - used by
 * {@link com.cinemahub.service.BookingExpiryScheduler} to release unpaid bookings - and @Async,
 * used by {@link com.cinemahub.service.notification.MovieEmailNotifier} to send emails off the request thread.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
