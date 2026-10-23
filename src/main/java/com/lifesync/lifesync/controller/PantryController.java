package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.PantryService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Optional;

/**
 * Controller for the Pantry view (pantry-view.fxml).
 *
 * <h3>How it works</h3>
 * <ol>
 *   <li>Loads all pantry items for the logged-in user from PantryService.</li>
 *   <li>Displays items as rows with name, category, quantity, unit,
 *       and a low-stock warning indicator.</li>
 *   <li>Users can add, edit, or remove ingredients via dialogs.</li>
 *   <li>Category filter buttons let users view items by group.</li>
 *   <li>Summary cards show total items, low-stock count, and category count.</li>
 * </ol>
 */
public class PantryController {

    @FXML private Label totalItemsLabel;
    @FXML private Label lowStockLabel;
    @FXML private Label categoriesLabel;
    @FXML private HBox categoryFilters;
    @FXML private VBox pantryItemsList;

    /** The logged-in user. */
    private User currentUser;

    /** Currently active category filter (null = show all). */
    private String activeCategory = null;

    /**
     * Called after FXML injection. Loads pantry data and builds the UI.
     */
    @FXML
    public void initialize() {
        currentUser = AuthService.getCurrentUser();
        refreshAll();
    }

    /**
     * Refreshes summary cards, category filters, and the item list.
     */
    private void refreshAll() {
        if (currentUser == null) return;
        String userId = currentUser.getId();

        // Summary cards
        List<PantryItem> allItems = PantryService.getAllItems(userId);
        totalItemsLabel.setText(String.valueOf(allItems.size()));
        lowStockLabel.setText(String.valueOf(PantryService.countLowStock(userId)));
        List<String> categories = PantryService.getCategories(userId);
        categoriesLabel.setText(String.valueOf(categories.size()));

        // Build category filter buttons
        buildCategoryFilters(categories);

        // Load items (filtered or all)
        List<PantryItem> displayItems = activeCategory != null
                ? PantryService.getItemsByCategory(userId, activeCategory)
                : allItems;
        loadItemsList(displayItems);
    }

    /**
     * Builds category filter buttons (All + one per category).
     *
     * @param categories the distinct category names
     */
    private void buildCategoryFilters(List<String> categories) {
        // Keep the "Filter:" label, remove the rest
        while (categoryFilters.getChildren().size() > 1) {
            categoryFilters.getChildren().remove(1);
        }

        // "All" button
        Button allBtn = new Button("All");
        allBtn.getStyleClass().add("toggle-btn");
        if (activeCategory == null) allBtn.getStyleClass().add("sidebar-btn-active");
        allBtn.setOnAction(e -> { activeCategory = null; refreshAll(); });
        categoryFilters.getChildren().add(allBtn);

        // One button per category
        for (String cat : categories) {
            Button catBtn = new Button(cat);
            catBtn.getStyleClass().add("toggle-btn");
            if (cat.equals(activeCategory)) catBtn.getStyleClass().add("sidebar-btn-active");
            catBtn.setOnAction(e -> { activeCategory = cat; refreshAll(); });
            categoryFilters.getChildren().add(catBtn);
        }
    }

    /**
     * Renders pantry items as rows in the items list.
     * Each row: [Low-stock icon] [Name] [Category tag] [Quantity + Unit] [Edit] [Delete]
     *
     * @param items the items to display
     */
    private void loadItemsList(List<PantryItem> items) {
        pantryItemsList.getChildren().clear();

        if (items.isEmpty()) {
            Label empty = new Label("No ingredients in your pantry yet. Click '➕ Add Ingredient' to start!");
            empty.getStyleClass().add("muted-text");
            pantryItemsList.getChildren().add(empty);
            return;
        }

        for (PantryItem item : items) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("activity-row");
            row.setPadding(new Insets(8, 12, 8, 12));

            // Low-stock warning icon
            if (item.isLow()) {
                Label warn = new Label("⚠️");
                warn.setTooltip(new Tooltip("Low stock!"));
                row.getChildren().add(warn);
            }

            // Item name
            Label nameLabel = new Label(item.getName());
            nameLabel.getStyleClass().add("activity-title");
            nameLabel.setMinWidth(150);

            // Category tag
            Label catLabel = new Label(item.getCategory() != null ? item.getCategory() : "—");
            catLabel.getStyleClass().add("category-tag");
            catLabel.setMinWidth(80);

            // Quantity + unit
            Label qtyLabel = new Label(
                    String.format("%.1f %s", item.getQuantity(),
                            item.getUnit() != null ? item.getUnit() : ""));
            qtyLabel.setMinWidth(80);
            qtyLabel.getStyleClass().add("macro-value");
            if (item.isLow()) {
                qtyLabel.getStyleClass().add("pantry-qty-low");
            }

            // Spacer
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // Edit button
            Button editBtn = new Button("✏️");
            editBtn.getStyleClass().add("nav-arrow");
            editBtn.setTooltip(new Tooltip("Edit"));
            editBtn.setOnAction(e -> showEditItemDialog(item));

            // Delete button
            Button delBtn = new Button("🗑️");
            delBtn.getStyleClass().add("nav-arrow");
            delBtn.setTooltip(new Tooltip("Remove"));
            delBtn.setOnAction(e -> {
                PantryService.removeItem(item.getId());
                refreshAll();
            });

            row.getChildren().addAll(nameLabel, catLabel, qtyLabel, spacer, editBtn, delBtn);
            pantryItemsList.getChildren().add(row);
        }
    }

    // ── Add / Edit Dialogs ─────────────────────────────────────────────

    /** Opens a dialog to add a new pantry item. */
    @FXML
    private void handleAddItem() {
        showItemDialog(null);
    }

    /** Opens a dialog to edit an existing pantry item. */
    private void showEditItemDialog(PantryItem item) {
        showItemDialog(item);
    }

    /**
     * Shows an add/edit dialog for a pantry item.
     * If {@code item} is null, creates a new item. Otherwise edits the existing one.
     *
     * @param item the item to edit, or null for a new item
     */
    private void showItemDialog(PantryItem item) {
        boolean isNew = (item == null);
        Dialog<PantryItem> dialog = new Dialog<>();
        dialog.setTitle(isNew ? "Add Ingredient" : "Edit Ingredient");
        dialog.setHeaderText(isNew ? "Add a new ingredient to your pantry"
                : "Modify ingredient details");

        // Form fields
        TextField nameField = new TextField(isNew ? "" : item.getName());
        nameField.setPromptText("Ingredient name (e.g. Chicken Breast)");

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(
                "Meat", "Vegetables", "Fruits", "Dairy", "Grains",
                "Spices", "Oils", "Canned Goods", "Beverages", "Other");
        categoryCombo.setEditable(true);
        if (!isNew && item.getCategory() != null) categoryCombo.setValue(item.getCategory());

        TextField qtyField = new TextField(isNew ? "" : String.valueOf(item.getQuantity()));
        qtyField.setPromptText("Quantity");

        ComboBox<String> unitCombo = new ComboBox<>();
        unitCombo.getItems().addAll("g", "kg", "ml", "L", "pieces", "cups", "tbsp", "tsp");
        unitCombo.setEditable(true);
        if (!isNew && item.getUnit() != null) unitCombo.setValue(item.getUnit());

        TextField thresholdField = new TextField(isNew ? "1" : String.valueOf(item.getLowThreshold()));
        thresholdField.setPromptText("Low-stock threshold");

        // Layout
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(new Label("Name:"), 0, 0);        grid.add(nameField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);     grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Quantity:"), 0, 2);      grid.add(qtyField, 1, 2);
        grid.add(new Label("Unit:"), 0, 3);          grid.add(unitCombo, 1, 3);
        grid.add(new Label("Low Threshold:"), 0, 4); grid.add(thresholdField, 1, 4);

        dialog.getDialogPane().setContent(grid);
        ButtonType saveBtn = new ButtonType(isNew ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) return null;

                double qty;
                try { qty = Double.parseDouble(qtyField.getText().trim()); }
                catch (NumberFormatException e) { qty = 0; }

                double threshold;
                try { threshold = Double.parseDouble(thresholdField.getText().trim()); }
                catch (NumberFormatException e) { threshold = 1; }

                if (isNew) {
                    PantryItem newItem = new PantryItem(
                            currentUser.getId(), name,
                            categoryCombo.getValue(), unitCombo.getValue(), qty);
                    newItem.setLowThreshold(threshold);
                    return newItem;
                } else {
                    item.setName(name);
                    item.setCategory(categoryCombo.getValue());
                    item.setQuantity(qty);
                    item.setUnit(unitCombo.getValue());
                    item.setLowThreshold(threshold);
                    return item;
                }
            }
            return null;
        });

        Optional<PantryItem> result = dialog.showAndWait();
        result.ifPresent(pantryItem -> {
            if (isNew) {
                PantryService.addItem(pantryItem);
            } else {
                PantryService.updateItem(pantryItem);
            }
            refreshAll();
        });
    }
}
