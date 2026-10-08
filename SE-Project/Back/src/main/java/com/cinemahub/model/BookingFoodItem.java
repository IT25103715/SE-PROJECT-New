package com.cinemahub.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One food line on a booking (e.g. 2 x Large Popcorn). The name and unit price are copied at
 * booking time, so the receipt stays correct even if the menu item is later renamed or repriced.
 *
 * Deliberately not mapped as a collection on Booking (Booking already eagerly loads its seats as
 * a List, and Hibernate can't eagerly fetch two Lists at once) - read with
 * BookingFoodItemRepository#findByBooking_IdOrderByIdAsc instead.
 */
@Entity
@Table(name = "booking_food_items")
public class BookingFoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    /** The menu item - null when this line is a food combo pack (see foodCombo). */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "food_item_id")
    private FoodItem foodItem;

    /** The food combo pack bought - null for a plain food item line. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "food_combo_id")
    private FoodCombo foodCombo;

    @Column(nullable = false, length = 100)
    private String itemName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public FoodItem getFoodItem() { return foodItem; }
    public FoodCombo getFoodCombo() { return foodCombo; }
    public void setFoodCombo(FoodCombo foodCombo) { this.foodCombo = foodCombo; }

    /** True when this line is a food combo pack (shown with a "Combo" badge). */
    public boolean isCombo() { return foodCombo != null; }
    public void setFoodItem(FoodItem foodItem) { this.foodItem = foodItem; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
