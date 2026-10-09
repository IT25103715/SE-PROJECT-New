package com.cinemahub.model;

import com.cinemahub.service.BookingConfirmedEvent;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener on {@link Booking}: notices the moment a booking's status changes to
 * CONFIRMED and publishes a {@link BookingConfirmedEvent}. Because it watches the booking row
 * itself, it covers every place that confirms a booking (BookingService's payment and
 * bank-transfer approval paths, and any added later) without duplicating that logic.
 * Spring Boot lets Hibernate create this listener as a Spring bean, so the publisher is injected.
 */
@Component
public class BookingStatusListener {

    private final ApplicationEventPublisher eventPublisher;

    public BookingStatusListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /** Remember the status a booking had when it was read from the database. */
    @PostLoad
    public void afterLoad(Booking booking) {
        booking.rememberStatus();
    }

    /** A new booking: if it was saved straight as CONFIRMED, that counts as a confirmation too. */
    @PostPersist
    public void afterInsert(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            publish(booking);
        }
        booking.rememberStatus();
    }

    /** An updated booking: publish only on the change INTO CONFIRMED, never on later edits. */
    @PostUpdate
    public void afterUpdate(Booking booking) {
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getStatusWhenLoaded() != BookingStatus.CONFIRMED) {
            publish(booking);
        }
        booking.rememberStatus();
    }

    private void publish(Booking booking) {
        if (booking.getUser() != null) {
            eventPublisher.publishEvent(new BookingConfirmedEvent(booking.getId(), booking.getUser().getId()));
        }
    }
}
