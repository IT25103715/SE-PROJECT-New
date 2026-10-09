package com.cinemahub.service.notification;

/**
 * Published by MovieService when a movie's approvalStatus changes to APPROVED (it went live) or to
 * PENDING (a Manager submitted it for approval). Carries plain values only, because
 * {@link MovieEmailNotifier} handles it on another thread after the transaction has closed.
 */
public record MovieApprovalEvent(Kind kind, Long movieId, String movieTitle, String genre, String submittedBy) {

    public enum Kind {
        APPROVED,
        SUBMITTED_FOR_APPROVAL
    }
}
