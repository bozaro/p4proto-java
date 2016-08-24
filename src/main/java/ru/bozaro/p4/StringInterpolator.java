package ru.bozaro.p4;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author Marat Radchenko
 */
public final class StringInterpolator {

    @NotNull
    public static String interpolate(@NotNull String fmt, @NotNull Function<String, String> lookup) {
        final StringBuilder result = new StringBuilder();

        int argStart = -1;
        for (int i = 0; i < fmt.length(); ++i) {
            final char c = fmt.charAt(i);
            switch (c) {
                case '%':
                    if (argStart >= 0) {
                        final String argName = fmt.substring(argStart, i);
                        argStart = -1;
                        final String argValue = lookup.apply(argName);
                        result.append(argValue);
                    } else {
                        argStart = i + 1;
                    }
                    break;
                default:
                    if (argStart < 0) {
                        result.append(c);
                    }
            }
        }

        return result.toString();
    }
}
