package com.cinemahub.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A bundled food pack, e.g. "Movie Night Combo" = 1 Large Popcorn + 2 Soft Drinks for a special
 * bundle price that is cheaper than buying the items separately. Built from existing FoodItem rows
 * (see {@link FoodComboItem}, table food_combo_items). Managed at /manager/food-combos; a Manager's
 * new or edited combo is PENDING until the System Admin approves it in the Approval Queue, the same
 * workflow as FoodItem / ParkingOption / Promotion. Customers pick combos at checkout, and approved
 * ones are also shown on the homepage "Promotions" tab.
 */
@Entity
@Table(name = "food_combos")
public class FoodCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional marketing text, e.g. "Perfect for two". What's included is always listed from the items. */
    @Size(max = 255)
    @Column(length = 255)
    private String description;

    @NotNull(message = "Combo price is required")
    @DecimalMin(value = "0.01", message = "Combo price must be greater than zero")
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal bundlePrice;

    @OneToMany(mappedBy = "foodCombo", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<FoodComboItem> items = new ArrayList<>();

    /** Soft delete: false hides the combo from customers without removing it. */
    @Column(nullable = false)
    private boolean available = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(length = 500)
    private String rejectionReason;

    /** Colour of this combo's card on the homepage. Null = Auto (orange, the original combo colour). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CardTheme cardTheme;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** What the included items would cost bought one by one, at today's menu prices. */
    public BigDecimal getIndividualTotal() {
        return items.stream().map(FoodComboItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** How much the customer saves with the combo (never negative). */
    public BigDecimal getSavings() {
        if (bundlePrice == null) {
            return BigDecimal.ZERO;
        }
        return getIndividualTotal().subtract(bundlePrice).max(BigDecimal.ZERO);
    }

    /** e.g. "1 x Large Popcorn + 2 x Soft Drink (500ml)". */
    public String getIncludedSummary() {
        return items.stream()
                .map(item -> item.getQuantity() + " x " + item.getFoodItem().getName())
                .collect(Collectors.joining(" + "));
    }

    /**
     * Customers can pick it only when it is available, approved, has items, and every included
     * item is still on the menu (a combo can't be served if one of its items is unavailable).
     */
    public boolean isOrderable() {
        return available && approvalStatus == ApprovalStatus.APPROVED && !items.isEmpty()
                && items.stream().allMatch(item -> item.getFoodItem().isOrderable());
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getBundlePrice() { return bundlePrice; }
    public void setBundlePrice(BigDecimal bundlePrice) { this.bundlePrice = bundlePrice; }
    public List<FoodComboItem> getItems() { return items; }
    public void setItems(List<FoodComboItem> items) { this.items = items; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public ApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(ApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public CardTheme getCardTheme() { return cardTheme; }
    public void setCardTheme(CardTheme cardTheme) { this.cardTheme = cardTheme; }

    /** CSS class for the homepage card: the chosen colour, or orange when left on Auto. */
    public String themeClass() {
        return (cardTheme != null ? cardTheme : CardTheme.ORANGE).getCssClass();
    }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
