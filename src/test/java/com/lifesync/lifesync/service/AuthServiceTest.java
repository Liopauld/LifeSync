package com.lifesync.lifesync.service;

import com.google.gson.reflect.TypeToken;
import com.lifesync.lifesync.model.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises recovery through the same JSON store used by sign-up and login. */
class AuthServiceTest {
    @TempDir Path directory;
    private String previousDirectory;

    @BeforeEach
    void setUp() {
        previousDirectory = System.getProperty("lifesync.data.dir");
        System.setProperty("lifesync.data.dir", directory.toString());
        AuthService.logout();
        assertTrue(AuthService.signUp("alice", "alice@example.com", "old-password", "Alice", "Example"));
    }

    @AfterEach
    void tearDown() {
        AuthService.logout();
        if (previousDirectory == null) System.clearProperty("lifesync.data.dir");
        else System.setProperty("lifesync.data.dir", previousDirectory);
    }

    @Test
    void legacyAccountCanLoginButCannotResetWithoutEnrolling() {
        assertTrue(AuthService.login("alice", "old-password"));
        assertNull(AuthService.getCurrentUser().getRecoveryCodeHash());
        assertFalse(AuthService.resetPassword("alice", "anything", "new-password"));
    }

    @Test
    void issuingCodeRequiresPasswordAndDoesNotLogIn() {
        assertNull(AuthService.issueRecoveryCode("alice", "incorrect"));
        assertNull(AuthService.issueRecoveryCode("unknown", "old-password"));
        assertNotNull(AuthService.issueRecoveryCode("alice", "old-password"));
        assertNull(AuthService.getCurrentUser());
    }

    @Test
    void onlyHashesArePersistedAndCodeSurvivesReload() throws Exception {
        String code = AuthService.issueRecoveryCode("alice", "old-password");
        String stored = Files.readString(directory.resolve("users.json"));
        assertFalse(stored.contains(code));
        assertFalse(stored.contains("old-password"));
        List<User> users = DataStore.loadAll("users.json", new TypeToken<List<User>>() { }.getType());
        assertEquals(AuthService.hashPassword(code), users.getFirst().getRecoveryCodeHash());
        assertTrue(AuthService.resetPassword("alice", code, "new-password"));
        assertFalse(AuthService.login("alice", "old-password"));
        assertTrue(AuthService.login("alice", "new-password"));
    }

    @Test
    void resetAcceptsTrimmedCaseInsensitiveEmailAndCode() {
        String code = AuthService.issueRecoveryCode(" ALICE@EXAMPLE.COM ", "old-password");
        assertTrue(AuthService.resetPassword(" ALICE@EXAMPLE.COM ", " " + code + " ", "new-password"));
        assertTrue(AuthService.login("alice@example.com", "new-password"));
    }

    @Test
    void incorrectCodeDoesNotChangePasswordOrConsumeValidCode() {
        String code = AuthService.issueRecoveryCode("alice", "old-password");
        assertFalse(AuthService.resetPassword("alice", "incorrect", "new-password"));
        assertTrue(AuthService.login("alice", "old-password"));
        assertTrue(AuthService.resetPassword("alice", code, "new-password"));
    }

    @Test
    void codeCannotBeReusedAndSuccessfulResetClearsActiveSession() {
        String code = AuthService.issueRecoveryCode("alice", "old-password");
        AuthService.login("alice", "old-password");
        assertTrue(AuthService.resetPassword("alice", code, "new-password"));
        assertNull(AuthService.getCurrentUser());
        assertFalse(AuthService.resetPassword("alice", code, "another-password"));
        assertTrue(AuthService.login("alice", "new-password"));
    }

    @Test
    void replacingCodeInvalidatesPreviousCodeAndUpdatesCurrentProfile() {
        String old = AuthService.issueRecoveryCode("alice", "old-password");
        AuthService.login("alice", "old-password");
        String replacement = AuthService.issueRecoveryCode("alice", "old-password");
        assertNotEquals(old, replacement);
        assertEquals(AuthService.hashPassword(replacement), AuthService.getCurrentUser().getRecoveryCodeHash());
        // A subsequent profile save must preserve the newly issued hash.
        UserService.saveUser(AuthService.getCurrentUser());
        assertFalse(AuthService.resetPassword("alice", old, "new-password"));
        assertTrue(AuthService.resetPassword("alice", replacement, "new-password"));
    }

    @Test
    void codeCannotResetAnotherAccount() {
        String code = AuthService.issueRecoveryCode("alice", "old-password");
        AuthService.signUp("bob", "bob@example.com", "bob-password", "Bob", "Example");
        assertFalse(AuthService.resetPassword("bob", code, "new-password"));
        assertTrue(AuthService.login("bob", "bob-password"));
    }

    @Test
    void invalidPasswordDoesNotConsumeRecoveryCode() {
        String code = AuthService.issueRecoveryCode("alice", "old-password");
        assertThrows(IllegalArgumentException.class, () -> AuthService.resetPassword("alice", code, "short"));
        assertThrows(IllegalArgumentException.class, () -> AuthService.resetPassword("alice", code, "      "));
        assertTrue(AuthService.resetPassword("alice", code, "new-password"));
    }
}
