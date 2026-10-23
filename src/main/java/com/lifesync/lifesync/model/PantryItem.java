package com.lifesync.lifesync.model;

import java.util.UUID;

/**
 * Represents an ingredient stored in the user's pantry.
 *
 * Tracks what the user has available at home, including the quantity,
 * unit of measurement, and an optional low-stock threshold that triggers
 * warnings on the dashboard.  Pantry items can be specific (e.g.,
 * "chicken breast", "pork belly") to support accurate recipe matching.
 *
 * Full pantry management UI is planned for Phase 2; this model is
 * created now so the data layer is ready.
 */
public class PantryItem {

    // ── Fields ─────────────────────────────────────────────────────────

    private String id;            // Unique identifier (UUID)
    private String userId;        // Owner of this item (references User.id)
    private String name;          // Ingredient name, e.g. "Chicken Breast"
    private String category;      // Grouping, e.g. "Meat", "Vegetables", "Spices"
    private String unit;          // Unit of measure, e.g. "g", "kg", "pieces", "ml"
    private double quantity;      // Current amount on hand
    private double lowThreshold;  // Below this quantity, item is flagged as "low"
    private String expiryDate;    // Optional expiry date (yyyy-MM-dd), may be null

    // ── Constructors ───────────────────────────────────────────────────

    /** No-arg constructor required by Gson deserialization. */
    public PantryItem() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Creates a pantry item with the essential fields.
     *
     * @param userId   owner's user ID
     * @param name     ingredient name
     * @param category grouping category
     * @param unit     unit of measurement
     * @param quantity current amount
     */
    public PantryItem(String userId, String name, String category,
                      String unit, double quantity) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.quantity = quantity;
        this.lowThreshold = 1.0; // sensible default
    }

    // ── Getters & Setters ──────────────────────────────────────────────

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public double getLowThreshold() { return lowThreshold; }
    public void setLowThreshold(double lowThreshold) { this.lowThreshold = lowThreshold; }

    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

    /** Returns true if current quantity is at or below the low threshold. */
    public boolean isLow() {
        return quantity <= lowThreshold;
    }

    @Override
    public String toString() {
        return "PantryItem{" + name + " " + quantity + " " + unit + "}";
    }
}
