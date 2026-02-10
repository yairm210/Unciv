package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebValidationInterop {
    private WebValidationInterop() {
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return false;"
                            + "if (window.__uncivEnableWebValidation === true) return true;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "return search.indexOf('webtest=1') !== -1;")
    static native boolean isValidationEnabled();

    @JSBody(
            params = "state",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivWebValidationState = state;")
    static native void publishState(String state);

    @JSBody(
            params = "message",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivWebValidationError = message;")
    static native void publishError(String message);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivWebValidationResultJson = json;"
                            + "window.__uncivWebValidationDone = true;"
                            + "window.__uncivWebValidationState = 'done';")
    static native void publishResult(String json);

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return;"
                            + "if (window.__uncivOpenSpyInstalled) return;"
                            + "window.__uncivOpenSpyInstalled = true;"
                            + "window.__uncivOpenCount = 0;"
                            + "window.__uncivOriginalOpen = window.open;"
                            + "window.open = function() {"
                            + "  window.__uncivOpenCount = (window.__uncivOpenCount || 0) + 1;"
                            + "  return null;"
                            + "};")
    static native void installExternalLinkSpy();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return 0;"
                            + "return window.__uncivOpenCount || 0;")
    static native int getExternalLinkOpenCount();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return;"
                            + "if (!window.__uncivOpenSpyInstalled) return;"
                            + "window.open = window.__uncivOriginalOpen;"
                            + "window.__uncivOriginalOpen = undefined;"
                            + "window.__uncivOpenSpyInstalled = false;")
    static native void restoreExternalLinkSpy();

    @JSBody(
            params = {"runnable", "delayMs"},
            script = "setTimeout(function(){ runnable.$run(); }, delayMs);")
    static native void schedule(Runnable runnable, int delayMs);
}
