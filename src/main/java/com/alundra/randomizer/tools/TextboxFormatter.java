package com.alundra.randomizer.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Formats a long text so that it fits inside the game's textboxes, by inserting newlines ({@code \N}) and
 * "wait for input" markers ({@code \A}) based on an approximation of the font's character widths.
 * <p>
 * Escape sequences understood by the game ({@code \C}, {@code \W2}, {@code \0100\Y}...) are kept as-is.
 */
public class TextboxFormatter {
    private static final int TEXTBOX_WIDTH = 500;
    private static final int TEXTBOX_LINES = 3;
    private static final int[] CHARACTER_SIZES = buildCharacterSizes();

    private final String inputString;
    private final StringBuilder output = new StringBuilder();
    private final List<String> words = new ArrayList<>();
    private int currentLineWidth = 0;
    private int lineCount = 0;
    private boolean previousWordRequiresSpace = false;
    private String result = null;

    public TextboxFormatter(String inputString) {
        this.inputString = inputString;
    }

    private static int[] buildCharacterSizes() {
        int[] sizes = new int[256];
        Arrays.fill(sizes, 14);
        sizes['w'] = 24;
        sizes['m'] = 24;
        sizes['n'] = 18;
        sizes['H'] = 18;
        sizes['h'] = 16;
        sizes['a'] = 16;
        sizes['A'] = 16;
        sizes['p'] = 16;
        sizes['i'] = 10;
        sizes['I'] = 10;
        sizes['t'] = 10;
        sizes['l'] = 10;
        sizes[' '] = 10;
        sizes['!'] = 8;
        sizes['.'] = 6;
        sizes[','] = 6;
        sizes['\\'] = 0;
        return sizes;
    }

    private static int charSize(char c) {
        return CHARACTER_SIZES[c & 0xFF];
    }

    public static String format(String input) {
        return new TextboxFormatter(input).format();
    }

    public String format() {
        if (result != null)
            return result;

        findWords();

        for (String word : words) {
            if (word.startsWith("\\")) {
                char code = word.length() > 1 ? word.charAt(1) : '\0';
                if (code == 'N')
                    addDynamicNewline();
                else if (code == 'A')
                    addWaitForInputAndNewline();
                else
                    output.append(word);

                previousWordRequiresSpace = false;
                continue;
            }

            int wordLength = 0;
            for (char c : word.toCharArray())
                wordLength += charSize(c);

            int wordLengthWithSpace = wordLength;
            if (previousWordRequiresSpace)
                wordLengthWithSpace += charSize(' ');

            if (currentLineWidth + wordLengthWithSpace <= TEXTBOX_WIDTH) {
                if (previousWordRequiresSpace)
                    output.append(' ');
                output.append(word);

                currentLineWidth += wordLengthWithSpace;
                previousWordRequiresSpace = true;
            } else {
                addDynamicNewline();
                output.append(word);
                currentLineWidth += wordLength;
                previousWordRequiresSpace = true;
            }
        }

        result = output.toString();
        return result;
    }

    private char charAtOrNull(int i) {
        return i < inputString.length() ? inputString.charAt(i) : '\0';
    }

    private void findWords() {
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < inputString.length(); ++i) {
            char c = inputString.charAt(i);
            // Cut words when encountering a space or an escape sequence
            if (currentWord.length() > 0 && (c == ' ' || c == '\\')) {
                words.add(currentWord.toString());
                currentWord.setLength(0);
            }

            // Process escape sequences as individual words
            if (c == '\\') {
                currentWord.append('\\').append(charAtOrNull(++i));

                // Wait (\W) instructions carry a digit as an extra argument
                if (currentWord.toString().equals("\\W"))
                    currentWord.append(charAtOrNull(++i));

                // Ask (\0) instructions are closed by a '\Y'
                if (currentWord.toString().equals("\\0")) {
                    while (charAtOrNull(++i) != '\\') {
                        if (i >= inputString.length())
                            throw new RandomizerException("Unterminated \\0 sequence in textbox string");
                        currentWord.append(inputString.charAt(i));
                    }
                    // Add the trailing '\Y'
                    currentWord.append(charAtOrNull(i));
                    currentWord.append(charAtOrNull(++i));
                }

                words.add(currentWord.toString());
                currentWord.setLength(0);
                continue;
            } else if (c == ' ') {
                continue;
            }

            currentWord.append(c);
        }

        if (currentWord.length() > 0)
            words.add(currentWord.toString());
    }

    private void addDynamicNewline() {
        // Add a "wait for input and newline" if we reached 3 lines, or just a newline if there is room for more lines
        if (lineCount < TEXTBOX_LINES - 1)
            addNewline();
        else
            addWaitForInputAndNewline();
    }

    private void addNewline() {
        output.append("\\N");
        ++lineCount;
        currentLineWidth = 0;
    }

    private void addWaitForInputAndNewline() {
        output.append("\\A");
        lineCount = 0;
        currentLineWidth = 0;
    }
}
