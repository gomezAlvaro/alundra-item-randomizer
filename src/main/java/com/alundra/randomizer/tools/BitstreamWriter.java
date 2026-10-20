package com.alundra.randomizer.tools;

import java.util.Collection;
import java.util.Map;

/**
 * Writes a compact stream of bits, used to build permalinks. Small integers (8 and 16 bits) are stored as
 * variable-length numbers (Elias-gamma like), 32-bit integers are stored raw in little-endian byte order.
 */
public class BitstreamWriter {
    private final ByteArray bytes = new ByteArray();
    private int bitPosition = 8;

    public byte[] bytes() {
        return bytes.toByteArray();
    }

    public void addBit(boolean bit) {
        if (bitPosition == 8) {
            bytes.addByte(0);
            bitPosition = 0;
        }

        int lastIndex = bytes.size() - 1;
        int lastByte = bytes.getByte(lastIndex);
        if (bit)
            lastByte |= (1 << (7 - bitPosition));
        bytes.setByte(lastIndex, lastByte);
        bitPosition += 1;
    }

    public void addNumber(long value, int numBits) {
        for (int bit = numBits - 1; bit >= 0; --bit)
            addBit((value & (1L << bit)) != 0);
    }

    public void addVariableLengthNumber(long number) {
        int exponent = 0;
        long numberCopy = number + 1;
        while (numberCopy > 1) {
            exponent += 1;
            numberCopy >>= 1;
        }
        long mantissa = number + 1 - (1L << exponent);

        for (int i = 0; i < exponent; ++i)
            addBit(false);
        addBit(true);
        addNumber(mantissa, exponent);
    }

    /** Packs an 8-bit or 16-bit unsigned value as a variable-length number. */
    public void packSmall(int value) {
        addVariableLengthNumber(value);
    }

    /** Packs a 32-bit unsigned value as raw little-endian bytes. */
    public void packUInt32(long value) {
        for (int i = 0; i < 4; ++i) {
            int b = (int) ((value >> (8 * i)) & 0xFF);
            for (int j = 0; j < 8; ++j)
                addBit((b & (1 << (7 - j))) != 0);
        }
    }

    public void pack(boolean value) {
        addBit(value);
    }

    public void packSmallArray(int[] array) {
        for (int value : array)
            packSmall(value);
    }

    public void packSmallVector(Collection<Integer> values) {
        packSmall(values.size());
        for (int value : values)
            packSmall(value);
    }

    public void packSmallMap(Map<Integer, Integer> map) {
        packSmall(map.size());
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            packSmall(entry.getKey());
            packSmall(entry.getValue());
        }
    }
}
