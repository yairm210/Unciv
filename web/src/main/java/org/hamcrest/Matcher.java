package org.hamcrest;

public interface Matcher<T> {
    boolean matches(T actual);
    String describe();
}
