package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.User;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Handles user authentication: sign-up, login, and session management.
 *
 * <h3>How authentication works</h3>
 * <ol>
 *   <li>On <strong>sign-up</strong>, the password is hashed with SHA-256
 *       before being stored.  Plain-text passwords are never persisted.</li>
 *   <li>On <strong>login</strong>, the entered password is hashed and
 *       compared against the stored hash.  If they match, the user is
 *       authenticated and stored as the "current user" for the session.</li>
 *   <li>The <strong>current user</strong> is held in a static field so
 *       that any controller or service can access the logged-in user's
 *       profile without passing it around manually.</li>
 * </ol>
 *
 * Data is persisted to {@code data/users.json} via {@link DataStore}.
 */
public class AuthService {

    /** JSON file where user accounts are stored. */
    private static final String USERS_FILE = "users.json";

    /** Gson type token for deserializing a List of User objects. */
    private static final Type USER_LIST_TYPE = new TypeToken<List<User>>() {}.getType();

    /**
     * The currently logged-in user for this session.
     * Set after a successful {@link #login} call.
     * Null if no user is logged in.
     */
    private static User currentUser = null;

    /**
     * Registers a new user account with default body metrics.
     *
     * @param username  desired username (must be unique)
     * @param email     email address (must be unique)
     * @param password  plain-text password (will be hashed)
     * @param firstName user's first name
     * @param lastName  user's last name
     * @return true if sign-up succeeded, false if username or email already taken
     */
    public static boolean signUp(String username, String email, String password,
                                 String firstName, String lastName) {
        return signUp(username, email, password, firstName, lastName,
                User.Gender.MALE, 20, 170.0, 65.0, User.ActivityLevel.MODERATELY_ACTIVE);
    }

    /**
     * Registers a new user account with complete anthropometric and lifestyle metrics.
     * Calculates initial BMI and TDEE targets automatically.
     *
     * @param username      unique username
     * @param email         unique email
     * @param password      plain-text password (hashed with SHA-256)
     * @param firstName     first name
     * @param lastName      last name
     * @param gender        biological sex (MALE/FEMALE)
     * @param age           age in years
     * @param heightCm      height in cm
     * @param weightKg      weight in kg
     * @param activityLevel daily physical activity tier
     * @return true if registered successfully, false if username or email is taken
     */
    public static boolean signUp(String username, String email, String password,
                                 String firstName, String lastName, User.Gender gender,
                                 int age, double heightCm, double weightKg,
                                 User.ActivityLevel activityLevel) {
        return signUp(username, email, password, firstName, lastName, gender, age, heightCm, weightKg, activityLevel, null);
    }

    /**
     * Registers a new user account with complete anthropometric, lifestyle, and dietary metrics.
     * Calculates initial BMI and TDEE targets automatically.
     *
     * @param username           unique username
     * @param email              unique email
     * @param password           plain-text password (hashed with SHA-256)
     * @param firstName          first name
     * @param lastName           last name
     * @param gender             biological sex (MALE/FEMALE)
     * @param age                age in years
     * @param heightCm           height in cm
     * @param weightKg           weight in kg
     * @param activityLevel      daily physical activity tier
     * @param dietaryPreferences dietary restrictions/preferences (e.g. Vegetarian, Lactose-Free)
     * @return true if registered successfully, false if username or email is taken
     */
    public static boolean signUp(String username, String email, String password,
                                 String firstName, String lastName, User.Gender gender,
                                 int age, double heightCm, double weightKg,
                                 User.ActivityLevel activityLevel, String dietaryPreferences) {
        List<User> users = DataStore.loadAll(USERS_FILE, USER_LIST_TYPE);

        // Check for duplicate username or email (case-insensitive)
        for (User existing : users) {
            if (existing.getUsername().equalsIgnoreCase(username)) {
                return false; // Username already taken
            }
            if (existing.getEmail().equalsIgnoreCase(email)) {
                return false; // Email already registered
            }
        }

        // Create the new user with a hashed password
        String hash = hashPassword(password);
        User newUser = new User(username, email, hash, firstName, lastName,
                gender, age, heightCm, weightKg, activityLevel);
        if (dietaryPreferences != null && !dietaryPreferences.isBlank()) {
            newUser.setDietaryPreferences(dietaryPreferences.trim());
        }

        // Calculate and set initial calorie target based on BMI & TDEE
        UserService.recalculateAndSave(newUser);
        return true;
    }

    /**
     * Authenticates a user by username/email and password.
     *
     * @param usernameOrEmail the username or email the user entered
     * @param password        the plain-text password to verify
     * @return true if credentials are valid, false otherwise
     */
    public static boolean login(String usernameOrEmail, String password) {
        List<User> users = DataStore.loadAll(USERS_FILE, USER_LIST_TYPE);
        String hash = hashPassword(password);

        for (User user : users) {
            // Match by username OR email (case-insensitive)
            boolean nameMatch = user.getUsername().equalsIgnoreCase(usernameOrEmail);
            boolean emailMatch = user.getEmail().equalsIgnoreCase(usernameOrEmail);

            if ((nameMatch || emailMatch) && user.getPasswordHash().equals(hash)) {
                currentUser = user; // Store as the active session user
                return true;
            }
        }
        return false; // No matching credentials found
    }

    /**
     * Returns the currently logged-in user, or null if nobody is logged in.
     */
    public static User getCurrentUser() {
        return currentUser;
    }

    /**
     * Logs out the current user by clearing the session.
     */
    public static void logout() {
        currentUser = null;
    }

    /**
     * Hashes a password using SHA-256.
     *
     * SHA-256 is sufficient for a school project.  In production, you'd
     * use bcrypt or Argon2 with per-user salts.
     *
     * @param password the plain-text password
     * @return the hexadecimal SHA-256 hash string
     */
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hex string
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JDK, but just in case
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
