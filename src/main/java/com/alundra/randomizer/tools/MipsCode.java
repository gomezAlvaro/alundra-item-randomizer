package com.alundra.randomizer.tools;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static com.alundra.randomizer.tools.MipsRegister.*;

/**
 * A tiny MIPS assembler producing little-endian R3000 machine code, used to write patches for the PS1 executable.
 * <p>
 * Instruction methods return {@code this} so code can be chained. Methods with a trailing underscore are
 * pseudo-instructions expanding to one or several real instructions (they may use {@code T9} as a scratch register).
 * Branches take a label name which can be defined before or after the branch with {@link #label_(String)}.
 */
public class MipsCode extends ByteArray {
    /** key = address of branch instruction, value = label name */
    private final Map<Integer, String> pendingBranches = new TreeMap<>();
    private final Map<String, Integer> labels = new HashMap<>();

    public MipsCode() {
        super(64);
    }

    private MipsCode op(long opcode) {
        addLongLE(opcode & 0xFFFFFFFFL);
        return this;
    }

    private static long imm(long value) {
        return value & 0xFFFF;
    }

    public MipsCode nop() {
        addLong(0x00000000);
        return this;
    }

    // ----- ARITHMETIC LOGIC UNIT -----

    public MipsCode add(MipsRegister regTo, MipsRegister regFrom1, MipsRegister regFrom2) {
        // 0000 00ss ssst tttt dddd d000 0010 0000
        return op(0x00000020L | (regFrom1.code() << 21) | (regFrom2.code() << 16) | (regTo.code() << 11));
    }

    public MipsCode addu(MipsRegister regTo, MipsRegister regFrom1, MipsRegister regFrom2) {
        // 0000 00ss ssst tttt dddd d000 0010 0001
        return op(0x00000021L | (regFrom1.code() << 21) | (regFrom2.code() << 16) | (regTo.code() << 11));
    }

    public MipsCode addiu(MipsRegister regTo, MipsRegister regFrom, long value) {
        // 0010 01ss ssst tttt iiii iiii iiii iiii
        return op(0x24000000L | (regFrom.code() << 21) | (regTo.code() << 16) | imm(value));
    }

    public MipsCode addiu(MipsRegister reg, long value) {
        return addiu(reg, reg, value);
    }

    public MipsCode logicalAnd(MipsRegister regTo, MipsRegister regFrom1, MipsRegister regFrom2) {
        // 0000 00ss ssst tttt dddd d000 0010 0100
        return op(0x00000024L | (regFrom1.code() << 21) | (regFrom2.code() << 16) | (regTo.code() << 11));
    }

    public MipsCode lui(MipsRegister reg, long value) {
        // 0011 1100 000t tttt iiii iiii iiii iiii
        return op(0x3C000000L | (reg.code() << 16) | imm(value));
    }

    public MipsCode slt(MipsRegister regTo, MipsRegister regFrom1, MipsRegister regFrom2) {
        // 0000 00ss ssst tttt dddd d000 0010 1010
        return op(0x0000002AL | (regFrom1.code() << 21) | (regFrom2.code() << 16) | (regTo.code() << 11));
    }

    public MipsCode slti(MipsRegister regTo, MipsRegister regFrom, long value) {
        // 0010 10ss ssst tttt iiii iiii iiii iiii
        return op(0x28000000L | (regFrom.code() << 21) | (regTo.code() << 16) | imm(value));
    }

    // ----- MULTIPLY -----

    public MipsCode mult(MipsRegister reg1, MipsRegister reg2) {
        // 0000 00ss ssst tttt 0000 0000 0001 1000
        return op(0x00000018L | (reg1.code() << 21) | (reg2.code() << 16));
    }

    public MipsCode multu(MipsRegister reg1, MipsRegister reg2) {
        // 0000 00ss ssst tttt 0000 0000 0001 1001
        return op(0x00000019L | (reg1.code() << 21) | (reg2.code() << 16));
    }

    public MipsCode mflo(MipsRegister reg) {
        // 0000 0000 0000 0000 dddd d000 0001 0010
        return op(0x00000012L | (reg.code() << 11));
    }

    // ----- BRANCH -----

    private MipsCode branch(long opcode, String label) {
        pendingBranches.put(size(), label);
        op(opcode);
        resolveBranches();
        nop();
        return this;
    }

    public MipsCode beq(MipsRegister reg1, MipsRegister reg2, String label) {
        return branch(0x10000000L | (reg1.code() << 21) | (reg2.code() << 16), label);
    }

    public MipsCode bne(MipsRegister reg1, MipsRegister reg2, String label) {
        return branch(0x14000000L | (reg1.code() << 21) | (reg2.code() << 16), label);
    }

    public MipsCode bgez(MipsRegister reg, String label) {
        // 0000 01ss sss0 0001 ffff ffff ffff ffff
        return branch(0x04010000L | (reg.code() << 21), label);
    }

    public MipsCode j(long addr) {
        return j(addr, true);
    }

    public MipsCode j(long addr, boolean addLoadDelayNop) {
        // 0000 10ii iiii iiii iiii iiii iiii iiii
        op(0x08000000L | ((addr / 4) & 0x00FFFFFFL));
        if (addLoadDelayNop)
            nop();
        return this;
    }

    public MipsCode jal(long addr) {
        return jal(addr, true);
    }

    public MipsCode jal(long addr, boolean addLoadDelayNop) {
        // 0000 11ii iiii iiii iiii iiii iiii iiii
        op(0x0C000000L | ((addr / 4) & 0x00FFFFFFL));
        if (addLoadDelayNop)
            nop();
        return this;
    }

    public MipsCode jr(MipsRegister reg) {
        return jr(reg, true);
    }

    public MipsCode jr(MipsRegister reg, boolean addLoadDelayNop) {
        // 0000 00ss sss0 0000 0000 0000 0000 1000
        op(0x00000008L | (reg.code() << 21));
        if (addLoadDelayNop)
            nop();
        return this;
    }

    // ----- MEMORY ACCESS -----

    public MipsCode sw(MipsRegister regFrom, MipsRegister regTo) {
        return sw(regFrom, regTo, 0);
    }

    public MipsCode sw(MipsRegister regFrom, MipsRegister regTo, long offsetTo) {
        // 1010 11ss ssst tttt iiii iiii iiii iiii
        return op(0xAC000000L | (regTo.code() << 21) | (regFrom.code() << 16) | imm(offsetTo));
    }

    public MipsCode sh(MipsRegister regFrom, MipsRegister regTo) {
        return sh(regFrom, regTo, 0);
    }

    public MipsCode sh(MipsRegister regFrom, MipsRegister regTo, long offsetTo) {
        // 1010 01ss ssst tttt iiii iiii iiii iiii
        return op(0xA4000000L | (regTo.code() << 21) | (regFrom.code() << 16) | imm(offsetTo));
    }

    public MipsCode sb(MipsRegister regFrom, MipsRegister regTo, long offsetTo) {
        // 1010 00ss ssst tttt iiii iiii iiii iiii
        return op(0xA0000000L | (regTo.code() << 21) | (regFrom.code() << 16) | imm(offsetTo));
    }

    private MipsCode load(long opcode, MipsRegister regTo, MipsRegister regFrom, long offsetFrom, boolean addLoadDelayNop) {
        op(opcode | (regFrom.code() << 21) | (regTo.code() << 16) | imm(offsetFrom));
        if (addLoadDelayNop)
            nop();
        return this;
    }

    public MipsCode lw(MipsRegister regTo, MipsRegister regFrom) {
        return lw(regTo, regFrom, 0, true);
    }

    public MipsCode lw(MipsRegister regTo, MipsRegister regFrom, long offsetFrom, boolean addLoadDelayNop) {
        // 1000 11ss ssst tttt iiii iiii iiii iiii
        return load(0x8C000000L, regTo, regFrom, offsetFrom, addLoadDelayNop);
    }

    public MipsCode lh(MipsRegister regTo, MipsRegister regFrom) {
        return lh(regTo, regFrom, 0);
    }

    public MipsCode lh(MipsRegister regTo, MipsRegister regFrom, long offsetFrom) {
        return lh(regTo, regFrom, offsetFrom, true);
    }

    public MipsCode lh(MipsRegister regTo, MipsRegister regFrom, long offsetFrom, boolean addLoadDelayNop) {
        // 1000 01ss ssst tttt iiii iiii iiii iiii
        return load(0x84000000L, regTo, regFrom, offsetFrom, addLoadDelayNop);
    }

    public MipsCode lb(MipsRegister regTo, MipsRegister regFrom, long offsetFrom, boolean addLoadDelayNop) {
        // 1000 00ss ssst tttt iiii iiii iiii iiii
        return load(0x80000000L, regTo, regFrom, offsetFrom, addLoadDelayNop);
    }

    // ----- PSEUDO INSTRUCTIONS -----

    /** Sets a 32-bit immediate value inside a register (lui + addiu, or a single addiu if possible). */
    public MipsCode set_(MipsRegister regTo, long value) {
        int upperHalf = (int) ((value >> 16) & 0xFFFF);
        int lowerHalf = (int) (value & 0xFFFF);
        if ((lowerHalf & 0x8000) != 0)
            upperHalf = (upperHalf + 1) & 0xFFFF;

        if (upperHalf != 0) {
            lui(regTo, upperHalf);
            addiu(regTo, regTo, lowerHalf);
        } else {
            addiu(regTo, ZERO, lowerHalf);
        }
        return this;
    }

    /** Copies a register into another one. */
    public MipsCode set_(MipsRegister regTo, MipsRegister regFrom) {
        return add(regTo, regFrom, ZERO);
    }

    public MipsCode multiply_(MipsRegister regDest, MipsRegister regSrc, long value) {
        set_(T9, value & 0xFFFF);
        mult(regSrc, T9);
        mflo(regDest);
        return this;
    }

    public MipsCode multiply_(MipsRegister regDest, MipsRegister reg1, MipsRegister reg2) {
        mult(reg1, reg2);
        mflo(regDest);
        return this;
    }

    public MipsCode clr_(MipsRegister reg) {
        return logicalAnd(reg, reg, ZERO);
    }

    public MipsCode label_(String label) {
        labels.put(label, size());
        resolveBranches();
        return this;
    }

    /** Unconditional relative branch. */
    public MipsCode bra_(String label) {
        return bgez(ZERO, label);
    }

    public MipsCode beq_(MipsRegister reg1, long value, String label) {
        set_(T9, value);
        return beq(reg1, T9, label);
    }

    public MipsCode bne_(MipsRegister reg1, long value, String label) {
        set_(T9, value);
        return bne(reg1, T9, label);
    }

    public MipsCode ble_(MipsRegister reg1, MipsRegister reg2, String label) {
        // if(reg1 <= reg2) <-------> if(!(reg2 < reg1))
        slt(T9, reg2, reg1);
        return beq(T9, ZERO, label);
    }

    public MipsCode ble_(MipsRegister reg1, long value, String label) {
        set_(T9, value);
        return ble_(reg1, T9, label);
    }

    public MipsCode blt_(MipsRegister reg1, MipsRegister reg2, String label) {
        slt(T9, reg1, reg2);
        return bne(T9, ZERO, label);
    }

    public MipsCode blt_(MipsRegister reg1, long value, String label) {
        slti(T9, reg1, value);
        return bne(T9, ZERO, label);
    }

    public MipsCode bge_(MipsRegister reg1, MipsRegister reg2, String label) {
        // if(reg1 >= reg2) <-------> if(!(reg1 < reg2))
        slt(T9, reg1, reg2);
        return beq(T9, ZERO, label);
    }

    public MipsCode bge_(MipsRegister reg1, long value, String label) {
        set_(T9, value);
        return bge_(reg1, T9, label);
    }

    public MipsCode bgt_(MipsRegister reg1, MipsRegister reg2, String label) {
        // if(reg1 > reg2) <-------> if(reg2 < reg1)
        return blt_(reg2, reg1, label);
    }

    public MipsCode bgt_(MipsRegister reg1, long value, String label) {
        set_(T9, value);
        return bgt_(reg1, T9, label);
    }

    private void resolveBranches() {
        Iterator<Map.Entry<Integer, String>> it = pendingBranches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, String> entry = it.next();
            Integer labelAddress = labels.get(entry.getValue());
            if (labelAddress == null)
                continue;

            // Find the offset between the label address and the branch instruction address
            int branchAddress = entry.getKey();
            int offsetInLongs = (labelAddress - (branchAddress + 4)) / 4;
            setWordLE(branchAddress, offsetInLongs & 0xFFFF);
            it.remove();
        }
    }

    /** @throws RandomizerException if some branches target labels that were never defined */
    public void checkResolved() {
        if (!pendingBranches.isEmpty())
            throw new RandomizerException("Unresolved labels are remaining in code: " + pendingBranches.values());
    }

    @Override
    public byte[] toByteArray() {
        checkResolved();
        return super.toByteArray();
    }
}
