package junit.framework;

import org.junit.Assert;

public class TestCase {
    public static void assertEquals(Object expected, Object actual) {
        Assert.assertEquals(expected, actual);
    }

    public static void assertEquals(String message, Object expected, Object actual) {
        Assert.assertEquals(message, expected, actual);
    }

    public static void assertEquals(double expected, double actual, double delta) {
        Assert.assertEquals(expected, actual, delta);
    }

    public static void assertEquals(String message, double expected, double actual, double delta) {
        Assert.assertEquals(message, expected, actual, delta);
    }

    public static void assertEquals(float expected, float actual, float delta) {
        Assert.assertEquals(expected, actual, delta);
    }

    public static void assertEquals(String message, float expected, float actual, float delta) {
        Assert.assertEquals(message, expected, actual, delta);
    }

    public static void assertTrue(boolean condition) {
        Assert.assertTrue(condition);
    }

    public static void assertTrue(String message, boolean condition) {
        Assert.assertTrue(message, condition);
    }

    public static void assertFalse(boolean condition) {
        Assert.assertFalse(condition);
    }

    public static void assertFalse(String message, boolean condition) {
        Assert.assertFalse(message, condition);
    }

    public static void assertNull(Object value) {
        Assert.assertNull(value);
    }

    public static void assertNotNull(Object value) {
        Assert.assertNotNull(value);
    }

    public static void fail() {
        Assert.fail();
    }

    public static void fail(String message) {
        Assert.fail(message);
    }
}
