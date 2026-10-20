package com.alundra.randomizer.logic;

import com.alundra.randomizer.Randomizer;
import com.alundra.randomizer.tools.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Checks that seeds generated from permalinks produced by the original C++ randomizer (v1.2.0) give exactly the same
 * world: item placements, hints, playthrough and settings. The reference spoiler logs were produced by the C++ build
 * (with its "itemsDistributions" key renamed to "itemsDistribution").
 */
class ReferenceParityTest {
    private static String resource(String path) throws IOException {
        try (InputStream stream = ReferenceParityTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "missing test resource " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void checkReference(String resourcePath) throws IOException {
        String expected = resource(resourcePath);
        String permalink = Json.parse(expected).get("permalink").asText();

        Randomizer randomizer = Randomizer.fromPermalink(permalink).output(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        randomizer.randomize();

        assertEquals(expected, Json.dump(randomizer.spoilerJson()));
    }

    @Test
    void defaultPresetMatchesOriginalRandomizer() throws IOException {
        checkReference("/golden/cpp_reference_spoiler.json");
    }

    @Test
    void nonProgressiveThreeCrestsPresetMatchesOriginalRandomizer() throws IOException {
        checkReference("/golden/cpp_reference_spoiler_v1.json");
    }
}
