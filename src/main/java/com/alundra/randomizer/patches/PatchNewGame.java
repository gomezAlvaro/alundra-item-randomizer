package com.alundra.randomizer.patches;

import com.alundra.randomizer.RandomizerOptions;
import com.alundra.randomizer.game.Flag;
import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.Item;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.ByteArray;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;

import static com.alundra.randomizer.game.ItemCodes.ITEM_COUNT;
import static com.alundra.randomizer.game.MapCodes.*;
import static com.alundra.randomizer.tools.MipsRegister.*;

/**
 * Changes the new game state: spawn location, starting flags, health, mana, gold and inventory.
 */
public class PatchNewGame extends GamePatch {
    private static final long CODE_BASE_ADDR = 0x1F800;

    private final RandomizerOptions options;

    public PatchNewGame(RandomizerOptions options) {
        this.options = options;
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        // Change spawn map ID
        exe.setWordLE(0x12558, MAP_JESS_HOUSE_RANDOMIZER);
        // Change spawn coords
        exe.setWordLE(0x12564, 0x0026);
        exe.setWordLE(0x12570, 0x000C);

        // Inject and call a custom "init game" function setting up flags properly
        long returnAddr = 0x1253C + CODE_BASE_ADDR;
        long funcAddr = injectFuncInitGame(exe, returnAddr, gameData);
        exe.setCode(0x12534, new MipsCode().j(funcAddr, false));

        // Set starting HP
        exe.setCode(0x12590, new MipsCode().addiu(A0, ZERO, options.startingHealth()));
        exe.setCode(0x12598, new MipsCode().addiu(A0, ZERO, options.startingHealth()));
        // Set starting MP
        exe.setCode(0x125A0, new MipsCode().addiu(A0, ZERO, options.startingMana()));
        exe.setCode(0x125A8, new MipsCode().addiu(A0, ZERO, options.startingMana()));
        // Set starting Gilders
        exe.setCode(0x125B0, new MipsCode().addiu(A0, ZERO, options.startingGold()));

        // Set starting items
        boolean[] itemsOwnedOnStart = new boolean[ITEM_COUNT];
        for (Item item : world.startingInventory())
            itemsOwnedOnStart[item.id()] = true;

        for (int itemId = 1; itemId < ITEM_COUNT; ++itemId) {
            long itemInfo2Addr = 0xA85EA + (itemId * 8L);
            int flag = exe.getByte(itemInfo2Addr) & 0x7F;
            if (itemsOwnedOnStart[itemId])
                flag |= 0x80;
            exe.setByte(itemInfo2Addr, flag);
        }
    }

    private static long injectFuncInitGame(PsxExeFile exe, long returnAddr, GameData gameData) {
        // Build a memory map of starting flags in order to know which words we need to edit
        ByteArray flagBytes = new ByteArray(0x1000);
        flagBytes.resize(0x1000, 0);
        for (Flag flag : gameData.startingFlags()) {
            int index = flag.offset() - 0xD000;
            int mask = (1 << flag.bit()) & 0xFF;
            flagBytes.setByte(index, flagBytes.getByte(index) | mask);
        }

        MipsCode func = new MipsCode();

        // Add instructions replaced by the J injection
        func.jal(0x83DA0);
        func.lui(AT, 0x801E);

        // For each non-null flag word, add instructions to set it up on new game
        for (int addr = 0; addr < flagBytes.size(); addr += 2) {
            int value = flagBytes.getWordLE(addr);
            if (value == 0)
                continue;
            func.set_(T0, value);
            func.sh(T0, AT, addr + 0xD000);
        }

        // Set default Inoa version to have Lutas chimney open (0x00F1 -> 0x1DD578 && 0x1DD586)
        func.set_(T0, MAP_INOA_RANDOMIZER);
        func.sh(T0, AT, 0xD578);
        func.sh(T0, AT, 0xD586);
        func.sh(T0, AT, 0xD63A); // 0xF1 -> 0x1DD63A for variant where blue chest can be opened

        // Set Jess house to a variant where chest can be opened
        func.set_(T0, MAP_JESS_HOUSE_CHEST_OPENABLE);
        func.sh(T0, AT, 0xD618);

        // Set Overworld C4 to the "Merrick shop open" variant (0x0197 -> 0x1DD44C)
        func.set_(T0, MAP_OVERWORLD_C4_MERRICK_OPEN_VARIANT);
        func.sh(T0, AT, 0xD44C);

        // Set Overworld E1 (Nirude) to a variant where things work properly
        func.set_(T0, 0x01B7);
        func.sh(T0, AT, 0xD456);

        // In the vanilla game, only a handful of items can be found in shops and be taken inside Alundra's hands.
        // Developers only set valid physics properties to item objects that could be encountered inside shops,
        // not to every possible item object. This code chunk copies the "good" physics properties from a well-handled
        // object to all other item objects, so that any item works inside shops.
        func.set_(T5, 0x800FEFB4L);
        func.set_(T6, 0x800FEFFCL);
        func.set_(AT, 0x800CC004L);
        func.set_(T4, 0x800CC168L);
        func.label_("entity_type_def_loop");
        {
            func.lw(T3, AT);
            func.beq_(T3, 0xFFFFFFFFL, "next_iter");
            {
                func.sw(T5, T3);
                func.sw(T6, T3, 4);
            }
            func.label_("next_iter");
            func.addiu(AT, 0x4);
            func.blt_(AT, T4, "entity_type_def_loop");
        }

        // Jump back to calling procedure
        func.j(returnAddr);

        return exe.injectCode(func);
    }
}
