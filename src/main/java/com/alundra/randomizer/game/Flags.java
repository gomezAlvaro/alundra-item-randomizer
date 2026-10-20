package com.alundra.randomizer.game;

/** Story flags used by the randomizer. */
public final class Flags {
    private Flags() {
    }

    public static final Flag FLAG_HUD_ALLOWED = new Flag(0xD403, 6);
    public static final Flag FLAG_SEPTIMUS_WAKE_UP_TALKING_ABOUT_RONAN = new Flag(0xD358, 0);
    public static final Flag FLAG_SYBILL_HOLY_SWORD_VISION_1 = new Flag(0xD3F3, 6);
    public static final Flag FLAG_SYBILL_HOLY_SWORD_VISION_2 = new Flag(0xD3F3, 5);
    public static final Flag FLAG_UNDERGROUND_WATERWAY_OPENED = new Flag(0xD370, 4);
    public static final Flag FLAG_REMOVED_TREE_TO_CLIFFS_OF_MADNESS = new Flag(0xD35F, 5);
    public static final Flag FLAG_CHANGE_SIGN_TO_CLIFFS_OF_MADNESS = new Flag(0xD34F, 3);
    public static final Flag FLAG_CAN_ACTIVATE_MEGALITHS = new Flag(0xD3E6, 0);
    public static final Flag FLAG_LEARNED_ABOUT_MERRICK_WANTING_FALCONS = new Flag(0xD3E6, 6);
    public static final Flag FLAG_TALKED_WITH_MERRICK = new Flag(0xD3E6, 7);
    public static final Flag FLAG_TALKED_WITH_MERRICK_ABOUT_FALCONS = new Flag(0xD3E7, 0);
    public static final Flag FLAG_ACCEPTED_MERRICK_DEAL_ABOUT_FALCONS = new Flag(0xD3E9, 1);
    public static final Flag FLAG_OPENED_LAKE_SHRINE_EXTERIOR_MAIN_GATE = new Flag(0xD3B2, 7);
    public static final Flag FLAG_LAKE_SHRINE_EXTERIOR_RIGHT_GARDEN_BUTTON = new Flag(0xD3C1, 7);
    public static final Flag FLAG_LAKE_SHRINE_EXTERIOR_LEFT_GARDEN_BUTTON = new Flag(0xD3C2, 0);
    public static final Flag FLAG_LAKE_SHRINE_EXTERIOR_GARDEN_MIDDLE_GATE = new Flag(0xD3C2, 6);
    public static final Flag FLAG_LAKE_SHRINE_INTERIOR_MELZAS_INTRO_CUTSCENE_1 = new Flag(0xD3B3, 2);
    public static final Flag FLAG_LAKE_SHRINE_INTERIOR_MELZAS_INTRO_CUTSCENE_2 = new Flag(0xD3B3, 3);
    public static final Flag FLAG_LAKE_SHRINE_NW_SEAL_BROKEN = new Flag(0xD3B3, 4);
    public static final Flag FLAG_LAKE_SHRINE_W_SEAL_BROKEN = new Flag(0xD3B3, 5);
    public static final Flag FLAG_LAKE_SHRINE_SW_SEAL_BROKEN = new Flag(0xD3B3, 6);
    public static final Flag FLAG_LAKE_SHRINE_SE_SEAL_BROKEN = new Flag(0xD3B3, 7);
    public static final Flag FLAG_LAKE_SHRINE_E_SEAL_BROKEN = new Flag(0xD3B4, 0);
    public static final Flag FLAG_LAKE_SHRINE_NE_SEAL_BROKEN = new Flag(0xD3B4, 1);
    public static final Flag FLAG_CAN_ENTER_LARS_CRYPT = new Flag(0xD351, 0);
    public static final Flag FLAG_OTHER_FLOWERS_ARE_IN_GRAVEYARD = new Flag(0xD34D, 7);
    public static final Flag FLAG_CAN_PUT_FLOWERS_IN_GRAVEYARD = new Flag(0xD351, 1);
    public static final Flag FLAG_CAN_EXIT_LARS_CRYPT = new Flag(0xD34E, 0);
    public static final Flag FLAG_MAGYSCAR_WALL_IS_CRACKED = new Flag(0xD357, 5);
    public static final Flag FLAG_COAL_MINE_BOSS_BEATEN = new Flag(0xD395, 5);
    public static final Flag FLAG_NIRUDE_IS_BROKEN = new Flag(0xD34F, 5);
    public static final Flag FLAG_TORLA_GEYSERS_ACTIVE = new Flag(0xD37A, 3);
    public static final Flag FLAG_NIRUDE_STATUES_VULNERABLE = new Flag(0xD3CC, 6);
    public static final Flag FLAG_SAW_NIRUDE_STATUES_ACTIVATION_CUTSCENE = new Flag(0xD3D0, 0);
    public static final Flag FLAG_NIRUDE_SAW_TOP_LEFT_CUTSCENE = new Flag(0xD3CE, 6);
    public static final Flag FLAG_NIRUDE_LOWER_BOULDER_REMOVED = new Flag(0xD3CE, 7);
    public static final Flag FLAG_PLACED_RUBY_CREST = new Flag(0xD3CA, 0);
    public static final Flag FLAG_PLACED_SAPPHIRE_CREST = new Flag(0xD3CA, 1);
    public static final Flag FLAG_PLACED_TOPAZ_CREST = new Flag(0xD3CA, 2);
    public static final Flag FLAG_PLACED_AGATE_CREST = new Flag(0xD3CA, 5);
    public static final Flag FLAG_PLACED_EMERALD_CREST = new Flag(0xD3CA, 4);
    public static final Flag FLAG_PLACED_GARNET_CREST = new Flag(0xD3CA, 3);
    public static final Flag FLAG_PLACED_DIAMOND_CREST = new Flag(0xD3CA, 6);
    public static final Flag FLAG_LARS_CRYPT_SAGES_IN_GOOD_ORDER = new Flag(0xD3C8, 6);
    public static final Flag FLAG_LARS_CRYPT_TALKED_WITH_MASTER_SAGE_ONCE = new Flag(0xD3C8, 7);
    public static final Flag FLAG_LARS_CRYPT_SAGES_CUTSCENE_ACTIVE = new Flag(0xD343, 7);
    public static final Flag FLAG_CASINO_SHOOTING_RANGE_1_WIN = new Flag(0xD3F6, 1);
    public static final Flag FLAG_CASINO_SHOOTING_RANGE_2_WINS = new Flag(0xD3F6, 2);
    public static final Flag FLAG_CASINO_SHOOTING_RANGE_3_WINS = new Flag(0xD3F6, 3);
    public static final Flag FLAG_CASINO_SHOOTING_RANGE_4_WINS = new Flag(0xD3F6, 4);
    public static final Flag FLAG_CASINO_SHOOTING_RANGE_BANNED = new Flag(0xD3F6, 6);
    public static final Flag FLAG_CASINO_ROULETTE_1_WIN = new Flag(0xD3F6, 7);
    public static final Flag FLAG_CASINO_ROULETTE_2_WINS = new Flag(0xD3F6, 8);
    public static final Flag FLAG_CASINO_ROULETTE_3_WINS = new Flag(0xD3F7, 0);
    public static final Flag FLAG_CASINO_ROULETTE_4_WINS = new Flag(0xD3F7, 1);
    public static final Flag FLAG_CASINO_ROULETTE_BANNED = new Flag(0xD3F7, 3);
    public static final Flag FLAG_CASINO_SLIME_1_WIN = new Flag(0xD3F7, 5);
    public static final Flag FLAG_CASINO_SLIME_2_WINS = new Flag(0xD3F7, 6);
    public static final Flag FLAG_CASINO_SLIME_3_WINS = new Flag(0xD3F7, 7);
    public static final Flag FLAG_CASINO_SLIME_4_WINS = new Flag(0xD3F8, 0);
    public static final Flag FLAG_CASINO_SLIME_BANNED = new Flag(0xD3F8, 2);
    public static final Flag FLAG_LURVY_WELCOMED_US_ONCE = new Flag(0xD3BE, 2);
    public static final Flag FLAG_ANCIENT_SHRINE_SYBOLS_PILLAR_ROOM_CLEARED = new Flag(0xD3F8, 7);
    public static final Flag FLAG_JESS_TALKED_LEAVING_HOUSE_ON_GAME_START = new Flag(0xD334, 7);
}
