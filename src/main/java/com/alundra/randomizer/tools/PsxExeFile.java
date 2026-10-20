package com.alundra.randomizer.tools;

/**
 * A PS-X EXE file (main game executable) which supports injecting new code before its original code start.
 * <p>
 * Injected code is prepended to the program text: the file is expanded by a multiple of 0x800 bytes right after its
 * header, and the header is updated so the executable is loaded that many bytes earlier in RAM. This gives an empty
 * RAM area for the randomizer to put its own functions without overwriting anything.
 */
public class PsxExeFile extends BinaryFile {
    private static final int HEADER_SIZE = 0x800;
    private static final int FILE_SIZE_INCREMENT = 0x800;

    private final long initialCodeStartAddr;
    private final ByteArray prependedBytes = new ByteArray();
    private boolean headerUpdated = false;

    public PsxExeFile(String name, byte[] bytes) {
        super(name, bytes);
        this.initialCodeStartAddr = getLongLE(0x18);
        // Add a full empty increment to let an open RAM space for the game to edit
        prependedBytes.resize(FILE_SIZE_INCREMENT, 0x00);
    }

    /**
     * Injects a piece of code inside the open RAM area.
     *
     * @return the RAM address where the code will be located when the game runs
     */
    public long injectCode(MipsCode code) {
        prependedBytes.insertBytes(0, code.toByteArray());
        return initialCodeStartAddr - prependedBytes.size();
    }

    /** Overwrites existing code at the given file offset. */
    public void setCode(long address, MipsCode code) {
        setBytes(address, code.toByteArray());
    }

    /**
     * Physically prepends the injected code to the file and updates the PSX-EXE header accordingly.
     * This is called automatically by {@link #contents()}, and only does something the first time.
     */
    public void updatePsxHeader() {
        if (headerUpdated)
            return;
        headerUpdated = true;

        int expansionSize = 0;
        while (expansionSize < prependedBytes.size())
            expansionSize += FILE_SIZE_INCREMENT;

        // Prepend padding bytes to ensure the size expansion is a multiple of 0x800
        int paddingSize = expansionSize - prependedBytes.size();
        byte[] padding = new byte[paddingSize];
        java.util.Arrays.fill(padding, (byte) 0xFF);
        prependedBytes.insertBytes(0, padding);

        // Actually insert the bytes inside the data
        insertBytes(HEADER_SIZE, prependedBytes.toByteArray());

        if (expansionSize > 0) {
            // Shift the address where code is loaded
            setLongLE(0x18, initialCodeStartAddr - expansionSize);
            // Update the amount of bytes to load
            setLongLE(0x1C, size() - HEADER_SIZE);
        }
    }

    @Override
    public byte[] contents() {
        updatePsxHeader();
        return super.contents();
    }
}
