package com.alundra.randomizer.io;

import com.alundra.randomizer.model.HintSource;
import com.alundra.randomizer.model.ItemSource;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.model.WorldNode;
import com.alundra.randomizer.model.WorldRegion;
import com.alundra.randomizer.tools.Json;

import java.util.Map;

/**
 * Builds the spoiler log sections describing the hints and the contents of every item source.
 */
public final class SpoilerWriter {
    private SpoilerWriter() {
    }

    public static Map<String, Object> buildSpoilerJson(RandomizerWorld world) {
        Map<String, Object> json = Json.object();

        // Export hints
        for (HintSource hintSource : world.hintSources().values())
            Json.child(json, "hints").put(hintSource.name(), hintSource.text());

        // Export item sources
        for (WorldRegion region : world.regions()) {
            for (WorldNode node : region.nodes()) {
                for (ItemSource source : node.itemSources()) {
                    Map<String, Object> regionJson = Json.child(Json.child(json, "itemSources"), region.name());
                    regionJson.put(source.prettyName(), source.item().name());
                }
            }
        }

        return json;
    }
}
