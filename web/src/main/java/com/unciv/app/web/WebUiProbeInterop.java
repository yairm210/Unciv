package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebUiProbeInterop {
    private WebUiProbeInterop() {
    }

    static boolean isEnabled() {
        String raw = WebValidationInterop.getQueryValue("uiProbe");
        if (raw == null) return false;
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1")
                || normalized.equals("true")
                || normalized.equals("yes")
                || normalized.equals("on");
    }

    static String getRole() {
        return WebValidationInterop.getQueryValue("uiProbeRole");
    }

    static String getRunId() {
        return WebValidationInterop.getQueryValue("uiProbeRunId");
    }

    static String getTimeoutMs() {
        return WebValidationInterop.getQueryValue("uiProbeTimeoutMs");
    }

    static String getTestMultiplayerServerUrl() {
        return WebValidationInterop.getTestMultiplayerServerUrl();
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var value = window.__uncivWarPreloadPayload;"
                            + "return value == null ? null : String(value);")
    static native String getWarPreloadPayload();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var value = window.__uncivWarPreloadMetaJson;"
                            + "return value == null ? null : String(value);")
    static native String getWarPreloadMetaJson();

    @JSBody(
            params = "state",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivUiProbeState = state;")
    static native void publishState(String state);

    @JSBody(
            params = "message",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivUiProbeError = message;"
                            + "window.__uncivUiProbeState = 'failed';")
    static native void publishError(String message);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivUiProbeResultJson = json;"
                            + "window.__uncivUiProbeState = 'done';")
    static native void publishResult(String json);

    @JSBody(
            params = "json",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivUiProbeStepLogJson = json;")
    static native void publishStepLog(String json);

    @JSBody(
            params = {"runnable", "delayMs"},
            script = "setTimeout(function(){ runnable.$run(); }, delayMs);")
    static native void schedule(Runnable runnable, int delayMs);
}
