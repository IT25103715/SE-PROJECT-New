package com.cinemahub.dto;

import java.time.LocalDateTime;

/**
 * One row of the Admin Approval Queue ({@code /admin/approvals}) - a
 * Promotion, Movie, or Showtime still awaiting SYSTEM_ADMIN review. Combines
 * the three otherwise-unrelated entities into a single display shape so the
 * queue can be rendered as one table.
 */
public class PendingApprovalItem {

    private final String typeLabel;
    private final String typeSlug;
    private final Long id;
    private final String name;
    private final String createdByName;
    private final LocalDateTime submittedAt;

    public PendingApprovalItem(String typeLabel, String typeSlug, Long id, String name,
                                String createdByName, LocalDateTime submittedAt) {
        this.typeLabel = typeLabel;
        this.typeSlug = typeSlug;
        this.id = id;
        this.name = name;
        this.createdByName = createdByName;
        this.submittedAt = submittedAt;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    /** Lowercase route segment used in the Approve/Reject form actions, e.g. "promotion". */
    public String getTypeSlug() {
        return typeSlug;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
