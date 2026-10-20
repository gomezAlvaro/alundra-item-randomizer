package com.alundra.randomizer.tools;

/**
 * Exception type used for every "expected" failure of the randomizer (bad preset, bad input image, impossible
 * generation...). These are reported to the user as a simple error message rather than a stack trace.
 */
public class RandomizerException extends RuntimeException {
    public RandomizerException(String reason) {
        super(reason);
    }

    public RandomizerException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
