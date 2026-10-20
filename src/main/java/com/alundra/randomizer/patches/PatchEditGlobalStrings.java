package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;
import com.alundra.randomizer.tools.PsxExeFile;
import com.alundra.randomizer.tools.RandomizerException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alundra.randomizer.game.ItemCodes.ITEM_COUNT;

/**
 * Edits the global strings file (ETC_USA.R): chapter names become "Randomizer", and item names are updated to match
 * the randomizer's items (such as progressive "upgrade" items). The file is rebuilt, so its size can change: the
 * size known by the executable is updated accordingly.
 */
public class PatchEditGlobalStrings extends GamePatch {
    private int newFileSize = 0;

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        BinaryFile globalStringsFile = files.file(GameFiles.GLOBAL_STRINGS);

        List<String> strings = parseStringsFromFile(globalStringsFile);

        // Edit chapter names
        for (int i = 0; i < 42; ++i)
            strings.set(i, "Randomizer");

        // Edit item names
        for (int itemId = 0; itemId < ITEM_COUNT; ++itemId) {
            String name = gameData.item(itemId).name();
            if (name.startsWith("Item "))
                continue;
            strings.set(itemId + 512, name);
        }

        ByteArray newContents = writeStrings(strings);
        files.replace(GameFiles.GLOBAL_STRINGS, newContents.toByteArray());
        newFileSize = newContents.size();
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        if (newFileSize > 0xFFFF)
            throw new RandomizerException("Global strings file is too big for its size to be expressed as a word");

        int roundedSize = newFileSize;
        if (roundedSize % 0x800 > 0)
            roundedSize = (roundedSize / 0x800) * 0x800 + 0x800;

        exe.setWordLE(0xD040, roundedSize);
    }

    private static List<String> parseStringsFromFile(BinaryFile file) {
        List<String> strings = new ArrayList<>();
        for (int addr = 0; addr < 0x800; addr += 2) {
            int offset = file.getWordLE(addr);
            strings.add(offset != 0xFFFF ? file.getCString(offset) : "");
        }
        return strings;
    }

    private static ByteArray writeStrings(List<String> strings) {
        // Build a dictionary to re-use offsets for identical strings
        Map<String, Integer> stringDictionary = new HashMap<>();

        ByteArray stringBytes = new ByteArray();
        ByteArray offsetBytes = new ByteArray();

        // Add an empty string at the beginning, and store its offset inside the dictionary
        stringBytes.addByte(0x00);
        stringDictionary.put("", 0x800);

        for (String str : strings) {
            Integer existingOffset = stringDictionary.get(str);
            if (existingOffset != null) {
                offsetBytes.addWordLE(existingOffset);
            } else {
                int offset = (0x800 + stringBytes.size()) & 0xFFFF;
                offsetBytes.addWordLE(offset);
                stringDictionary.put(str, offset);
                stringBytes.addBytes(str.getBytes(StandardCharsets.ISO_8859_1));
                stringBytes.addByte(0x00);
            }
        }

        ByteArray file = new ByteArray();
        file.addBytes(offsetBytes);
        file.addBytes(stringBytes);
        return file;
    }
}
