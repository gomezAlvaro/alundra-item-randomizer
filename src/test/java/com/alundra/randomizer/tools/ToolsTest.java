package com.alundra.randomizer.tools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alundra.randomizer.tools.MipsRegister.*;
import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {
    @Test
    void mersenneTwisterMatchesStdMt19937() {
        // Reference values of std::mt19937 with its default seed (5489)
        MersenneTwister mt = new MersenneTwister(5489);
        assertEquals(3499211612L, mt.next());
        assertEquals(581869302L, mt.next());
        assertEquals(3890346734L, mt.next());

        // The C++ standard requires the 10000th output of a default-constructed mt19937 to be 4123659995
        MersenneTwister mt2 = new MersenneTwister(5489);
        long value = 0;
        for (int i = 0; i < 10000; ++i)
            value = mt2.next();
        assertEquals(4123659995L, value);
    }

    @Test
    void shuffleIsDeterministicAndKeepsElements() {
        List<Integer> a = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<Integer> b = new ArrayList<>(a);
        VecTools.shuffle(a, new MersenneTwister(1));
        VecTools.shuffle(b, new MersenneTwister(1));
        assertEquals(a, b);
        assertEquals(55, a.stream().mapToInt(Integer::intValue).sum());
        assertNotEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), a);
    }

    @Test
    void byteArrayEndianness() {
        ByteArray array = new ByteArray();
        array.addLong(0x11223344L);
        array.addLongLE(0x11223344L);
        assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44, 0x44, 0x33, 0x22, 0x11}, array.toByteArray());
        assertEquals(0x11223344L, array.getLong(0));
        assertEquals(0x11223344L, array.getLongLE(4));
        assertEquals(0x2233, array.getWordLE(5));
        assertThrows(IndexOutOfBoundsException.class, () -> array.setByte(8, 0));
    }

    @Test
    void mipsInstructionEncoding() {
        MipsCode code = new MipsCode()
                .lui(AT, 0x801E)            // 3C01801E
                .sh(T0, AT, 0xD578)         // A428D578
                .jr(RA, false)              // 03E00008
                .addiu(V0, ZERO, 5);        // 24020005
        assertArrayEquals(new byte[]{
                0x1E, (byte) 0x80, 0x01, 0x3C,
                0x78, (byte) 0xD5, 0x28, (byte) 0xA4,
                0x08, 0x00, (byte) 0xE0, 0x03,
                0x05, 0x00, 0x02, 0x24}, code.toByteArray());
    }

    @Test
    void mipsSetWithNegativeLowerHalfCompensatesUpperHalf() {
        // 0x800FEFB4 = (0x8010 << 16) + (int16) 0xEFB4
        MipsCode code = new MipsCode().set_(T5, 0x800FEFB4L);
        ByteArray expected = new ByteArray();
        expected.addLongLE(0x3C0D8010L); // lui t5, 0x8010
        expected.addLongLE(0x25ADEFB4L); // addiu t5, t5, 0xEFB4
        assertArrayEquals(expected.toByteArray(), code.toByteArray());
    }

    @Test
    void mipsBranchesAreResolved() {
        MipsCode code = new MipsCode();
        code.label_("start");
        code.nop();
        code.bra_("end");        // forward branch (bgez $zero), at 0x04, delay slot nop at 0x08
        code.bra_("start");      // backward branch at 0x0C, delay slot nop at 0x10
        code.label_("end");      // 0x14
        byte[] bytes = code.toByteArray();
        // Forward: (0x14 - (0x04 + 4)) / 4 = 3
        assertEquals(3, (bytes[4] & 0xFF) | ((bytes[5] & 0xFF) << 8));
        // Backward: (0x00 - (0x0C + 4)) / 4 = -4
        assertEquals(0xFFFC, (bytes[12] & 0xFF) | ((bytes[13] & 0xFF) << 8));

        MipsCode unresolved = new MipsCode().bra_("nowhere");
        assertThrows(RandomizerException.class, unresolved::toByteArray);
    }

    @Test
    void jsonParserHandlesCommentsOrderAndEscapes() {
        JsonNode node = Json.parse("""
                {
                    // a comment
                    "zeta": 1, /* another */ "alpha": [true, false, null, -2.5e1,],
                    "text": "quote \\" slash \\\\ unicode \\u00e9",
                }""");
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : Json.fields(node))
            keys.add(entry.getKey());
        assertEquals(List.of("zeta", "alpha", "text"), keys);
        assertEquals(1, node.get("zeta").asInt());
        assertTrue(node.get("alpha").get(0).asBoolean());
        assertTrue(node.get("alpha").get(2).isNull());
        assertEquals("quote \" slash \\ unicode é", node.get("text").asText());
        assertThrows(RandomizerException.class, () -> Json.parse("{\"a\": }"));
    }

    @Test
    void jsonDumpLayout() {
        Map<String, Object> json = Json.object();
        json.put("b", 1);
        json.put("a", List.of("x", true));
        json.put("empty", Json.object());
        json.put("list", List.of());
        assertEquals("""
                {
                    "b": 1,
                    "a": [
                        "x",
                        true
                    ],
                    "empty": {},
                    "list": []
                }""", Json.dump(json));
    }

    @Test
    void bitstreamRoundTrip() {
        BitstreamWriter writer = new BitstreamWriter();
        writer.packSmall(12);
        writer.packUInt32(0xDEADBEEFL);
        writer.pack(true);
        writer.packSmall(9999);
        writer.packSmallVector(List.of(0, 1, 300));
        BitstreamReader reader = new BitstreamReader(writer.bytes());
        assertEquals(12, reader.unpackUInt8());
        assertEquals(0xDEADBEEFL, reader.unpackUInt32());
        assertTrue(reader.unpackBool());
        assertEquals(9999, reader.unpackUInt16());
        assertEquals(List.of(0, 1, 300), reader.unpackUInt16Vector());
    }

    @Test
    void textboxFormatterWrapsLines() {
        String formatted = TextboxFormatter.format("\\CThis is a rather long sentence that will need to be wrapped "
                + "over several lines because it does not fit in a single textbox line at all.");
        assertTrue(formatted.startsWith("\\CThis is a rather long sentence"));
        assertTrue(formatted.contains("\\N"));
        assertFalse(formatted.contains("  "));
        // Escape sequences with arguments are kept intact
        assertTrue(TextboxFormatter.format("\\CPrice? \\0100\\Y").endsWith("\\0100\\Y"));
        assertTrue(TextboxFormatter.format("Wait\\W2now").contains("\\W2"));
    }

    @Test
    void replaceFirstFailsLoudlyOnMissingText() {
        assertEquals("a Sword!", StringTools.replaceFirst("a Dagger!", "Dagger", "Sword"));
        assertThrows(RandomizerException.class, () -> StringTools.replaceFirst("abc", "z", "y"));
    }
}
