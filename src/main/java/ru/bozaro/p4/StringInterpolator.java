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
        int escapeStart = -1;
        for (int i = 0; i < fmt.length(); ++i) {
            final char c = fmt.charAt(i);
            switch (c) {
                case '%':
                    if (argStart >= 0) {
                        if (escapeStart >= 0) {
                            final int escapeEnd = Math.max(escapeStart, i - 1);
                            result.append(fmt.substring(escapeStart, escapeEnd));
                        } else {
                            final String argName = fmt.substring(argStart, i);
                            final String argValue = lookup.apply(argName);
                            result.append(argValue);
                        }
                        argStart = -1;
                        escapeStart = -1;
                    } else {
                        argStart = i + 1;
                    }
                    break;
                case '\'':
                    if (escapeStart < 0) {
                        if (argStart >= 0) {
                            escapeStart = i + 1;
                        } else {
                            result.append(c);
                        }
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
