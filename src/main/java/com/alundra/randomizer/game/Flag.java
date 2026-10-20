package com.alundra.randomizer.game;

/**
 * A story flag, represented by a bit inside the game's flag area in RAM.
 *
 * @param offset the address of the byte containing the flag (lower 16 bits of RAM address 0x801Dxxxx)
 * @param bit    the bit index inside that byte
 */
public record Flag(int offset, int bit) {
    /** @return the flag ID as used by event scripts */
    public int eventCode() {
        return (((offset - 0xD334) << 3) + bit) & 0xFFFF;
    }
}
