package com.alundra.randomizer.tools;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses command line arguments of the form {@code --name=value} or {@code --flag}. Names are case-insensitive.
 * A boolean flag can be negated with a "no" prefix ({@code --nopause} is equivalent to {@code --pause=false}).
 */
public class ArgumentDictionary {
    private final Map<String, String> args = new HashMap<>();

    public ArgumentDictionary(String[] argv) {
        for (String param : argv) {
            if (!param.startsWith("--"))
                continue;

            int equalsIndex = param.indexOf('=', 2);
            String name = (equalsIndex >= 0 ? param.substring(2, equalsIndex) : param.substring(2)).toLowerCase(Locale.ROOT);
            String value = equalsIndex >= 0 ? param.substring(equalsIndex + 1) : "";
            args.put(name, value);
        }
    }

    public boolean contains(String name) {
        return args.containsKey(name) || args.containsKey("no" + name);
    }

    public String getString(String name) {
        return getString(name, "");
    }

    public String getString(String name, String defaultValue) {
        return args.getOrDefault(name, defaultValue);
    }

    public void setString(String key, String value) {
        args.put(key, value);
    }

    public int getInteger(String name, int defaultValue) {
        String value = args.get(name);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        // "--noParam" <==> "--param=false"
        if (args.containsKey("no" + name))
            return false;

        String contents = args.get(name);
        if (contents == null)
            return defaultValue;
        // "--param=false" -> false, "--param=true" or "--param" -> true
        return !contents.toLowerCase(Locale.ROOT).equals("false");
    }
}
