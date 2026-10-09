package com.cinemahub.model;

/** Kind of item on the cinema's food menu (see FoodItem). */
public enum FoodCategory {
    SNACK("Snack"),
    DRINK("Drink"),
    COMBO_SNACK("Combo snack");

    private final String displayLabel;

    FoodCategory(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }
}
