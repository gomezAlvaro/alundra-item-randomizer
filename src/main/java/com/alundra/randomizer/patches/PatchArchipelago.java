package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;
import com.alundra.randomizer.tools.MipsCode;
import com.alundra.randomizer.tools.PsxExeFile;
import com.alundra.randomizer.tools.RandomizerException;

import static com.alundra.randomizer.game.ItemCodes.*;
import static com.alundra.randomizer.tools.MipsRegister.*;

/**
 * Makes the game talk to the Archipelago client through a small mailbox in RAM.
 * <p>
 * Mailbox layout (little-endian words, at {@link #MAILBOX_ADDRESS}):
 * <pre>
 *   +0  magic "ALAP"
 *   +4  item ID the client wants the game to give (0 when the mailbox is free)
 *   +8  count of items the game gave from the mailbox so far (incremented by the game)
 *   +12 1 when the text at +16 must be shown in a message box (the game clears it once shown)
 *   +16 set to 1 by the game when the ending starts
 *   +20 null-terminated message text, up to 75 characters
 *   +96 name of the Archipelago slot (null-terminated, up to 31 characters), used by the client to log in
 *   +128 name of the multiworld seed (null-terminated, up to 63 characters)
 * </pre>
 * Once per frame, the game checks the mailbox, gives the requested item through its own item pickup routine, shows the
 * message if there is one and frees the mailbox. Items placed in other players' worlds are replaced by a placeholder item which gives nothing
 * and opens no box: the client announces what it stands for.
 */
public class PatchArchipelago extends GamePatch {
    /** Where the mailbox ends up in RAM, as long as it is the first block injected in the executable */
    public static final int MAILBOX_SIZE = 0xC0;
    public static final long MAILBOX_ADDRESS = 0x8001F800L - MAILBOX_SIZE;
    public static final long MAILBOX_MAGIC = 0x50414C41L;

    /** Unused item ID used for items belonging to other worlds */
    public static final int ITEM_AP_PLACEHOLDER = 0x1E;

    /** The function called once per frame while the game is running: we hook its call to a subroutine */
    private static final long FRAME_HOOK_CALL = 0x8002BFB4L;
    private static final long FRAME_HOOK_ORIGINAL_TARGET = 0x80048FB0L;
    /** Applies the effects of an item given by a chest or a script: A0 = player data, A1 = item ID */
    private static final long FUN_OBTAIN_ITEM = 0x800344D4L;
    /** The only place where a chest or script gives an item: we hook it to ignore the placeholder item */
    private static final long GIVE_ITEM_CALL = 0x8007EB4CL;
    /** Where a chest or script opens its "you got this item" box: S3 = item ID, A0 = text, A1 = 1 */
    private static final long ITEM_MESSAGE_CALL = 0x8007EB14L;
    /** Opens a message box: A0 = text, A1 = 1 */
    private static final long FUN_SHOW_MESSAGE = 0x8004608CL;
    private static final long FUN_PLAY_SOUND = 0x8004A128L;
    /** Sound effect played when an item is obtained */
    private static final int ITEM_JINGLE_SOUND = 0x34;
    private static final int MESSAGE_OFFSET = 20;
    /** In the state of a chest or script giving an item (S1): the object holding it, and the offset of its story flag in it */
    private static final int ITEM_OBJECT_OFFSET = 0x280;
    private static final int ITEM_OBJECT_FLAG_OFFSET = 0x40;
    /**
     * Table of the texts of the items of other worlds, found after this magic: entries of a 32-bit story flag
     * (0 ends the table) followed by a null-terminated text, with one more empty entry at the end.
     */
    public static final long TEXT_TABLE_MAGIC = 0x42544C41L;
    public static final int TEXT_ENTRY_SIZE = 80;
    public static final int TEXT_TABLE_ENTRIES = 320;
    /** Start of the branch of the game starting its ending: its first call is hooked to flag the goal as reached */
    private static final long ENDING_HOOK_CALL = 0x8002C70CL;
    private static final long ENDING_HOOK_ORIGINAL_TARGET = 0x8004AD18L;
    private static final long PLAYER_DATA = 0x801AC6F8L;
    /** File offset of the Alundra executable's text start (code RAM address 0x80020000 is at this file offset) */
    private static final long EXE_TEXT_FILE_OFFSET = 0x800L;
    private static final long EXE_TEXT_RAM_ADDRESS = 0x80020000L;

    private static final int SLOT_NAME_OFFSET = 0x60;
    private static final int SEED_NAME_OFFSET = 0x80;

    private final String slotName;
    private final String seedName;
    /** False to only reserve the mailbox in the executable: the Archipelago world adds the hooks itself */
    private final boolean exeHooks;

    public PatchArchipelago(String slotName, String seedName) {
        this(slotName, seedName, true);
    }

    public PatchArchipelago(String slotName, String seedName, boolean exeHooks) {
        this.slotName = slotName;
        this.seedName = seedName;
        this.exeHooks = exeHooks;
    }

    @Override
    public void alterGameData(GameData gameData) {
        gameData.item(ITEM_AP_PLACEHOLDER).name("Archipelago item");
    }

    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        PatchProgressiveItems.replaceItemSprite(data, ITEM_AP_PLACEHOLDER, ITEM_ZOLISTS_STONE);
    }

    /** @return the text as ASCII, cut so that it fits in a null-terminated field of the given size */
    private static byte[] asciiField(String text, int size) {
        byte[] bytes = new byte[size];
        byte[] ascii = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(ascii, 0, bytes, 0, Math.min(ascii.length, size - 1));
        return bytes;
    }

    @Override
    public void alterExeFile(PsxExeFile exe, GameFiles files, GameData gameData, RandomizerWorld world) {
        MipsCode mailbox = new MipsCode();
        mailbox.addLongLE(MAILBOX_MAGIC);
        while (mailbox.size() < MAILBOX_SIZE)
            mailbox.addLongLE(0);
        mailbox.setBytes(SLOT_NAME_OFFSET, asciiField(slotName, SEED_NAME_OFFSET - SLOT_NAME_OFFSET));
        mailbox.setBytes(SEED_NAME_OFFSET, asciiField(seedName, MAILBOX_SIZE - SEED_NAME_OFFSET));
        long mailboxAddress = exe.injectCode(mailbox);
        if (mailboxAddress != MAILBOX_ADDRESS)
            throw new RandomizerException("The Archipelago patch must be the first one injecting code in the executable"
                    + " (mailbox at " + Long.toHexString(mailboxAddress) + ")");

        if (!exeHooks)
            return;

        MipsCode tick = new MipsCode();
        tick.addiu(SP, SP, -0x20);
        tick.sw(RA, SP, 0x18);

        tick.set_(T0, MAILBOX_ADDRESS);
        tick.lw(T1, T0, 4, true);
        tick.beq(T1, ZERO, "no_item");

        // The game obtains the item exactly like when picking it up
        tick.set_(A0, PLAYER_DATA);
        tick.set_(A1, T1);
        tick.jal(FUN_OBTAIN_ITEM);

        tick.set_(T0, MAILBOX_ADDRESS);
        tick.sw(ZERO, T0, 4);
        tick.lw(T1, T0, 8, true);
        tick.addiu(T1, T1, 1);
        tick.sw(T1, T0, 8);

        tick.label_("no_item");
        tick.set_(T0, MAILBOX_ADDRESS);
        tick.lw(T1, T0, 12, true);
        tick.beq(T1, ZERO, "done");
        tick.sw(ZERO, T0, 12);
        tick.addiu(A0, T0, MESSAGE_OFFSET);
        tick.addiu(A1, ZERO, 1);
        tick.jal(FUN_SHOW_MESSAGE);
        tick.addiu(A0, ZERO, ITEM_JINGLE_SOUND);
        tick.jal(FUN_PLAY_SOUND);

        tick.label_("done");
        tick.lw(RA, SP, 0x18, true);
        tick.addiu(SP, SP, 0x20);
        // Tail call: the hooked function returns directly to the original caller
        tick.j(FRAME_HOOK_ORIGINAL_TARGET);
        long tickAddress = exe.injectCode(tick);

        exe.setCode(FRAME_HOOK_CALL - EXE_TEXT_RAM_ADDRESS + EXE_TEXT_FILE_OFFSET, new MipsCode().jal(tickAddress, false));

        // The goal is reached when the ending starts
        MipsCode goal = new MipsCode();
        goal.set_(T0, MAILBOX_ADDRESS);
        goal.addiu(T1, ZERO, 1);
        goal.sw(T1, T0, 16);
        goal.j(ENDING_HOOK_ORIGINAL_TARGET);
        long goalAddress = exe.injectCode(goal);
        exe.setCode(ENDING_HOOK_CALL - EXE_TEXT_RAM_ADDRESS + EXE_TEXT_FILE_OFFSET, new MipsCode().jal(goalAddress, false));

        // Items of other worlds must not be added to the inventory, and their box tells what they are. They are the
        // chests listed in a table, filled when the game is built, of (story flag of the chest, text). A chest holds
        // either the placeholder or an item of Alundra whose sprite is shown for it. A chest of another world without
        // an entry still gives nothing and shows the game's own box.
        MipsCode table = new MipsCode();
        table.addLongLE(TEXT_TABLE_MAGIC);
        while (table.size() < 4 + TEXT_TABLE_ENTRIES * TEXT_ENTRY_SIZE + 4)
            table.addLongLE(0);
        long tableAddress = exe.injectCode(table) + 4;

        MipsCode give = new MipsCode();
        give.beq_(A1, ITEM_AP_PLACEHOLDER, "skip");
        appendTableSearch(give, tableAddress, "give", "skip");
        give.label_("skip");
        give.jr(RA);
        give.label_("give");
        give.j(FUN_OBTAIN_ITEM);
        long giveAddress = exe.injectCode(give);

        exe.setCode(GIVE_ITEM_CALL - EXE_TEXT_RAM_ADDRESS + EXE_TEXT_FILE_OFFSET, new MipsCode().jal(giveAddress, false));

        MipsCode message = new MipsCode();
        appendTableSearch(message, tableAddress, "show", "found");
        message.label_("found");
        message.addiu(A0, T1, 4);
        message.label_("show");
        message.j(FUN_SHOW_MESSAGE);
        long messageAddress = exe.injectCode(message);

        exe.setCode(ITEM_MESSAGE_CALL - EXE_TEXT_RAM_ADDRESS + EXE_TEXT_FILE_OFFSET, new MipsCode().jal(messageAddress, false));
    }

    /**
     * Looks for the story flag of the chest being opened (the state of a chest is in S1) in the text table. Branches to
     * one of the labels, with T1 pointing at the entry when found.
     */
    private static void appendTableSearch(MipsCode code, long tableAddress, String notFoundLabel, String foundLabel) {
        code.lw(T0, S1, ITEM_OBJECT_OFFSET, true);
        code.lw(T0, T0, ITEM_OBJECT_FLAG_OFFSET, true);
        code.set_(T1, tableAddress);
        String search = "search_" + notFoundLabel;
        code.label_(search);
        code.lw(T2, T1, 0, true);
        code.beq(T2, ZERO, notFoundLabel);
        code.beq(T2, T0, foundLabel);
        code.addiu(T1, T1, TEXT_ENTRY_SIZE);
        code.bra_(search);
    }
}
