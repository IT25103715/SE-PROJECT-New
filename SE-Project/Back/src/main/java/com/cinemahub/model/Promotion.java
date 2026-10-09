package com.cinemahub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A discount coupon / promotional campaign (Function 6).
 *
 * Kept standalone on purpose - no foreign key to Booking/Payment/User, since
 * the shared database schema is still being finalized. {@link com.cinemahub.service.BookingService#createBooking}
 * already applies a code at checkout via two plain
 * {@code PromotionService} calls ({@code previewDiscount} then
 * {@code recordUsage}) rather than a database join.
 */
@Entity
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Coupon code is required")
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @NotBlank(message = "Description is required")
    @Column(nullable = false, length = 500)
    private String description;

    @NotNull(message = "Please choose a discount type")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be greater than zero")
    @Column(nullable = false)
    private BigDecimal discountValue;

    @NotNull(message = "Start date is required")
    @Column(nullable = false)
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    @Column(nullable = false)
    private LocalDate endDate;

    // Null means unlimited uses.
    @PositiveOrZero(message = "Usage limit cannot be negative")
    private Integer usageLimit;

    @Column(nullable = false)
    private Integer usageCount = 0;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PromotionStatus status = PromotionStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Admin approval workflow (see AdminApprovalController): a PROMOTION_MANAGER's
    // new/edited promotion starts PENDING and can't be redeemed by customers until
    // SYSTEM_ADMIN approves it; SYSTEM_ADMIN's own submissions are saved as APPROVED directly.
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(length = 500)
    private String rejectionReason;

    /**
     * What a Percentage / Fixed Amount promotion discounts: the whole booking (default) or only
     * the food subtotal. Nullable so promotions created before this existed load as
     * BOOKING_TOTAL - always read it through {@link #scope()}. Not used by COMBO promotions.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DiscountScope discountScope = DiscountScope.BOOKING_TOTAL;

    /**
     * Minimum number of seats (tickets) the booking must have, e.g. 2. Null = no seat condition.
     * Works for every discount type: a Percentage / Fixed promotion with minSeats only applies
     * from that many seats up, and it is one of the conditions of a COMBO promotion.
     */
    @jakarta.validation.constraints.Min(value = 1, message = "Minimum seats must be at least 1")
    private Integer minSeats;

    // ---------- COMBO promotions only (discountType = COMBO); null/false for the other types ----------

    /** A food item that must be in the order. Null = no food condition. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "required_food_item_id")
    private FoodItem requiredFoodItem;

    /** True when the booking must include car parking. Nullable so older rows load as "false". */
    private Boolean requiresParking;

    /**
     * Colour of this promotion's card on the homepage. Null = Auto (rotates red / blue /
     * navy-gold by position), so promotions created before this field existed look the same.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CardTheme cardTheme;

    /**
     * Membership program: only customers who are members (User#isMember) can use this code; for
     * anyone else it doesn't apply (PromotionService#validAndUsable). Still shown on the homepage
     * to everyone, with a "Members Only" badge. The column default keeps existing codes open to all.
     */
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean membersOnly = false;

    public boolean isMembersOnly() {
        return membersOnly;
    }

    public void setMembersOnly(boolean membersOnly) {
        this.membersOnly = membersOnly;
    }

    public CardTheme getCardTheme() {
        return cardTheme;
    }

    public void setCardTheme(CardTheme cardTheme) {
        this.cardTheme = cardTheme;
    }

    /** CSS class for the homepage card: the chosen colour, or the automatic one for this position. */
    public String themeClass(int position) {
        return (cardTheme != null ? cardTheme : CardTheme.autoFor(position)).getCssClass();
    }

    /** Short summary for lists, e.g. "Combo: 2+ seats + Large Popcorn + Parking -> Rs. 500 off". */
    public String getComboSummary() {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (minSeats != null && minSeats > 0) {
            parts.add(minSeats + "+ seats");
        }
        if (requiredFoodItem != null) {
            parts.add(requiredFoodItem.getName());
        }
        if (parkingRequired()) {
            parts.add("Parking");
        }
        String conditions = parts.isEmpty() ? "no conditions" : String.join(" + ", parts);
        return "Combo: " + conditions + " -> Rs. " + (discountValue != null ? discountValue.toPlainString() : "?") + " off";
    }

    public DiscountScope getDiscountScope() {
        return discountScope;
    }

    public void setDiscountScope(DiscountScope discountScope) {
        this.discountScope = discountScope;
    }

    /** Null-safe scope used by the pricing code (older rows have no value = whole booking). */
    public DiscountScope scope() {
        return discountScope == null ? DiscountScope.BOOKING_TOTAL : discountScope;
    }

    /** True for a Percentage / Fixed promotion that only discounts food. */
    public boolean isFoodOnly() {
        return discountType != DiscountType.COMBO && scope() == DiscountScope.FOOD_ONLY;
    }

    /**
     * Extra conditions of a Percentage / Fixed promotion for lists and offer cards, e.g.
     * "on food only, 2+ seats". Empty when it is a plain whole-booking discount.
     */
    public String getConditionsSummary() {
        if (discountType == DiscountType.COMBO) {
            return "";
        }
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (isFoodOnly()) {
            parts.add("on food only");
        }
        if (minSeats != null && minSeats > 0) {
            parts.add("when you book " + minSeats + "+ seats");
        }
        return String.join(", ", parts);
    }

    public Integer getMinSeats() {
        return minSeats;
    }

    public void setMinSeats(Integer minSeats) {
        this.minSeats = minSeats;
    }

    public FoodItem getRequiredFoodItem() {
        return requiredFoodItem;
    }

    public void setRequiredFoodItem(FoodItem requiredFoodItem) {
        this.requiredFoodItem = requiredFoodItem;
    }

    /** Bean property for the form checkbox (may be null on older rows). */
    public Boolean getRequiresParking() {
        return requiresParking;
    }

    /** Null-safe check used by the pricing code: true only when parking is a combo condition. */
    public boolean parkingRequired() {
        return Boolean.TRUE.equals(requiresParking);
    }

    public void setRequiresParking(Boolean requiresParking) {
        this.requiresParking = requiresParking;
    }

    /** True once today is past the end date, regardless of the stored status - used only to show a hint in the UI. */
    public boolean isPastEndDate() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public PromotionStatus getStatus() {
        return status;
    }

    public void setStatus(PromotionStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(User createdBy) {
        this.createdBy = createdBy;
    }
    
    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
