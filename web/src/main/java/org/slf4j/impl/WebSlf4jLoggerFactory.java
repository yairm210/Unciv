package org.slf4j.impl;

import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

final class WebSlf4jLoggerFactory implements ILoggerFactory {
    private final ConcurrentHashMap<String, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, WebSlf4jLogger::new);
    }
}
