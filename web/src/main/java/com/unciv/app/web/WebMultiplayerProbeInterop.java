package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebMultiplayerProbeInterop {
    private WebMultiplayerProbeInterop() {
    }

    static boolean isEnabled() {
        String raw = getQueryValue("mpProbe");
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }

    static String getRole() {
        return getQueryValue("mpRole");
    }

    static String getGameId() {
        return getQueryValue("mpGameId");
    }

    static String getTimeoutMs() {
        return getQueryValue("mpTimeoutMs");
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

    @JSBody(
            params = "key",
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "var params = null;"
                            + "try { params = new URLSearchParams(search); } catch (e) { params = null; }"
                            + "if (!params) return null;"
                            + "var value = params.get(String(key));"
                            + "return value == null ? null : value;")
    static native String getQueryValue(String key);

    @JSBody(
            params = {"runnable", "delayMs"},
            script = "setTimeout(function(){ runnable.$run(); }, delayMs);")
    static native void schedule(Runnable runnable, int delayMs);
}
