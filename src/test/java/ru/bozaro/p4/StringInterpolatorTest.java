package ru.bozaro.p4;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static ru.bozaro.p4.StringInterpolator.interpolate;

/**
 * @author Marat Radchenko
 */
public final class StringInterpolatorTest {

    @Test
    public void empty() {
        assertEquals(interpolate("", s -> ""), "");
    }

    @Test
    public void simple() {
        assertEquals(interpolate("foobar", s -> ""), "foobar");
    }

    @Test
    public void arg() {
        assertEquals(interpolate("%foo%", s -> "foo".equals(s) ? "bar" : ""), "bar");
    }

    @Test
    public void emptyArgName() {
        assertEquals(interpolate("%%", s -> ""), "");
    }

    @Test
    public void unmatched() {
        assertEquals(interpolate("%", s -> ""), "");
    }

    @Test
    public void unmatched2() {
        assertEquals(interpolate("%aa", s -> ""), "");
    }

    @Test
    public void escaped() {
        assertEquals(interpolate("Perforce password (%'P4PASSWD'%) invalid or unset.", s -> ""), "Perforce password (P4PASSWD) invalid or unset.");
    }

    @Test
    public void escapedEmpty() {
        assertEquals(interpolate("%''%", s -> ""), "");
    }

    @Test
    public void escapedInvalid() {
        assertEquals(interpolate("%'%", s -> ""), "");
    }

    @Test
    public void quote() {
        assertEquals(interpolate("'", s -> ""), "'");
    }
}
