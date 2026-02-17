package com.unciv.utils;

import org.teavm.jso.JSBody;

final class WebTimeInterop {
    private WebTimeInterop() {
    }

    @JSBody(
            params = {"runnable", "delayMs"},
            script = "setTimeout(function(){ runnable.$run(); }, delayMs);")
    static native void schedule(Runnable runnable, int delayMs);
}
