package org.junit;

import java.util.Arrays;
import java.util.Objects;

public final class Assert {
    private Assert() {
    }

    public static void assertTrue(boolean condition) {
        if (!condition) fail("expected true");
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) fail(message != null ? message : "expected true");
    }

    public static void assertFalse(boolean condition) {
        if (condition) fail("expected false");
    }

    public static void assertFalse(String message, boolean condition) {
        if (condition) fail(message != null ? message : "expected false");
    }

    public static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            fail((message != null ? message + " " : "") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        if (Math.abs(expected - actual) > delta) {
            fail((message != null ? message + " " : "") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertEquals(float expected, float actual, float delta) {
        if (Math.abs(expected - actual) > delta) fail("expected:<" + expected + "> but was:<" + actual + ">");
    }

    public static void assertEquals(String message, float expected, float actual, float delta) {
        if (Math.abs(expected - actual) > delta) {
            fail((message != null ? message + " " : "") + "expected:<" + expected + "> but was:<" + actual + ">");
        }
    }

    public static void assertNotEquals(Object expected, Object actual) {
        if (Objects.equals(expected, actual)) fail("expected not equals to:<" + expected + ">");
    }

    public static void assertNotEquals(String message, Object expected, Object actual) {
        if (Objects.equals(expected, actual)) {
            fail((message != null ? message + " " : "") + "expected not equals to:<" + expected + ">");
        }
    }

    public static void assertNull(Object value) {
        if (value != null) fail("expected null but was:<" + value + ">");
    }

    public static void assertNull(String message, Object value) {
        if (value != null) fail((message != null ? message + " " : "") + "expected null but was:<" + value + ">");
    }

    public static void assertNotNull(Object value) {
        if (value == null) fail("expected value to be non-null");
    }

    public static void assertNotNull(String message, Object value) {
        if (value == null) fail(message != null ? message : "expected value to be non-null");
    }

    public static void assertArrayEquals(Object[] expected, Object[] actual) {
        if (!Arrays.deepEquals(expected, actual)) {
            fail("expected:<" + Arrays.deepToString(expected) + "> but was:<" + Arrays.deepToString(actual) + ">");
        }
    }

    public static void assertArrayEquals(String message, Object[] expected, Object[] actual) {
        if (!Arrays.deepEquals(expected, actual)) {
            fail((message != null ? message + " " : "") +
                    "expected:<" + Arrays.deepToString(expected) + "> but was:<" + Arrays.deepToString(actual) + ">");
        }
    }

    public static void fail() {
        throw new AssertionError();
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
