package org.slf4j.impl;

import org.teavm.jso.JSBody;

final class WebConsoleBridge {
    private static final int BRIDGE_UNKNOWN = 0;
    private static final int BRIDGE_JS = 1;
    private static final int BRIDGE_JVM = 2;

    private static volatile int bridgeMode = BRIDGE_UNKNOWN;

    private WebConsoleBridge() {
    }

    private static void jvmLog(String message) {
        System.out.println(message);
    }

    private static void jvmWarn(String message) {
        System.err.println(message);
    }

    private static void jvmError(String message) {
        System.err.println(message);
    }

    private static boolean canUseJsBridge() {
        return bridgeMode != BRIDGE_JVM;
    }

    private static void markJsBridgeAvailable() {
        bridgeMode = BRIDGE_JS;
    }

    private static void markJvmFallback() {
        bridgeMode = BRIDGE_JVM;
    }

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.log(message);")
    private static native void logJs(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined' && console.debug) console.debug(message); else if (typeof console !== 'undefined') console.log(message);")
    private static native void debugJs(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.warn(message);")
    private static native void warnJs(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.error(message);")
    private static native void errorJs(String message);

    static void log(String message) {
        if (!canUseJsBridge()) {
            jvmLog(message);
            return;
        }
        try {
            logJs(message);
            markJsBridgeAvailable();
        } catch (LinkageError ignored) {
            markJvmFallback();
            jvmLog(message);
        }
    }

    static void debug(String message) {
        if (!canUseJsBridge()) {
            jvmLog(message);
            return;
        }
        try {
            debugJs(message);
            markJsBridgeAvailable();
        } catch (LinkageError ignored) {
            markJvmFallback();
            jvmLog(message);
        }
    }

    static void warn(String message) {
        if (!canUseJsBridge()) {
            jvmWarn(message);
            return;
        }
        try {
            warnJs(message);
            markJsBridgeAvailable();
        } catch (LinkageError ignored) {
            markJvmFallback();
            jvmWarn(message);
        }
    }

    static void error(String message) {
        if (!canUseJsBridge()) {
            jvmError(message);
            return;
        }
        try {
            errorJs(message);
            markJsBridgeAvailable();
        } catch (LinkageError ignored) {
            markJvmFallback();
            jvmError(message);
        }
    }
}
