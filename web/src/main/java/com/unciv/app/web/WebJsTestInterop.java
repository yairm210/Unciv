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
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "if (typeof window.__uncivJsTestsFilter === 'string' && window.__uncivJsTestsFilter.length > 0) return window.__uncivJsTestsFilter;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "var match = /(?:\\?|&)jstestsFilter=([^&]+)/.exec(search);"
                            + "if (!match) return null;"
                            + "try { return decodeURIComponent(match[1]); } catch (e) { return match[1]; }")
    static native String getClassFilter();

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
