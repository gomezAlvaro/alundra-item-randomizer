package com.alundra.randomizer.game;

import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.ByteArray;
import com.alundra.randomizer.tools.RandomizerException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The text strings attached to a room (map) inside DATAS.BIN.
 * <p>
 * Strings are stored at the end of each room data block ("sector 7"), as a table of 16-bit offsets terminated by
 * 0xFFFF followed by null-terminated strings. Edited strings are written back in the same space, which means the
 * total size cannot exceed the original one.
 */
public class RoomStrings {
    private final long initialStringsPos;
    private final int initialStringsSize;
    private final List<String> strings = new ArrayList<>();

    public RoomStrings(int mapId, BinaryFile datasFile) {
        long roomOffsetAddr = 0x28 + (mapId * 4L);
        long roomAddr = datasFile.getLongLE(roomOffsetAddr);
        long nextRoomAddr = datasFile.getLongLE(roomOffsetAddr + 4);
        if (nextRoomAddr == 0)
            nextRoomAddr = datasFile.size();
        long roomBytesSize = nextRoomAddr - roomAddr;

        long sector7Offset = datasFile.getLongLE(roomAddr + 0x18);
        this.initialStringsSize = (int) (roomBytesSize - sector7Offset);
        this.initialStringsPos = roomAddr + sector7Offset;
        parseStrings(datasFile, initialStringsPos);
    }

    private void parseStrings(BinaryFile datasFile, long startingOffset) {
        for (long addr = startingOffset; ; addr += 2) {
            int stringOffset = datasFile.getWordLE(addr);
            if (stringOffset == 0xFFFF)
                break;
            strings.add(datasFile.getCString(startingOffset + stringOffset));
        }
    }

    public String string(int stringId) {
        return strings.get(stringId);
    }

    public void string(int stringId, String value) {
        strings.set(stringId, value);
    }

    public int count() {
        return strings.size();
    }

    public void applyOnData(BinaryFile datasFile) {
        ByteArray stringBytes = new ByteArray(initialStringsSize);
        int offsetsTableSize = (strings.size() + 1) * 2;
        stringBytes.resize(offsetsTableSize, 0xFF);

        for (int i = 0; i < strings.size(); ++i) {
            stringBytes.setWordLE(i * 2L, stringBytes.size() & 0xFFFF);
            stringBytes.addBytes(strings.get(i).getBytes(StandardCharsets.ISO_8859_1));
            stringBytes.addByte(0x00);
        }

        if (stringBytes.size() > initialStringsSize)
            throw new RandomizerException("String data is bigger than in original room");

        stringBytes.resize(initialStringsSize, 0x00);
        datasFile.setBytes(initialStringsPos, stringBytes);
    }
}
