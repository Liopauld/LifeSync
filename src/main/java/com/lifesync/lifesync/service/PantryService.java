package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.PantryItem;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages the user's pantry — ingredients they have available at home.
 *
 * <h3>Key responsibilities</h3>
 * <ul>
 *   <li>CRUD operations for pantry items.</li>
 *   <li>Low-stock detection (items at or below their threshold).</li>
 *   <li>Category-based filtering for the pantry view.</li>
 *   <li>Quantity adjustments (use ingredient, restock).</li>
 * </ul>
 *
 * Data is persisted to {@code data/pantry.json} via {@link DataStore}.
 */
public class PantryService {

    /** JSON file for pantry persistence. */
    private static final String PANTRY_FILE = "pantry.json";

    /** Gson type token for List<PantryItem>. */
    private static final Type ITEM_LIST_TYPE =
            new TypeToken<List<PantryItem>>() {}.getType();

    /**
     * Returns all pantry items belonging to a user, sorted by category then name.
     *
     * @param userId the user's ID
     * @return sorted list of pantry items
     */
    public static List<PantryItem> getAllItems(String userId) {
        List<PantryItem> all = DataStore.loadAll(PANTRY_FILE, ITEM_LIST_TYPE);
        return all.stream()
                .filter(i -> i.getUserId().equals(userId))
                .sorted((a, b) -> {
                    int cat = safeCompare(a.getCategory(), b.getCategory());
                    return cat != 0 ? cat : safeCompare(a.getName(), b.getName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Alias for {@link #getAllItems(String)} providing an intuitive lookup.
     *
     * @param userId the user's ID
     * @return sorted list of all pantry items for the user
     */
    public static List<PantryItem> getPantryItems(String userId) {
        return getAllItems(userId);
    }

    /**
     * Returns pantry items filtered by category.
     *
     * @param userId   the user's ID
     * @param category the category to filter by (case-insensitive)
     * @return filtered list
     */
    public static List<PantryItem> getItemsByCategory(String userId, String category) {
        return getAllItems(userId).stream()
                .filter(i -> i.getCategory() != null
                        && i.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    /**
     * Returns all items that are at or below their low-stock threshold.
     *
     * @param userId the user's ID
     * @return list of low-stock items
     */
    public static List<PantryItem> getLowStockItems(String userId) {
        return getAllItems(userId).stream()
                .filter(PantryItem::isLow)
                .collect(Collectors.toList());
    }

    /**
     * Counts how many items are low in stock.
     *
     * @param userId the user's ID
     * @return number of low-stock items
     */
    public static long countLowStock(String userId) {
        return getLowStockItems(userId).size();
    }

    /**
     * Adds a new item to the pantry.
     *
     * @param item the pantry item to add
     */
    public static void addItem(PantryItem item) {
        List<PantryItem> all = DataStore.loadAll(PANTRY_FILE, ITEM_LIST_TYPE);
        all.add(item);
        DataStore.saveAll(PANTRY_FILE, all);
    }

    /**
     * Updates an existing pantry item (matched by ID).
     *
     * @param updated the item with modified fields
     */
    public static void updateItem(PantryItem updated) {
        List<PantryItem> all = DataStore.loadAll(PANTRY_FILE, ITEM_LIST_TYPE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getId().equals(updated.getId())) {
                all.set(i, updated);
                break;
            }
        }
        DataStore.saveAll(PANTRY_FILE, all);
    }

    /**
     * Removes a pantry item by ID.
     *
     * @param itemId the ID of the item to remove
     */
    public static void removeItem(String itemId) {
        List<PantryItem> all = DataStore.loadAll(PANTRY_FILE, ITEM_LIST_TYPE);
        all.removeIf(i -> i.getId().equals(itemId));
        DataStore.saveAll(PANTRY_FILE, all);
    }

    /**
     * Returns all distinct categories across the user's pantry.
     * Useful for building filter tabs.
     *
     * @param userId the user's ID
     * @return sorted list of unique category strings
     */
    public static List<String> getCategories(String userId) {
        return getAllItems(userId).stream()
                .map(PantryItem::getCategory)
                .filter(c -> c != null && !c.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Null-safe string comparison helper. */
    private static int safeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareToIgnoreCase(b);
    }
}
