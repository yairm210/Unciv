package com.unciv.app.web;

import org.teavm.jso.JSBody;

final class WebRuntimeInterop {
    private WebRuntimeInterop() {
    }

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "if (typeof window.__uncivWebProfile === 'string' && window.__uncivWebProfile.length > 0) return window.__uncivWebProfile;"
                            + "var search = (window.location && window.location.search) || '';"
                            + "var match = /(?:\\?|&)webProfile=([^&]+)/i.exec(search);"
                            + "if (!match) return null;"
                            + "try { return decodeURIComponent(match[1]); } catch (e) { return match[1]; }")
    static native String getWebProfile();
}
