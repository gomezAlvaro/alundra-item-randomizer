package com.alundra.randomizer.tools;

import java.util.Arrays;

/**
 * A growable array of unsigned bytes with helpers to read and write big-endian and little-endian words (16 bits)
 * and longs (32 bits). All getters return unsigned values widened to {@code int} (or {@code long} for 32-bit reads
 * where the sign matters).
 */
public class ByteArray {
    protected byte[] data;
    protected int size;

    public ByteArray() {
        this(16);
    }

    public ByteArray(int initialCapacity) {
        this.data = new byte[Math.max(initialCapacity, 1)];
        this.size = 0;
    }

    public ByteArray(byte[] bytes) {
        this.data = bytes.clone();
        this.size = bytes.length;
    }

    /** Wraps the given array without copying it. */
    public static ByteArray wrap(byte[] bytes) {
        ByteArray array = new ByteArray(1);
        array.data = bytes;
        array.size = bytes.length;
        return array;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public void clear() {
        size = 0;
    }

    /** Resizes the array, filling new space with the given byte value. */
    public void resize(int newSize, int fillValue) {
        ensureCapacity(newSize);
        if (newSize > size)
            Arrays.fill(data, size, newSize, (byte) fillValue);
        size = newSize;
    }

    public void resize(int newSize) {
        resize(newSize, 0);
    }

    private void ensureCapacity(int capacity) {
        if (capacity > data.length) {
            int newCapacity = Math.max(capacity, data.length * 2);
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    /** @return a copy of the meaningful bytes */
    public byte[] toByteArray() {
        return Arrays.copyOf(data, size);
    }

    /** @return the internal buffer (may be bigger than {@link #size()}) */
    public byte[] buffer() {
        return data;
    }

    private void checkRange(long address, int length) {
        if (address < 0 || address + length > size)
            throw new IndexOutOfBoundsException("Offset 0x" + Long.toHexString(address) + " is out of range from ByteArray (size 0x"
                    + Integer.toHexString(size) + ")");
    }

    // -------------------- Reading functions --------------------

    public int getByte(long address) {
        checkRange(address, 1);
        return data[(int) address] & 0xFF;
    }

    public int getWord(long address) {
        return (getByte(address) << 8) | getByte(address + 1);
    }

    public long getLong(long address) {
        return ((long) getWord(address) << 16) | getWord(address + 2);
    }

    public int getWordLE(long address) {
        return getByte(address) | (getByte(address + 1) << 8);
    }

    public long getLongLE(long address) {
        return (long) getWordLE(address) | ((long) getWordLE(address + 2) << 16);
    }

    /** @return a copy of bytes in range [begin, end[ */
    public ByteArray getBytes(long begin, long end) {
        checkRange(begin, (int) (end - begin));
        return new ByteArray(Arrays.copyOfRange(data, (int) begin, (int) end));
    }

    /** Reads a null-terminated string starting at the given address (bytes are interpreted as Latin-1). */
    public String getCString(long address) {
        int start = (int) address;
        int end = start;
        while (end < size && data[end] != 0)
            end++;
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // -------------------- Add functions --------------------

    public void addByte(int value) {
        ensureCapacity(size + 1);
        data[size++] = (byte) value;
    }

    public void addWord(int word) {
        addByte((word >> 8) & 0xFF);
        addByte(word & 0xFF);
    }

    public void addLong(long longWord) {
        addWord((int) ((longWord >> 16) & 0xFFFF));
        addWord((int) (longWord & 0xFFFF));
    }

    public void addWordLE(int word) {
        addByte(word & 0xFF);
        addByte((word >> 8) & 0xFF);
    }

    public void addLongLE(long longWord) {
        addWordLE((int) (longWord & 0xFFFF));
        addWordLE((int) ((longWord >> 16) & 0xFFFF));
    }

    public void addBytes(int... bytes) {
        for (int b : bytes)
            addByte(b);
    }

    public void addBytes(byte[] bytes) {
        addBytes(bytes, 0, bytes.length);
    }

    public void addBytes(byte[] bytes, int offset, int length) {
        ensureCapacity(size + length);
        System.arraycopy(bytes, offset, data, size, length);
        size += length;
    }

    public void addBytes(ByteArray bytes) {
        addBytes(bytes.data, 0, bytes.size);
    }

    /** Inserts the given bytes at the given position, shifting everything after it. */
    public void insertBytes(int position, byte[] bytes) {
        if (position < 0 || position > size)
            throw new IndexOutOfBoundsException("Cannot insert at position " + position);
        ensureCapacity(size + bytes.length);
        System.arraycopy(data, position, data, position + bytes.length, size - position);
        System.arraycopy(bytes, 0, data, position, bytes.length);
        size += bytes.length;
    }

    // -------------------- Set functions --------------------

    public void setByte(long address, int value) {
        checkRange(address, 1);
        data[(int) address] = (byte) value;
    }

    public void setWord(long address, int word) {
        setByte(address, (word >> 8) & 0xFF);
        setByte(address + 1, word & 0xFF);
    }

    public void setLong(long address, long longWord) {
        setWord(address, (int) ((longWord >> 16) & 0xFFFF));
        setWord(address + 2, (int) (longWord & 0xFFFF));
    }

    public void setWordLE(long address, int word) {
        setByte(address, word & 0xFF);
        setByte(address + 1, (word >> 8) & 0xFF);
    }

    public void setLongLE(long address, long longWord) {
        setWordLE(address, (int) (longWord & 0xFFFF));
        setWordLE(address + 2, (int) ((longWord >> 16) & 0xFFFF));
    }

    public void setBytes(long address, int... bytes) {
        for (int i = 0; i < bytes.length; ++i)
            setByte(address + i, bytes[i]);
    }

    public void setBytes(long address, byte[] bytes) {
        checkRange(address, bytes.length);
        System.arraycopy(bytes, 0, data, (int) address, bytes.length);
    }

    public void setBytes(long address, ByteArray bytes) {
        checkRange(address, bytes.size);
        System.arraycopy(bytes.data, 0, data, (int) address, bytes.size);
    }
}
