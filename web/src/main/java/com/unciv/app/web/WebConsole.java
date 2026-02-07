package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebConsole {
    private WebConsole() {
    }

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.log(message);")
    static native void log(String message);

    @JSBody(params = "message", script = "if (typeof console !== 'undefined') console.error(message);")
    static native void error(String message);
}
