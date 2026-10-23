package com.lifesync.lifesync;

import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Guards against replacing transparent mascots with opaque rectangular artwork. */
class MascotAssetTest {
    @Test
    void mascotAssetsHaveTransparentSurroundingsAndVisibleArtwork() throws Exception {
        for (String name : new String[]{"login", "activity", "pantry", "meals", "plan", "chatbot", "home"}) {
            try (var stream = getClass().getResourceAsStream("images/" + name + ".png")) {
                assertNotNull(stream, name);
                Image image = new Image(stream);
                assertFalse(image.isError(), name);
                int width = (int) image.getWidth();
                int height = (int) image.getHeight();
                int transparent = 0;
                int opaque = 0;
                int left = width;
                int right = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int alpha = image.getPixelReader().getArgb(x, y) >>> 24;
                        if (alpha == 0) transparent++;
                        if (alpha == 255) opaque++;
                        if (alpha != 0) { left = Math.min(left, x); right = Math.max(right, x); }
                    }
                }
                assertTrue(transparent > width * height / 5, name + " needs transparent surroundings");
                assertTrue(opaque > width * height / 5, name + " needs visible artwork");
                assertEquals(0, image.getPixelReader().getArgb(0, 0) >>> 24, name);
                assertEquals(0, image.getPixelReader().getArgb(width - 1, height - 1) >>> 24, name);
                if (name.equals("login")) {
                    assertTrue(left >= 3, "Login mascot must not retain the stray left border");
                    assertTrue(Math.abs(left - (width - 1 - right)) <= 2,
                            "Login mascot needs balanced transparent margins");
                }
            }
        }
    }
}
