package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebValidationInterop {
    private WebValidationInterop() {
    }

    @JSBody(
            params = "key",
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "if (!search) return null;"
                            + "var query = search.charAt(0) === '?' ? search.substring(1) : search;"
                            + "if (!query) return null;"
                            + "var wanted = String(key);"
                            + "var parts = query.split('&');"
                            + "for (var i = 0; i < parts.length; i++) {"
                            + "  var part = parts[i];"
                            + "  if (!part) continue;"
                            + "  var eq = part.indexOf('=');"
                            + "  var rawKey = eq >= 0 ? part.substring(0, eq) : part;"
                            + "  var rawValue = eq >= 0 ? part.substring(eq + 1) : '';"
                            + "  var parsedKey = rawKey;"
                            + "  var parsedValue = rawValue;"
                            + "  try {"
                            + "    parsedKey = decodeURIComponent(rawKey.replace(/\\+/g, ' '));"
                            + "  } catch (e) {"
                            + "    parsedKey = rawKey;"
                            + "  }"
                            + "  try {"
                            + "    parsedValue = decodeURIComponent(rawValue.replace(/\\+/g, ' '));"
                            + "  } catch (e) {"
                            + "    parsedValue = rawValue;"
                            + "  }"
                            + "  if (parsedKey === wanted) return parsedValue;"
                            + "}"
                            + "return null;")
    static native String getQueryValue(String key);

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return false;"
                            + "if (window.__uncivEnableWebValidation === true) return true;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "return search.indexOf('webtest=1') !== -1;")
    static native boolean isValidationEnabled();

    static String getTestMultiplayerServerUrl() {
        String override = getTestMultiplayerServerOverride();
        if (override != null && !override.trim().isEmpty()) return override;
        return getQueryValue("mpServer");
    }

    static String getTestModZipUrl() {
        String override = getTestModZipUrlOverride();
        if (override != null && !override.trim().isEmpty()) return override;
        return getQueryValue("modZip");
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "return window.__uncivTestMultiplayerServer || null;")
    private static native String getTestMultiplayerServerOverride();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "return window.__uncivTestModZipUrl || null;")
    private static native String getTestModZipUrlOverride();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "if (window.__uncivBaseUrl) return window.__uncivBaseUrl;"
                            + "if (window.location && window.location.origin) return window.location.origin;"
                            + "return null;")
    static native String getBaseUrl();

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivTestFileStore = { enabled: true, files: {}, binary: {}, lastName: '', lastBinaryName: '' };")
    static native void enableTestFileStore();

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
            params = {"runner", "reason"},
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivRunnerSelected = runner;"
                            + "window.__uncivRunnerReason = reason;")
    static native void publishRunnerSelection(String runner, String reason);

    @JSBody(
            params = {"step", "detail"},
            script =
                    "if (typeof window === 'undefined') return;"
                            + "if (!window.__uncivBootstrapTrace) window.__uncivBootstrapTrace = [];"
                            + "window.__uncivBootstrapTrace.push({"
                            + "  atMs: Date.now(),"
                            + "  step: step,"
                            + "  detail: detail"
                            + "});"
                            + "if (window.__uncivBootstrapTrace.length > 80) {"
                            + "  window.__uncivBootstrapTrace = window.__uncivBootstrapTrace.slice(window.__uncivBootstrapTrace.length - 80);"
                            + "}"
                            + "window.__uncivBootstrapTraceJson = JSON.stringify({ steps: window.__uncivBootstrapTrace });")
    static native void appendBootstrapTrace(String step, String detail);

    @JSBody(
            params = "marker",
            script =
                    "if (typeof window === 'undefined') return;"
                            + "window.__uncivBootProgressMarker = marker;")
    static native void publishBootProgress(String marker);

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
