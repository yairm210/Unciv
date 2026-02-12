package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebMultiplayerProbeInterop {
    private WebMultiplayerProbeInterop() {
    }

    static boolean isEnabled() {
        String raw = WebRuntimeInterop.getRuntimeConfigValue("mpProbe");
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }

    static String getRole() {
        return WebRuntimeInterop.getRuntimeConfigValue("mpRole");
    }

    static String getGameId() {
        return WebRuntimeInterop.getRuntimeConfigValue("mpGameId");
    }

    static String getTimeoutMs() {
        return WebRuntimeInterop.getRuntimeConfigValue("mpTimeoutMs");
    }

    static String getTestMultiplayerServerUrl() {
        return WebValidationInterop.getTestMultiplayerServerUrl();
    }

    @JSBody(
            params = "state",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivMpProbeState = state;")
    static native void publishState(String state);

    @JSBody(
            params = "message",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivMpProbeError = message;"
                            + "window.__uncivMpProbeState = 'failed';")
    static native void publishError(String message);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivMpProbeResultJson = json;"
                            + "window.__uncivMpProbeState = 'done';")
    static native void publishResult(String json);
}
