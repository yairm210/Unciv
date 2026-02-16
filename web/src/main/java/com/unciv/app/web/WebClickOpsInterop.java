package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebClickOpsInterop {
    private WebClickOpsInterop() {
    }

    static boolean isEnabled() {
        String raw = WebValidationInterop.getQueryValue("clickOps");
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1");
    }

    static String getRole() {
        return WebValidationInterop.getQueryValue("clickOpsRole");
    }

    static String getTestMultiplayerServerUrl() {
        return WebValidationInterop.getTestMultiplayerServerUrl();
    }

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivClickOpsTargetsJson = json;")
    static native void publishTargets(String json);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivClickOpsStateJson = json;")
    static native void publishState(String json);

    @JSBody(
            params = "message",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivClickOpsError = message;")
    static native void publishError(String message);
}
