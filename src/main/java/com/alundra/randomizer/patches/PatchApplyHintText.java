package com.alundra.randomizer.patches;

import com.alundra.randomizer.game.GameData;
import com.alundra.randomizer.game.RoomStrings;
import com.alundra.randomizer.model.RandomizerWorld;
import com.alundra.randomizer.tools.BinaryFile;

import static com.alundra.randomizer.game.MapCodes.*;
import static com.alundra.randomizer.tools.TextboxFormatter.format;

/**
 * Writes the generated hints inside the dialogues of the characters giving them.
 */
public class PatchApplyHintText extends GamePatch {
    @Override
    public void alterDatasFile(BinaryFile data, GameFiles files, GameData gameData, RandomizerWorld world) {
        applyLarsCryptHint(data, world);
        applyReptileLairHint(data, world);
        applyNirudesLairHint(data, world);
        applyCoastalCaveHint(data, world);
        applyTorlaMountainHint(data, world);
        applyMagyscarHint(data, world);
        applyCoalMineHint(data, world);
        applyAncientShrineHint(data, world);
        applyFairyPondHint(data, world);
        applyGreatTreeHint(data, world);
        applyNavasKeepHint(data, world);
        applyJessHint(data, world);
        applyCoalMineSignVanillaHint(data);
    }

    private static String hint(RandomizerWorld world, String hintSourceId) {
        return world.hintSource(hintSourceId).text();
    }

    private static void applyLarsCryptHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "lars_crypt");

        RoomStrings strings = new RoomStrings(MAP_LARS_CRYPT_25, data);
        strings.string(35, format("\\CYou need to know... " + hintText + "."));
        strings.string(37, "\\CGood luck on your quest.");
        strings.applyOnData(data);
    }

    private static void applyReptileLairHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "reptile_lair");

        RoomStrings strings = new RoomStrings(MAP_REPTILE_LAIR_84, data);
        strings.string(59, format(
                "\\CHear my words, Releaser. I have come to be known as Jeal. I am also one of the seven guardians\\A"
                        + "I was a metalsmith who sought to understand the mysteries of life.\\N\\A"
                        + "To stop the evil present in the mortal realm, I sacrificed my human form and vanished into time.\\A"
                        + "I have given thought to trusting the light of your soul to save this weary land.\\A"
                        + "Here is a hint to help you in your journey: " + hintText + "."));
        strings.applyOnData(data);
    }

    private static void applyNirudesLairHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "nirudes_lair");

        RoomStrings strings = new RoomStrings(MAP_OVERWORLD_E1, data);
        strings.string(37, format("\\CLord Nirude left a message for you with his dying breath: " + hintText + "."));
        strings.applyOnData(data);
    }

    private static void applyCoastalCaveHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "coastal_cave");

        RoomStrings strings = new RoomStrings(MAP_COASTAL_CAVE_76, data);
        strings.string(11, format(
                "\\CYou are the Releaser of which\\NLars has spoken.\\N\\A"
                        + "I sense the power that flows within you. I am Vul, one of the seven guardians.\\A"
                        + "I have seen your power, and sense that you may possess the strength to one day crush Melzas.\\A"
                        + "Listen to my words as they will help you to find what you seek: \\A"
                        + hintText + ".\\N"
                        + "Use it well, Releaser\\W2you must recover the crests and beat Melzas!\\N"));
        strings.applyOnData(data);
    }

    private static void applyTorlaMountainHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "torla_mountain");

        RoomStrings strings = new RoomStrings(MAP_325, data);
        strings.string(10, format(
                "\\FUngh\\T\\W2\\T\\W2 You humans have grown so strong, too strong, during my long rest.\\A"
                        + "I learnt this during my sleep: " + hintText + ".\\A"
                        + "I need to go now, Releaser.\\AThis time,\\W2for eternity..."));
        strings.applyOnData(data);
    }

    private static void applyMagyscarHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "magyscar");

        RoomStrings strings = new RoomStrings(MAP_MAGYSCAR_95, data);
        strings.string(7, format(
                "\\CI am Uma, Queen of Life and Death, and one of the seven Guardians.\\A"
                        + "The Murgg came before you and stole the Crest. My servant was so angry he mistakenly attacked you.\\A"
                        + "Please accept my apologies for his rash actions. The Murgg were talking about something...\\A"
                        + "What they said might help you: \"" + hintText + "\"\\A"
                        + "Go forth now, Releaser. Return the stolen Crests and destroy Melzas."));
        strings.applyOnData(data);
    }

    private static void applyCoalMineHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "coal_mine");

        RoomStrings strings = new RoomStrings(MAP_COAL_MINE_329, data);
        strings.string(46, format(
                "\\FWhat? You haven't found a Crest here?\\A"
                        + "WUKIKI! You remember what Lord Melzas said? \"" + hintText + "\"\\A"
                        + "GO and do not return without the remaining Crests! WUKIKI!"));
        strings.applyOnData(data);
    }

    private static void applyAncientShrineHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "ancient_shrine");

        RoomStrings strings = new RoomStrings(MAP_ANCIENT_SHRINE_30, data);
        strings.string(14, format(
                "\\CYou are truly worthy of the power you possess. Your courage shall not go unrewarded.\\A"
                        + "You deserve to know these ancestral words: \"" + hintText + "\""));
        strings.applyOnData(data);
    }

    private static void applyFairyPondHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "fairy_pond");

        RoomStrings strings = new RoomStrings(MAP_FAIRY_POND_161, data);
        strings.string(95, format(
                "\\DI am at once amazed and proud that you have progressed this far, Releaser.\\A"
                        + "I am Stenia, Queen of the waters. Your actions have been shown to my heart by the rivers and oceans.\\A"
                        + "I have been waiting for you to come, for I am able to help you in your quest.\\A"
                        + "Mark the words I am about to tell you: " + hintText + "."));
        strings.applyOnData(data);
    }

    private static void applyGreatTreeHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "great_tree");

        RoomStrings strings = new RoomStrings(MAP_GREAT_TREE_153, data);
        strings.string(48, format(
                "\\EAhh! Furless one, you have come so far, but this is the end of your sad journey of self delusion.\\A"
                        + "Since you are going to die, I can tell you the missing piece in the puzzle...\\A"
                        + hintText + "!\\A"
                        + "But it doesn't matter because now is the time where you DIE! WUKIKI!"));
        strings.applyOnData(data);
    }

    private static void applyNavasKeepHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "nava_keep");

        RoomStrings strings = new RoomStrings(MAP_432, data);

        // Shorten Nava & Zorgia talk
        strings.string(32, "\\CIt's time you knew the truth about\\NMelzas. He is a ravenous monster\\Nfrom a distant star...");
        strings.string(34, format("\\FAlundra, it's about time I get rid of you. I just hope it won't get too dirty..."));

        // Make post-fight Nava give a hint
        strings.string(36, format(
                "\\CIt is I, Nava.\\A"
                        + "You were wonderful, Alundra. I've never witnessed such a passionate fight!\\A"
                        + "Now listen to me very well. There is a secret we've been sharing between guardians for centuries, and I think it is time for you to know about it.\\A"
                        + "You must know that " + hintText + ".\\A"
                        + "I hope you make good use of it."));
        strings.applyOnData(data);
    }

    private static void applyJessHint(BinaryFile data, RandomizerWorld world) {
        String hintText = hint(world, "jess");

        for (int mapId : new int[]{MAP_JESS_HOUSE_CHEST_OPENABLE, MAP_JESS_HOUSE_RANDOMIZER}) {
            RoomStrings strings = new RoomStrings(mapId, data);
            strings.string(0, format(
                    "\\CHello there, Alundra! This morning, I heard something in town.\\A"
                            + "Apparently, " + hintText + ". Can you believe it?"));
            strings.applyOnData(data);
        }
    }

    private static void applyCoalMineSignVanillaHint(BinaryFile data) {
        RoomStrings strings = new RoomStrings(MAP_COAL_MINE_61, data);
        strings.string(8, "\\B\\HEngine Room to the right\\N\\HTo start the engine, do the following:\\N\\HLeft, left, right, left");
        strings.applyOnData(data);
    }
}
