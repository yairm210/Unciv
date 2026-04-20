package org.hamcrest;

public final class MatcherAssert {
    private MatcherAssert() {}

    public static <T> void assertThat(T actual, Matcher<? super T> matcher) {
        if (!matcher.matches(actual)) {
            throw new AssertionError("Expected " + matcher.describe() + " but was " + actual);
        }
    }
}
