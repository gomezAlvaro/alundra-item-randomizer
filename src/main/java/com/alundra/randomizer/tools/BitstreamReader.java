package com.alundra.randomizer.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reads a stream of bits written by {@link BitstreamWriter}.
 */
public class BitstreamReader {
    private final byte[] data;
    private int byteIndex = 0;
    private int bitPosition = 7;

    public BitstreamReader(byte[] data) {
        this.data = data;
    }

    public boolean nextBit() {
        if (bitPosition < 0) {
            bitPosition = 7;
            byteIndex++;
        }
        if (byteIndex >= data.length)
            throw new RandomizerException("This permalink is malformed (unexpected end of data).");

        boolean bit = ((data[byteIndex] >> bitPosition) & 0x01) != 0;
        bitPosition -= 1;
        return bit;
    }

    public long readBits(int amount) {
        if (amount > 32)
            throw new RandomizerException("Cannot read more than 32 bits using Bitstream");

        long value = 0;
        for (int i = 0; i < amount; ++i) {
            value <<= 1;
            value |= nextBit() ? 1 : 0;
        }
        return value;
    }

    public long readVariableLengthNumber() {
        int exponent = 0;
        while (!nextBit()) {
            exponent += 1;
            if (exponent > 32)
                throw new RandomizerException("This permalink is malformed (invalid number).");
        }

        long mantissa = readBits(exponent);
        return (1L << exponent) + mantissa - 1;
    }

    public int unpackUInt8() {
        return (int) (readVariableLengthNumber() & 0xFF);
    }

    public int unpackUInt16() {
        return (int) (readVariableLengthNumber() & 0xFFFF);
    }

    public long unpackUInt32() {
        long value = 0;
        for (int i = 0; i < 4; ++i) {
            int b = 0;
            for (int j = 0; j < 8; ++j)
                if (nextBit())
                    b |= 1 << (7 - j);
            value |= ((long) b) << (8 * i);
        }
        return value;
    }

    public boolean unpackBool() {
        return nextBit();
    }

    public int[] unpackUInt8Array(int count) {
        int[] values = new int[count];
        for (int i = 0; i < count; ++i)
            values[i] = unpackUInt8();
        return values;
    }

    public List<Integer> unpackUInt8Vector() {
        int count = unpackUInt16();
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; ++i)
            values.add(unpackUInt8());
        return values;
    }

    public List<Integer> unpackUInt16Vector() {
        int count = unpackUInt16();
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; ++i)
            values.add(unpackUInt16());
        return values;
    }

    public Map<Integer, Integer> unpackUInt16ToUInt8Map() {
        Map<Integer, Integer> map = new TreeMap<>();
        int count = unpackUInt16();
        for (int i = 0; i < count; ++i) {
            int key = unpackUInt16();
            int value = unpackUInt8();
            map.put(key, value);
        }
        return map;
    }
}
