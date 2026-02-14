package org.slf4j.impl;

import org.teavm.jso.JSBody;

final class WebConsoleBridge {
    private WebConsoleBridge() {
    }

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.log(message);")
    static native void log(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.warn(message);")
    static native void warn(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.error(message);")
    static native void error(String message);
}
