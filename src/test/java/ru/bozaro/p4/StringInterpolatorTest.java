package ru.bozaro.p4;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static ru.bozaro.p4.proto.StringInterpolator.interpolate;

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
        assertEquals(interpolate("%foo%", s -> "foo" .equals(s) ? "bar" : ""), "bar");
    }

    @Test
    public void arg2() {
        assertEquals(interpolate("User %user% logged in.", s -> "user" .equals(s) ? "OldJohn" : ""), "User OldJohn logged in.");
    }

    @Test
    public void emptyArgName() {
        assertEquals(interpolate("%%", s -> ""), "%");
    }

    @Test
    public void unmatched() {
        assertEquals(interpolate("%", s -> ""), "%");
    }

    @Test
    public void unmatched2() {
        assertEquals(interpolate("%aa", s -> ""), "%aa");
    }

    @Test
    public void escaped() {
        assertEquals(interpolate("%'foo'%", s -> ""), "foo");
    }

    @Test
    public void escapedEmpty() {
        assertEquals(interpolate("%''%", s -> ""), "");
    }

    @Test
    public void escapedInvalid() {
        assertEquals(interpolate("%'%", s -> ""), "%'%");
    }

    @Test
    public void escapedJunk() {
        assertEquals(interpolate("%'junk", s -> ""), "%'junk");
    }

    @Test
    public void quote() {
        assertEquals(interpolate("'", s -> ""), "'");
    }

    @Test
    public void alternateJunk() {
        assertEquals(interpolate("[foo", s -> ""), "[foo");
    }

    @Test
    public void alternateSingleFalse() {
        assertEquals(interpolate("[%foo%]", s -> ""), "");
    }

    @Test
    public void alternateSingleTrue() {
        assertEquals(interpolate("[%foo%]", s -> "bar"), "bar");
    }

    @Test
    public void alternateTrue() {
        assertEquals(interpolate("[%argc% - file(s)|File(s)] not opened on this client.", s -> ""), "File(s) not opened on this client.");
    }

    @Test
    public void alternateFalse() {
        assertEquals(interpolate("[%argc% - file(s)|File(s)] not opened on this client.", s -> "qwe"), "qwe - file(s) not opened on this client.");
    }
}
