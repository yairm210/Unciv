package org.slf4j.impl;

import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

final class WebSlf4jLogger extends MarkerIgnoringBase {
    private static final long serialVersionUID = 1L;

    WebSlf4jLogger(String name) {
        this.name = name;
    }

    private static String line(String level, String loggerName, String message) {
        return "[SLF4J][" + level + "][" + loggerName + "] " + message;
    }

    private void emit(String level, String message, Throwable throwable) {
        String output = line(level, name, message);
        if (throwable != null) {
            output = output + " | " + throwable;
        }
        if ("ERROR".equals(level)) {
            WebConsoleBridge.error(output);
            return;
        }
        if ("WARN".equals(level)) {
            WebConsoleBridge.warn(output);
            return;
        }
        WebConsoleBridge.log(output);
    }

    private void emitFormatted(String level, String format, Object arg) {
        var tuple = MessageFormatter.format(format, arg);
        emit(level, tuple.getMessage(), tuple.getThrowable());
    }

    private void emitFormatted(String level, String format, Object arg1, Object arg2) {
        var tuple = MessageFormatter.format(format, arg1, arg2);
        emit(level, tuple.getMessage(), tuple.getThrowable());
    }

    private void emitFormatted(String level, String format, Object... arguments) {
        var tuple = MessageFormatter.arrayFormat(format, arguments);
        emit(level, tuple.getMessage(), tuple.getThrowable());
    }

    @Override
    public boolean isTraceEnabled() {
        return false;
    }

    @Override
    public void trace(String msg) {
    }

    @Override
    public void trace(String format, Object arg) {
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
    }

    @Override
    public void trace(String format, Object... arguments) {
    }

    @Override
    public void trace(String msg, Throwable t) {
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public void debug(String msg) {
        emit("DEBUG", msg, null);
    }

    @Override
    public void debug(String format, Object arg) {
        emitFormatted("DEBUG", format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        emitFormatted("DEBUG", format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
        emitFormatted("DEBUG", format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
        emit("DEBUG", msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        emit("INFO", msg, null);
    }

    @Override
    public void info(String format, Object arg) {
        emitFormatted("INFO", format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        emitFormatted("INFO", format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
        emitFormatted("INFO", format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
        emit("INFO", msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        emit("WARN", msg, null);
    }

    @Override
    public void warn(String format, Object arg) {
        emitFormatted("WARN", format, arg);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        emitFormatted("WARN", format, arg1, arg2);
    }

    @Override
    public void warn(String format, Object... arguments) {
        emitFormatted("WARN", format, arguments);
    }

    @Override
    public void warn(String msg, Throwable t) {
        emit("WARN", msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public void error(String msg) {
        emit("ERROR", msg, null);
    }

    @Override
    public void error(String format, Object arg) {
        emitFormatted("ERROR", format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        emitFormatted("ERROR", format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
        emitFormatted("ERROR", format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
        emit("ERROR", msg, t);
    }
}
