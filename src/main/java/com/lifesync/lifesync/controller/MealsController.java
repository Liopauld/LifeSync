package com.lifesync.lifesync.controller;

import com.lifesync.lifesync.SceneManager;
import com.lifesync.lifesync.model.Activity;
import com.lifesync.lifesync.model.NutritionEntry;
import com.lifesync.lifesync.model.PantryItem;
import com.lifesync.lifesync.model.Recipe;
import com.lifesync.lifesync.model.User;
import com.lifesync.lifesync.service.ActivityService;
import com.lifesync.lifesync.service.AuthService;
import com.lifesync.lifesync.service.GeminiService;
import com.lifesync.lifesync.service.NutritionService;
import com.lifesync.lifesync.service.PantryService;
import com.lifesync.lifesync.service.RecipeService;
import com.lifesync.lifesync.service.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller for the redesigned Meals & Nutrition view (meals-view.fxml).
 *
 * <p>Implements the clean team UI mockup including:
 * <ul>
 *   <li>Top "FOOD & NUTRITION / Meals" heading with action buttons.</li>
 *   <li>"Meals & Nutrition" sub-heading with "+ Add recipe" button.</li>
 *   <li>Horizontal recipe cards with food emoji header, name, ingredient summary,
 *       prep time, calories, and "Add to meal plan" action button.</li>
 *   <li>"Meal plan" section displaying today's scheduled meal activities.</li>
 *   <li>Full Recipe Details Modal overlay with ingredient checklist and planning actions.</li>
 * </ul>
 *
 * <p>The Gemini AI integration provides intelligent meal recommendations when
 * the user clicks the AI suggestion button (✨), analyzing their BMI, pantry
 * stock, and calorie targets.</p>
 *
 * @author LifeSync Development Team
 * @version 3.0 (Phase 3 - Simplified Meals UI + Gemini AI)
 */
public class MealsController {

    // ── FXML Fields: Recipe Cards Container ────────────────────────────

    /** Horizontal container for the recipe cards row. */
    @FXML private HBox recipeCardsContainer;

    /** Container for the Meal Plan list entries at the bottom. */
    @FXML private VBox mealPlanListBox;

    // ── FXML Fields: AI Chef Pantry Banner ────────────────────────────

    @FXML private HBox aiChefBanner;
    @FXML private Label aiChefTitleLabel;
    @FXML private Label aiChefSubtitleLabel;
    @FXML private Button btnRegenerateAi;
    @FXML private Button btnGoPantry;

    // ── FXML Fields: Modal Overlay ────────────────────────────────────

    /** The full-screen semi-transparent overlay for the recipe detail modal. */
    @FXML private StackPane recipeModalOverlay;

    /** The recipe title displayed in the modal header. */
    @FXML private Label modalTitle;

    /** Container for recipe tag pills in the modal header. */
    @FXML private HBox modalTagsBox;

    /** Toggle button to save/unsave the recipe as a favorite. */
    @FXML private Button modalFavBtn;

    /** Calorie count label in the modal nutrient specs bar. */
    @FXML private Label modalCalories;

    /** Protein value label in the modal nutrient specs bar. */
    @FXML private Label modalProtein;

    /** Carbohydrate value label in the modal nutrient specs bar. */
    @FXML private Label modalCarbs;

    /** Fat value label in the modal nutrient specs bar. */
    @FXML private Label modalFat;

    /** Preparation time label in the modal nutrient specs bar. */
    @FXML private Label modalPrepTime;

    /** Cooking time label in the modal nutrient specs bar. */
    @FXML private Label modalCookTime;

    /** Recipe description text displayed in the modal scrollable area. */
    @FXML private Label modalDescription;

    /** Container for ingredient CheckBox items in the modal. */
    @FXML private VBox modalIngredientsList;

    /** Recipe cooking instructions displayed in the modal scrollable area. */
    @FXML private Label modalInstructions;

    // ── Internal State ────────────────────────────────────────────────

    /** The recipe currently displayed in the detail modal (null if modal is closed). */
    private Recipe selectedModalRecipe = null;

    // ── Food Emoji Pool for Card Headers ──────────────────────────────

    /** Pool of food-related emojis used for recipe card header decoration. */
    private static final String[] FOOD_EMOJIS = {
            "🥗", "🍳", "🥘", "🍲", "🥑", "🍝", "🥙", "🍜", "🥣", "🫕",
            "🍱", "🥡", "🧆", "🌮", "🥪"
    };

    // ═══════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Called automatically after FXML injection.
     * Loads recipe cards and renders the meal plan section.
     */
    @FXML
    public void initialize() {
        // Ensure modal is initially hidden
        if (recipeModalOverlay != null) {
            recipeModalOverlay.setVisible(false);
            recipeModalOverlay.setManaged(false);
        }

        // Render recipe cards from pantry using AI
        loadAiGeneratedRecipes(false);

        // Render today's meal plan
        refreshMealPlan();
    }

    // ═══════════════════════════════════════════════════════════════════
    // AI-Driven Recipe Generation from Pantry
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads recipes dynamically generated by the AI based strictly on what
     * ingredients are currently in the user's pantry, respecting their dietary
     * preferences (e.g. lactose intolerance, vegetarianism) and calibrated to
     * their BMI and calorie goals.
     *
     * @param forceRemote true to trigger a new remote call to Google Gemini
     */
    private void loadAiGeneratedRecipes(boolean forceRemote) {
        recipeCardsContainer.getChildren().clear();

        User currentUser = AuthService.getCurrentUser();
        if (currentUser == null) {
            showEmptyPantryCard("Please sign in to view AI-generated recipes tailored to your pantry.");
            return;
        }

        List<PantryItem> pantryItems = PantryService.getPantryItems(currentUser.getId());
        List<PantryItem> inStock = (pantryItems != null)
                ? pantryItems.stream()
                        .filter(p -> p.getQuantity() > 0)
                        .collect(Collectors.toList())
                : new ArrayList<>();

        if (inStock.isEmpty()) {
            if (aiChefTitleLabel != null) {
                aiChefTitleLabel.setText("🥫 Pantry is Empty • Add Ingredients to Generate Meals");
                aiChefSubtitleLabel.setText("Add ingredients (e.g. Eggs, Bread, Chicken, Rice) in the Pantry tab so the AI Chef can create custom recipes for you!");
            }
            showEmptyPantryCard("Your pantry is currently empty!\n\nAdd ingredients in the Pantry tab and our AI Chef will dynamically create custom recipes tailored to your available stock and dietary preferences.");
            return;
        }

        String stockSummary = inStock.stream().map(PantryItem::getName).limit(5).collect(Collectors.joining(", "));
        String dietStr = (currentUser.getDietaryPreferences() != null && !currentUser.getDietaryPreferences().isBlank())
                ? currentUser.getDietaryPreferences().trim()
                : "Standard";

        if (aiChefTitleLabel != null) {
            aiChefTitleLabel.setText("✨ AI Chef • Recipes Created From Your In-Stock Pantry (" + stockSummary + ")");
            aiChefSubtitleLabel.setText("Dietary Preference: " + dietStr + " • Focus: "
                    + (currentUser.getBmiCategory() != null ? currentUser.getBmiCategory().getDisplayName() : "Healthy")
                    + " (" + String.format("%.1f", currentUser.getBmi()) + " BMI, " + currentUser.getDailyCalorieTarget() + " kcal)");
        }

        // 1. Immediately render local synthesized recipes matching pantry & diet
        List<Recipe> localRecipes = RecipeService.generateLocalPantryRecipes(currentUser, inStock);
        renderRecipeList(localRecipes);

        // 2. Concurrently call Google Gemini AI to generate creative new recipes from pantry
        final List<PantryItem> finalStock = inStock;
        GeminiService.generateRecipesFromPantry(currentUser, finalStock)
                .thenAccept(aiRecipes -> {
                    if (aiRecipes != null && !aiRecipes.isEmpty()) {
                        Platform.runLater(() -> {
                            renderRecipeList(aiRecipes);
                            if (aiChefSubtitleLabel != null) {
                                aiChefSubtitleLabel.setText("✨ Freshly crafted by Google Gemini AI from your pantry (" + stockSummary + ") • Respecting: " + dietStr);
                            }
                        });
                    }
                });
    }

    /**
     * Renders a list of recipes into the horizontal card container.
     *
     * @param recipes list of recipes to display
     */
    private void renderRecipeList(List<Recipe> recipes) {
        recipeCardsContainer.getChildren().clear();
        for (int i = 0; i < recipes.size(); i++) {
            VBox card = createRecipeCard(recipes.get(i), i);
            recipeCardsContainer.getChildren().add(card);
        }
    }

    /**
     * Displays a friendly placeholder card when the pantry has no items.
     *
     * @param message informative message with guidance
     */
    private void showEmptyPantryCard(String message) {
        VBox emptyCard = new VBox(12);
        emptyCard.setAlignment(Pos.CENTER);
        emptyCard.setPrefWidth(460);
        emptyCard.setPadding(new Insets(30, 25, 30, 25));
        emptyCard.getStyleClass().add("empty-state-card");

        Label icon = new Label("🥫");
        icon.getStyleClass().add("empty-state-icon");

        Label heading = new Label("No Pantry Ingredients in Stock");
        heading.getStyleClass().add("empty-state-heading");

        Label msg = new Label(message);
        msg.getStyleClass().add("empty-state-message");
        msg.setWrapText(true);

        Button goToPantryBtn = new Button("🥫 Go to Pantry & Add Items");
        goToPantryBtn.getStyleClass().add("meals-add-to-plan-btn");
        goToPantryBtn.setOnAction(e -> handleGoToPantry());

        emptyCard.getChildren().addAll(icon, heading, msg, goToPantryBtn);
        recipeCardsContainer.getChildren().add(emptyCard);
    }

    /**
     * Handles the "🔄 Create New Recipes" button click.
     */
    @FXML
    private void handleRegenerateRecipes() {
        if (aiChefSubtitleLabel != null) {
            aiChefSubtitleLabel.setText("🍳 Asking LifeSync AI Chef to create new recipes from your pantry...");
        }
        loadAiGeneratedRecipes(true);
    }

    /**
     * Handles the "🥫 Pantry" button click to navigate to the Pantry view.
     */
    @FXML
    private void handleGoToPantry() {
        if (MainController.getInstance() != null) {
            MainController.getInstance().navigateToPantry();
        } else {
            SceneManager.loadContent("fxml/pantry-view.fxml");
        }
    }

    /**
     * Creates a single recipe card VBox matching the mockup design.
     * The card has a colored top area with a food emoji, recipe details below,
     * and an orange "Add to meal plan" button at the bottom.
     *
     * @param recipe the recipe to display
     * @param index  the card index (used for color/emoji rotation)
     * @return a fully styled VBox representing the recipe card
     */
    private VBox createRecipeCard(Recipe recipe, int index) {
        VBox card = new VBox(0);
        card.setPrefWidth(300);
        card.setMinWidth(280);
        card.setMaxWidth(320);
        card.getStyleClass().add("meals-recipe-card");

        // ── Top colored area with food emoji matching recipe theme ──
        String[] bgColors = {
                "#E8F5E9", "#FFFDE7", "#FFF3E0", "#E3F2FD",
                "#F3E5F5", "#FFF8E1", "#E8EAF6", "#FCE4EC"
        };
        String[] darkBgColors = {
                "#1C2B22", "#2B2818", "#2F2218", "#182433",
                "#2B1B35", "#2D2618", "#1E2035", "#301B26"
        };
        boolean isDark = com.lifesync.lifesync.service.ThemeManager.isDarkMode();
        String bgColor = isDark ? darkBgColors[index % darkBgColors.length] : bgColors[index % bgColors.length];

        StackPane emojiArea = new StackPane();
        emojiArea.setPrefHeight(130);
        emojiArea.setMinHeight(130);
        emojiArea.setStyle("-fx-background-color: " + bgColor + "; "
                + "-fx-background-radius: 16 16 0 0;");

        String rNameLower = recipe.getName().toLowerCase();
        String emoji;
        if (rNameLower.contains("egg") || rNameLower.contains("omelette")) emoji = "🍳";
        else if (rNameLower.contains("toast") || rNameLower.contains("bread") || rNameLower.contains("sandwich")) emoji = "🥪";
        else if (rNameLower.contains("chicken") || rNameLower.contains("meat")) emoji = "🍗";
        else if (rNameLower.contains("rice") || rNameLower.contains("bowl")) emoji = "🍚";
        else if (rNameLower.contains("pasta") || rNameLower.contains("noodle")) emoji = "🍝";
        else if (rNameLower.contains("salad")) emoji = "🥗";
        else if (rNameLower.contains("pancake") || rNameLower.contains("french toast")) emoji = "🥞";
        else if (rNameLower.contains("soup")) emoji = "🥣";
        else emoji = FOOD_EMOJIS[index % FOOD_EMOJIS.length];

        Label emojiLabel = new Label(emoji);
        emojiLabel.getStyleClass().add("recipe-card-emoji");
        emojiArea.getChildren().add(emojiLabel);

        // ── Card body ──
        VBox body = new VBox(6);
        body.setPadding(new Insets(14, 16, 16, 16));

        // Recipe name
        Label nameLabel = new Label(recipe.getName());
        nameLabel.getStyleClass().add("recipe-card-title");
        nameLabel.setWrapText(true);

        // Ingredient summary (first few ingredients, comma-separated)
        String ingredientSummary = buildIngredientSummary(recipe);
        Label ingredientsLabel = new Label(ingredientSummary);
        ingredientsLabel.getStyleClass().add("recipe-card-desc");
        ingredientsLabel.setWrapText(true);

        // Time + Calories row
        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);
        metaRow.setPadding(new Insets(6, 0, 10, 0));

        int totalTime = recipe.getPrepTimeMinutes() + recipe.getCookTimeMinutes();
        Label timeLabel = new Label("⏱ " + totalTime + " min");
        timeLabel.getStyleClass().add("meals-meta-pill");

        Label calLabel = new Label("≈ " + (int) recipe.getCalories() + " kcal");
        calLabel.getStyleClass().add("meals-meta-pill");

        metaRow.getChildren().addAll(timeLabel, calLabel);

        // Highlight dietary tags if present (e.g. Lactose-Free)
        if (recipe.getTags() != null) {
            for (String tag : recipe.getTags()) {
                if (tag.equalsIgnoreCase("Lactose-Free") || tag.equalsIgnoreCase("Dairy Free")
                        || tag.equalsIgnoreCase("Vegan") || tag.equalsIgnoreCase("Vegetarian")) {
                    Label dietPill = new Label(tag);
                    dietPill.getStyleClass().addAll("diet-pill", "diet-pill-success");
                    metaRow.getChildren().add(dietPill);
                    break;
                }
            }
        }

        // "Add to meal plan" orange button
        Button addBtn = new Button("Add to meal plan");
        addBtn.getStyleClass().add("meals-add-to-plan-btn");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> handleAddRecipeToPlan(recipe));

        body.getChildren().addAll(nameLabel, ingredientsLabel, metaRow, addBtn);

        // Click the card body to view recipe details
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof Button) return; // Don't trigger on button click
            showRecipeModal(recipe);
        });

        card.getChildren().addAll(emojiArea, body);
        return card;
    }

    /**
     * Builds a short comma-separated summary of the first few key ingredients.
     *
     * @param recipe the recipe to summarize
     * @return a concise ingredient list string
     */
    private String buildIngredientSummary(Recipe recipe) {
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            return recipe.getDescription() != null ? recipe.getDescription() : "Fresh ingredients";
        }

        // Extract just the ingredient names (strip quantities like "2 cups")
        return recipe.getIngredients().stream()
                .limit(4)
                .map(ing -> {
                    // Try to extract just the ingredient name
                    String[] parts = ing.split(" ", 3);
                    if (parts.length >= 3) return parts[2]; // "2 cups Rice" -> "Rice"
                    if (parts.length >= 2) return parts[1]; // "1 Chicken" -> "Chicken"
                    return parts[0];
                })
                .collect(Collectors.joining(", "));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Meal Plan Section
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Refreshes the "Meal plan" section at the bottom of the view.
     * Shows today's scheduled MEAL activities, or a placeholder if empty.
     */
    private void refreshMealPlan() {
        mealPlanListBox.getChildren().clear();

        User user = AuthService.getCurrentUser();
        if (user == null) {
            showMealPlanPlaceholder();
            return;
        }

        String today = LocalDate.now().toString();
        List<Activity> mealActivities = ActivityService.getActivitiesForDate(user.getId(), today)
                .stream()
                .filter(a -> a.getType() == Activity.ActivityType.MEAL)
                .collect(Collectors.toList());

        if (mealActivities.isEmpty()) {
            showMealPlanPlaceholder();
            return;
        }

        for (Activity meal : mealActivities) {
            HBox row = createMealPlanRow(meal);
            mealPlanListBox.getChildren().add(row);
        }
    }

    /**
     * Shows the "No meals scheduled" placeholder text.
     */
    private void showMealPlanPlaceholder() {
        Label placeholder = new Label("No meals scheduled yet. Add a recipe to your plan.");
        placeholder.getStyleClass().add("meal-plan-placeholder");
        placeholder.setAlignment(Pos.CENTER);
        mealPlanListBox.setAlignment(Pos.CENTER);
        mealPlanListBox.getChildren().add(placeholder);
    }

    /**
     * Creates a single meal plan row with time, title, and status.
     *
     * @param meal the scheduled MEAL activity
     * @return a styled HBox representing the meal plan entry
     */
    private HBox createMealPlanRow(Activity meal) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("meal-plan-item-row");
        row.setPadding(new Insets(12, 16, 12, 16));

        Label timeLabel = new Label("⏱ " + meal.getStartTime());
        timeLabel.getStyleClass().add("meal-plan-item-time");
        timeLabel.setMinWidth(70);

        Label titleLabel = new Label(meal.getTitle());
        titleLabel.getStyleClass().add("meal-plan-item-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusPill = new Label(meal.getStatus().name());
        statusPill.getStyleClass().addAll("status-pill",
                meal.getStatus() == Activity.Status.COMPLETED ? "status-completed" : "status-upcoming");

        Button removeBtn = new Button("✕");
        removeBtn.getStyleClass().add("meal-plan-remove-btn");
        removeBtn.setOnAction(e -> {
            ActivityService.deleteActivity(meal.getId());
            refreshMealPlan();
        });

        row.getChildren().addAll(timeLabel, titleLabel, statusPill, removeBtn);
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Action Handlers: Header Buttons & Recipe Catalog
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Filters catalog recipes to ONLY show:
     * 1) Recipes the user has favorited.
     * 2) Recipes created from / matching available in-stock pantry ingredients.
     */
    private List<Recipe> getEligibleCatalogRecipes() {
        User user = AuthService.getCurrentUser();
        String userId = user != null ? user.getId() : "default_user";

        List<Recipe> eligible = new ArrayList<>();
        java.util.Set<String> seenIds = new java.util.HashSet<>();

        // 1. Favorited recipes
        List<Recipe> favs = RecipeService.getFavorites();
        for (Recipe r : favs) {
            if (seenIds.add(r.getId())) {
                eligible.add(r);
            }
        }

        // 2. AI-generated / Pantry recipes that match available in-stock ingredients
        if (user != null) {
            List<PantryItem> pantryItems = PantryService.getPantryItems(userId);
            List<PantryItem> inStock = (pantryItems != null)
                    ? pantryItems.stream().filter(p -> p.getQuantity() > 0).collect(Collectors.toList())
                    : new ArrayList<>();

            if (!inStock.isEmpty()) {
                List<Recipe> pantryRecipes = RecipeService.generateLocalPantryRecipes(user, inStock);
                for (Recipe r : pantryRecipes) {
                    if (seenIds.add(r.getId())) {
                        eligible.add(r);
                    }
                }
            }
        }

        eligible.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return eligible;
    }

    /**
     * Handles the "+ Add recipe" button.
     * Opens a curated dialog showing ONLY favorited recipes and recipes generated
     * from available in-stock pantry ingredients.
     */
    @FXML
    private void handleAddRecipe() {
        List<Recipe> eligibleRecipes = getEligibleCatalogRecipes();

        Dialog<Void> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Recipe Catalog");
        dialog.setHeaderText("Browse Favorited & In-Stock Pantry Recipes");

        VBox content = new VBox(10);
        content.setPrefWidth(540);
        content.setPadding(new Insets(10));

        if (eligibleRecipes.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(30, 20, 30, 20));

            Label emptyIcon = new Label("🥫");
            emptyIcon.setStyle("-fx-font-size: 36px;");

            Label emptyMsg = new Label("Your Recipe Catalog only shows recipes you have favorited (❤️) or recipes that can be made from ingredients in your pantry.\n\nAdd ingredients in the Pantry tab or favorite recipes to view them here!");
            emptyMsg.setWrapText(true);
            emptyMsg.setStyle("-fx-text-alignment: center; -fx-font-size: 13px; -fx-text-fill: #8E8E93;");

            emptyBox.getChildren().addAll(emptyIcon, emptyMsg);
            content.getChildren().add(emptyBox);
        } else {
            TextField searchField = new TextField();
            searchField.setPromptText("Search favorited & pantry-ready recipes...");

            VBox resultsList = new VBox(8);
            renderRecipeSearchResults(resultsList, eligibleRecipes, dialog);

            searchField.textProperty().addListener((obs, old, query) -> {
                List<Recipe> filtered = eligibleRecipes.stream()
                        .filter(r -> r.getName().toLowerCase().contains(query.toLowerCase()))
                        .collect(Collectors.toList());
                renderRecipeSearchResults(resultsList, filtered, dialog);
            });

            ScrollPane scroll = new ScrollPane(resultsList);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(380);

            content.getChildren().addAll(searchField, scroll);
        }

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.show();
    }

    /**
     * Renders recipe search results inside the Add Recipe dialog.
     */
    private void renderRecipeSearchResults(VBox container, List<Recipe> recipes, Dialog<?> dialog) {
        container.getChildren().clear();
        for (Recipe r : recipes) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("recipe-search-row");

            VBox nameBox = new VBox(2);
            Label nameLabel = new Label(r.getName());
            nameLabel.getStyleClass().add("recipe-search-name");

            HBox badgeBox = new HBox(6);
            if (r.isFavorite()) {
                Label favBadge = new Label("❤️ Favorited");
                favBadge.setStyle("-fx-font-size: 10px; -fx-text-fill: #E11D48; -fx-font-weight: bold;");
                badgeBox.getChildren().add(favBadge);
            }
            Label stockBadge = new Label("🥫 Pantry-Ready");
            stockBadge.setStyle("-fx-font-size: 10px; -fx-text-fill: #10B981; -fx-font-weight: bold;");
            badgeBox.getChildren().add(stockBadge);

            nameBox.getChildren().addAll(nameLabel, badgeBox);
            HBox.setHgrow(nameBox, Priority.ALWAYS);

            Label metaLabel = new Label((int) r.getCalories() + " kcal • " + r.getPrepTimeMinutes() + " min");
            metaLabel.getStyleClass().add("recipe-search-meta");

            Button addBtn = new Button("📅 Add to Plan");
            addBtn.getStyleClass().add("recipe-search-add-btn");
            addBtn.setOnAction(e -> {
                dialog.close();
                handleAddRecipeToPlan(r);
            });

            row.getChildren().addAll(nameBox, metaLabel, addBtn);
            container.getChildren().add(row);
        }
    }

    /**
     * Handles the "+ Add to Plan" button in the header.
     */
    @FXML
    private void handleQuickPlanMeal() {
        handleAddRecipe();
    }

    /**
     * Handles the "Plan meals by date" hyperlink.
     */
    @FXML
    private void handlePlanByDate() {
        handleAddRecipe();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Action: Add Recipe to Meal Plan with Category & Non-Overlapping Slot
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds a recipe to the meal plan by prompting the user for meal category
     * (Breakfast, Lunch, Snack, Dinner) and date, validating that no
     * overlapping time slots occur.
     *
     * @param recipe the recipe to schedule
     */
    private void handleAddRecipeToPlan(Recipe recipe) {
        User user = AuthService.getCurrentUser();
        String userId = user != null ? user.getId() : "default_user";

        Dialog<Activity> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("Schedule Meal Plan");
        dialog.setHeaderText("Add \"" + recipe.getName() + "\" (" + (int) recipe.getCalories() + " kcal)");

        ComboBox<String> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(
                "Breakfast (08:00 - 08:30)",
                "Lunch (12:00 - 12:45)",
                "Snack (15:30 - 16:00)",
                "Dinner (18:30 - 19:15)",
                "Custom Time"
        );
        categoryCombo.setValue("Lunch (12:00 - 12:45)");

        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField startField = new TextField("12:00");
        TextField endField = new TextField("12:45");

        categoryCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                if (newVal.startsWith("Breakfast")) {
                    startField.setText("08:00");
                    endField.setText("08:30");
                } else if (newVal.startsWith("Lunch")) {
                    startField.setText("12:00");
                    endField.setText("12:45");
                } else if (newVal.startsWith("Snack")) {
                    startField.setText("15:30");
                    endField.setText("16:00");
                } else if (newVal.startsWith("Dinner")) {
                    startField.setText("18:30");
                    endField.setText("19:15");
                }
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(20));

        grid.add(new Label("Meal Category:"), 0, 0);
        grid.add(categoryCombo, 1, 0);
        grid.add(new Label("Date:"), 0, 1);
        grid.add(datePicker, 1, 1);
        grid.add(new Label("Start Time (HH:mm):"), 0, 2);
        grid.add(startField, 1, 2);
        grid.add(new Label("End Time (HH:mm):"), 0, 3);
        grid.add(endField, 1, 3);

        dialog.getDialogPane().setContent(grid);
        ButtonType scheduleBtn = new ButtonType("Schedule Meal", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(scheduleBtn, ButtonType.CANCEL);

        dialog.setResultConverter(btn -> {
            if (btn == scheduleBtn) {
                LocalDate d = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
                String st = startField.getText().trim();
                String et = endField.getText().trim();

                try {
                    LocalTime s = LocalTime.parse(st);
                    LocalTime e = LocalTime.parse(et);
                    if (!s.isBefore(e)) {
                        Platform.runLater(() -> {
                            Alert a = new Alert(Alert.AlertType.ERROR);
                            ThemeManager.applyTheme(a.getDialogPane());
                            a.setTitle("Invalid Time");
                            a.setHeaderText("End time must be after start time.");
                            a.showAndWait();
                        });
                        return null;
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.ERROR);
                        ThemeManager.applyTheme(a.getDialogPane());
                        a.setTitle("Invalid Time Format");
                        a.setHeaderText("Please enter times in HH:mm format (e.g. 12:00).");
                        a.showAndWait();
                    });
                    return null;
                }

                // Strict overlap conflict check
                List<Activity> conflicts = ActivityService.findConflicts(userId, d.toString(), st, et, null);
                if (!conflicts.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert conflictAlert = new Alert(Alert.AlertType.ERROR);
                        ThemeManager.applyTheme(conflictAlert.getDialogPane());
                        conflictAlert.setTitle("Schedule Conflict Detected");
                        conflictAlert.setHeaderText("Cannot schedule meal due to overlapping time:");
                        StringBuilder sb = new StringBuilder();
                        for (Activity c : conflicts) {
                            sb.append("• ").append(c.getTitle()).append(" (")
                              .append(c.getStartTime()).append(" - ").append(c.getEndTime()).append(")\n");
                        }
                        conflictAlert.setContentText(sb + "\nOverlapping time slots are not allowed. Please choose a different category or adjust the time.");
                        conflictAlert.showAndWait();
                    });
                    return null;
                }

                String catName = categoryCombo.getValue().split(" ")[0];
                Activity act = new Activity(
                        userId,
                        "[" + catName + "] " + recipe.getName(),
                        Activity.ActivityType.MEAL,
                        d.toString(),
                        st,
                        et
                );
                act.setDescription("Enjoy " + recipe.getName() + " (" + (int) recipe.getCalories() + " kcal)");
                return act;
            }
            return null;
        });

        Optional<Activity> res = dialog.showAndWait();
        res.ifPresent(activity -> {
            ActivityService.addActivity(activity);
            refreshMealPlan();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Meal Scheduled");
            alert.setHeaderText("Added to Meal Plan");
            alert.setContentText(activity.getTitle() + " has been scheduled for " + activity.getDate()
                    + " (" + activity.getStartTime() + " - " + activity.getEndTime() + ") without conflicts!");
            alert.showAndWait();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // AI Suggestion (Gemini Integration)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the LifeSync AI Meal Coach dialog powered by Google Gemini.
     * Sends user BMI, calorie target, and available pantry items to Gemini
     * for intelligent meal recommendations, then cross-references local
     * recipes with the user's pantry stock.
     */
    @FXML
    private void handleAiSuggest() {
        User user = AuthService.getCurrentUser();
        if (user == null) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Sign In Required");
            alert.setHeaderText("Please sign in to use AI suggestions");
            alert.show();
            return;
        }

        List<PantryItem> pantry = PantryService.getPantryItems(user.getId());

        Dialog<Void> dialog = new Dialog<>();
        ThemeManager.applyTheme(dialog.getDialogPane());
        dialog.setTitle("LifeSync AI Meal Coach");
        dialog.setHeaderText("✨ Personalized Pantry & BMI Recommendations (Google Gemini)");

        VBox content = new VBox(14);
        content.setPrefWidth(540);
        content.setPadding(new Insets(15));

        // Profile & Pantry Context Summary Badges
        HBox contextPills = new HBox(8);
        contextPills.setAlignment(Pos.CENTER_LEFT);

        Label bmiPill = new Label("BMI " + String.format("%.1f", user.getBmi()) + " (" +
                (user.getBmiCategory() != null ? user.getBmiCategory().getDisplayName() : "Normal") + ")");
        bmiPill.getStyleClass().add("meal-ai-pill-bmi");

        Label calPill = new Label(user.getDailyCalorieTarget() + " kcal target");
        calPill.getStyleClass().add("meal-ai-pill-cal");

        long inStockCount = pantry.stream().filter(p -> !p.isLow()).count();
        Label pantryPill = new Label(inStockCount + " pantry items in stock");
        pantryPill.getStyleClass().add("meal-ai-pill-pantry");

        contextPills.getChildren().addAll(bmiPill, calPill, pantryPill);

        // AI Advice text
        Label aiTextLabel = new Label("Consulting Google Gemini AI with your pantry stock and BMI goals...");
        aiTextLabel.setWrapText(true);
        aiTextLabel.setMaxWidth(520);
        aiTextLabel.getStyleClass().add("meal-ai-advice-text");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(26, 26);

        HBox loadingBox = new HBox(10, spinner, new Label("Synthesizing personalized culinary guidance..."));
        loadingBox.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(contextPills, new Separator(), loadingBox, aiTextLabel);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(480);
        scrollPane.setPrefViewportWidth(560);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        // Call Gemini Asynchronously
        GeminiService.generateMealRecommendationsFromPantry(user, pantry)
                .thenAccept(recommendation -> {
                    Platform.runLater(() -> {
                        content.getChildren().remove(loadingBox);
                        aiTextLabel.setText(recommendation);

                        // Find matching recipes in local catalog
                        List<RecipeService.RecipeMatch> matches = RecipeService.matchWithPantry(user.getId()).stream()
                                .filter(m -> m.matchPercent > 0)
                                .limit(3)
                                .toList();

                        if (!matches.isEmpty()) {
                            Label matchHeading = new Label("🍳 Matching Recipes from Your Catalog:");
                            matchHeading.getStyleClass().add("meal-ai-match-heading");
                            content.getChildren().add(new Separator());
                            content.getChildren().add(matchHeading);

                            VBox matchesList = new VBox(6);
                            for (RecipeService.RecipeMatch m : matches) {
                                Recipe r = m.recipe;
                                HBox matchRow = new HBox(10);
                                matchRow.setAlignment(Pos.CENTER_LEFT);
                                matchRow.getStyleClass().add("meal-ai-match-row");

                                Label rName = new Label(r.getName());
                                rName.getStyleClass().add("recipe-search-name");
                                HBox.setHgrow(rName, Priority.ALWAYS);

                                Label rScore = new Label((int) m.matchPercent + "% stock match");
                                rScore.getStyleClass().add("meal-ai-match-score");

                                Button viewBtn = new Button("View Recipe");
                                viewBtn.getStyleClass().add("meal-ai-match-btn");
                                viewBtn.setOnAction(e -> {
                                    dialog.close();
                                    showRecipeModal(r);
                                });

                                matchRow.getChildren().addAll(rName, rScore, viewBtn);
                                matchesList.getChildren().add(matchRow);
                            }
                            content.getChildren().add(matchesList);
                        }
                    });
                });

        dialog.show();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Recipe Detail Modal
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Opens the Recipe Details modal and populates it with the selected recipe's
     * nutritional data, ingredients, and cooking instructions.
     *
     * @param recipe the recipe to display in the modal
     */
    private void showRecipeModal(Recipe recipe) {
        selectedModalRecipe = recipe;
        recipeModalOverlay.setVisible(true);
        recipeModalOverlay.setManaged(true);

        modalTitle.setText(recipe.getName());
        modalCalories.setText((int) recipe.getCalories() + " kcal");
        modalProtein.setText(String.format("%.0fg", recipe.getProteinG()));
        modalCarbs.setText(String.format("%.0fg", recipe.getCarbsG()));
        modalFat.setText(String.format("%.0fg", recipe.getFatG()));
        modalPrepTime.setText(recipe.getPrepTimeMinutes() + " min");
        modalCookTime.setText(recipe.getCookTimeMinutes() + " min");
        modalDescription.setText(recipe.getDescription() != null ? recipe.getDescription() : "");
        modalInstructions.setText(recipe.getInstructions() != null ? recipe.getInstructions() : "");

        modalFavBtn.setText(recipe.isFavorite() ? "♥ Saved" : "♡ Favorite");
        if (recipe.isFavorite()) {
            if (!modalFavBtn.getStyleClass().contains("fav-heart-active")) {
                modalFavBtn.getStyleClass().add("fav-heart-active");
            }
        } else {
            modalFavBtn.getStyleClass().remove("fav-heart-active");
        }

        // Tags
        modalTagsBox.getChildren().clear();
        if (recipe.getTags() != null) {
            for (String tag : recipe.getTags()) {
                Label pill = new Label(tag);
                pill.getStyleClass().add("recipe-tag");
                modalTagsBox.getChildren().add(pill);
            }
        }

        // Ingredients with CheckBoxes
        modalIngredientsList.getChildren().clear();
        if (recipe.getIngredients() != null) {
            for (String ing : recipe.getIngredients()) {
                CheckBox item = new CheckBox(ing);
                item.getStyleClass().add("recipe-modal-checkbox");
                modalIngredientsList.getChildren().add(item);
            }
        }
    }

    /**
     * Closes the recipe detail modal overlay.
     */
    @FXML
    private void handleCloseModal() {
        recipeModalOverlay.setVisible(false);
        recipeModalOverlay.setManaged(false);
        selectedModalRecipe = null;
    }

    /**
     * Toggles the favorite status of the currently displayed recipe in the modal.
     */
    @FXML
    private void handleToggleModalFavorite() {
        if (selectedModalRecipe == null) return;
        RecipeService.toggleFavorite(selectedModalRecipe.getId());
        selectedModalRecipe.setFavorite(!selectedModalRecipe.isFavorite());
        modalFavBtn.setText(selectedModalRecipe.isFavorite() ? "♥ Saved" : "♡ Favorite");
        if (selectedModalRecipe.isFavorite()) {
            if (!modalFavBtn.getStyleClass().contains("fav-heart-active")) {
                modalFavBtn.getStyleClass().add("fav-heart-active");
            }
        } else {
            modalFavBtn.getStyleClass().remove("fav-heart-active");
        }
        loadAiGeneratedRecipes(false);
    }

    /**
     * Logs the currently displayed recipe's nutrition as eaten today.
     */
    @FXML
    private void handleModalLogNutrition() {
        if (selectedModalRecipe == null) return;
        User user = AuthService.getCurrentUser();
        String userId = user != null ? user.getId() : "default_user";

        NutritionEntry.MealType type;
        try {
            type = NutritionEntry.MealType.valueOf(selectedModalRecipe.getMealType().toUpperCase());
        } catch (Exception ex) {
            type = NutritionEntry.MealType.LUNCH;
        }

        NutritionEntry entry = new NutritionEntry(
                userId,
                LocalDate.now().toString(),
                type,
                selectedModalRecipe.getName(),
                selectedModalRecipe.getCalories(),
                selectedModalRecipe.getProteinG(),
                selectedModalRecipe.getCarbsG(),
                selectedModalRecipe.getFatG()
        );
        NutritionService.addEntry(entry);

        handleCloseModal();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Nutrition Logged");
        alert.setHeaderText("Meal Intake Recorded");
        alert.setContentText(selectedModalRecipe.getName() + " (" + (int) selectedModalRecipe.getCalories()
                + " kcal) was added to your daily nutrition totals!");
        alert.show();
    }

    /**
     * Adds the currently displayed modal recipe to today's meal plan.
     */
    @FXML
    private void handleModalAddToPlan() {
        if (selectedModalRecipe == null) return;
        handleAddRecipeToPlan(selectedModalRecipe);
        handleCloseModal();
    }
}
