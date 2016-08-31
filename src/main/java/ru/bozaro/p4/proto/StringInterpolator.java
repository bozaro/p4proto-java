package ru.bozaro.p4.proto;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * @author Marat Radchenko
 */
public final class StringInterpolator {

    private static void append(@NotNull StringBuilder sb, @NotNull CharSequence s, int offset, int len) {
        sb.append(s, offset, offset + len);
    }

    private static int memchr(@NotNull CharSequence s, int fromIndex, char c, int length) {
        int endIndex = fromIndex + Math.min(length, s.length() - fromIndex);
        for (int i = fromIndex; i < endIndex; ++i)
            if (s.charAt(i) == c)
                return i;

        return -1;
    }

    /**
     * Ported from https://swarm.workshop.perforce.com/projects/perforce_software-p4/files/2016-1/support/strops.cc (StrOps::Expand2)
     */
    @NotNull
    public static String interpolate(@NotNull String m, @NotNull Function<String, String> lookup) {
        final StringBuilder o = new StringBuilder();

        int p = 0;
        int q, r, s, t;

        // Handle sequences of
        //	text %var% ...
        //	text [ stuff1 %var% stuff2 ] ...

        while ((q = m.indexOf('%', p)) >= 0) {
            if (q < m.length() - 1 && m.charAt(q + 1) == '\'') // %' stuff '%: include stuff, uninspected...
            {
                for (s = q + 2; s < m.length(); s++)
                    if (m.charAt(s) == '\'' && m.charAt(s + 1) == '%')
                        break;
                if (s >= m.length())
                    break; // %'junk
                append(o, m, p, q - p);
                q += 2;
                append(o, m, q, s - q);
                p = s + 2;
                continue;
            }

            // variables: (p)text (r)[ stuff (q)%var(s)% stuff2 (t)]

            if ((s = m.indexOf('%', q + 1)) < 0) {
                // %junk
                break;
            } else if (s == q + 1) {
                // %% - [ %% ] not handled!
                append(o, m, p, s - p);
                p = s + 1;
                continue;
            }

            // Pick out var name and look up value

            final String var = m.substring(q + 1, s);
            final String val = lookup.apply(var);

            // Now handle %var% or [ %var% | alt ]

            if ((r = memchr(m, p, '[', q - p)) < 0) {
                // %var%

                append(o, m, p, q - p);
                o.append(val);
                p = s + 1;

            } else if ((t = m.indexOf(']', s + 1)) < 0) {
                // [ junk
                break;
            } else {
                // [ stuff1 %var% stuff2 | alternate ]

                append(o, m, p, r - p);

                // [ | alternate ]

                int v = memchr(m, s, '|', t - s);
                if (v < 0) v = t;

                if (val.length() > 0) {
                    // stuff1, val, stuff2
                    append(o, m, r + 1, q - r - 1);
                    o.append(val);
                    append(o, m, s + 1, v - s - 1);
                } else if (v < t) {
                    // alternate
                    append(o, m, v + 1, t - v - 1);
                }

                p = t + 1;
            }
        }

        o.append(m, p, m.length());

        return o.toString();
    }
}
