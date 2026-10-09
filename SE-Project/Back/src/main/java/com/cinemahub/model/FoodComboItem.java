package com.cinemahub.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** One line of a {@link FoodCombo}: an existing FoodItem and how many of it the pack includes. */
@Entity
@Table(name = "food_combo_items")
public class FoodComboItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_combo_id", nullable = false)
    private FoodCombo foodCombo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "food_item_id", nullable = false)
    private FoodItem foodItem;

    @Column(nullable = false)
    private int quantity;

    /** Today's menu price of this line, e.g. 2 x Soft Drink at Rs. 300 = Rs. 600. */
    public BigDecimal getLineTotal() {
        return foodItem.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public FoodCombo getFoodCombo() { return foodCombo; }
    public void setFoodCombo(FoodCombo foodCombo) { this.foodCombo = foodCombo; }
    public FoodItem getFoodItem() { return foodItem; }
    public void setFoodItem(FoodItem foodItem) { this.foodItem = foodItem; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
