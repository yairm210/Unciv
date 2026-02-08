package org.hamcrest;

import java.util.Objects;

public final class CoreMatchers {
    private CoreMatchers() {}

    public static <T> Matcher<T> is(T expected) {
        return new EqualsMatcher<>(expected);
    }

    private static final class EqualsMatcher<T> implements Matcher<T> {
        private final T expected;

        private EqualsMatcher(T expected) {
            this.expected = expected;
        }

        @Override
        public boolean matches(T actual) {
            return Objects.equals(expected, actual);
        }

        @Override
        public String describe() {
            return "is(" + expected + ")";
        }
    }
}
