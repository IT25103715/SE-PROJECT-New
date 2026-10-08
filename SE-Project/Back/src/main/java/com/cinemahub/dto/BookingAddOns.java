package com.cinemahub.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The optional extras a customer picked at checkout: food item quantities (by FoodItem id), food
 * combo pack quantities (by FoodCombo id) and whether car parking was added. Sent by the booking
 * forms as repeated "food" parameters in the form "foodItemId:quantity" (e.g. food=3:2), repeated
 * "foodCombo" parameters "foodComboId:quantity" (e.g. foodCombo=1:1), plus parking=true.
 */
public record BookingAddOns(Map<Long, Integer> foodQuantities, boolean parking, Map<Long, Integer> comboQuantities)
        implements Serializable {

    /** Max of any one food item (or combo) per booking - stops typos like 500 popcorns. */
    public static final int MAX_QUANTITY_PER_ITEM = 20;

    public BookingAddOns {
        foodQuantities = foodQuantities == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(foodQuantities));
        comboQuantities = comboQuantities == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(comboQuantities));
    }

    /** Food items and parking only (no combos). */
    public BookingAddOns(Map<Long, Integer> foodQuantities, boolean parking) {
        this(foodQuantities, parking, Map.of());
    }

    public static BookingAddOns none() {
        return new BookingAddOns(Map.of(), false, Map.of());
    }

    /** Parses the form values (food items + parking, no combos). */
    public static BookingAddOns fromForm(List<String> food, Boolean parking) {
        return fromForm(food, null, parking);
    }

    /** Parses the form values; malformed or zero entries are ignored, repeated ids are added up. */
    public static BookingAddOns fromForm(List<String> food, List<String> foodCombos, Boolean parking) {
        return new BookingAddOns(parseQuantities(food), Boolean.TRUE.equals(parking), parseQuantities(foodCombos));
    }

    private static Map<Long, Integer> parseQuantities(List<String> values) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (values != null) {
            for (String entry : values) {
                if (entry == null || !entry.contains(":")) {
                    continue;
                }
                String[] parts = entry.split(":", 2);
                try {
                    long id = Long.parseLong(parts[0].trim());
                    int quantity = Integer.parseInt(parts[1].trim());
                    if (quantity > 0) {
                        quantities.merge(id, quantity, Integer::sum);
                    }
                } catch (NumberFormatException ignored) {
                    // not a "id:quantity" pair - skip it
                }
            }
        }
        quantities.replaceAll((id, quantity) -> Math.min(quantity, MAX_QUANTITY_PER_ITEM));
        return quantities;
    }

    /** The food items back in "id:quantity" form, for re-sending as hidden "food" fields. */
    public List<String> toFormValues() {
        return toValues(foodQuantities);
    }

    /** The food combos back in "id:quantity" form, for re-sending as hidden "foodCombo" fields. */
    public List<String> toComboFormValues() {
        return toValues(comboQuantities);
    }

    private static List<String> toValues(Map<Long, Integer> quantities) {
        return quantities.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).toList();
    }

    public boolean isEmpty() {
        return foodQuantities.isEmpty() && comboQuantities.isEmpty() && !parking;
    }
}
