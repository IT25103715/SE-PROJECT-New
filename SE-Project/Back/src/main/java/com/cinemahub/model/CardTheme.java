package com.cinemahub.model;

/**
 * Colour of an offer card in the homepage "Offers &amp; Promotions" section. Chosen by the
 * Promotion Manager (promotions) or Food Manager (food combos) on the create/edit form.
 * Stored nullable: null = AUTO, i.e. the original behaviour (promotions rotate red / blue /
 * navy-gold by position, food combos are orange). Each value maps to the CSS class
 * {@code ch-offer-theme-<key>} in static/css/offers.css.
 */
public enum CardTheme {
    RED("red", "Cinema Red"),
    BLUE("blue", "Royal Blue"),
    GOLD("gold", "Navy & Gold"),
    ORANGE("orange", "Sunset Orange"),
    GREEN("green", "Emerald Green"),
    PURPLE("purple", "Royal Purple");

    /** Colours used, in order, when a promotion is left on Auto. */
    private static final CardTheme[] AUTO_ROTATION = {RED, BLUE, GOLD};

    private final String key;
    private final String displayLabel;

    CardTheme(String key, String displayLabel) {
        this.key = key;
        this.displayLabel = displayLabel;
    }

    public String getKey() {
        return key;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    /** CSS class for this colour, e.g. "ch-offer-theme-red". */
    public String getCssClass() {
        return "ch-offer-theme-" + key;
    }

    /** The automatic colour for the card at this position (0-based) in the promotions row. */
    public static CardTheme autoFor(int position) {
        return AUTO_ROTATION[Math.floorMod(position, AUTO_ROTATION.length)];
    }
}
