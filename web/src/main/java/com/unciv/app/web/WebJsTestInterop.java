package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebJsTestInterop {
    private WebJsTestInterop() {
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return false;"
                            + "if (window.__uncivEnableJsTests === true) return true;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "return search.indexOf('jstests=1') !== -1;")
    static native boolean isEnabled();

    @JSBody(
            params = "state",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivJsTestsState = state;")
    static native void publishState(String state);

    @JSBody(
            params = "error",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivJsTestsError = error;"
                            + "window.__uncivJsTestsDone = true;"
                            + "window.__uncivJsTestsState = 'failed';")
    static native void publishError(String error);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivJsTestsResultJson = json;"
                            + "window.__uncivJsTestsDone = true;"
                            + "window.__uncivJsTestsState = 'done';")
    static native void publishResult(String json);
}
