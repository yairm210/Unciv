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

    @JSBody(
            params = "key",
            script =
                    "if (typeof window === 'undefined') return null;"
                            + "var cfg = window.__uncivRuntimeConfig;"
                            + "if (cfg && Object.prototype.hasOwnProperty.call(cfg, key)) {"
                            + "  var value = cfg[key];"
                            + "  return value == null ? null : String(value);"
                            + "}"
                            + "if (Object.prototype.hasOwnProperty.call(window, key)) {"
                            + "  var direct = window[key];"
                            + "  return direct == null ? null : String(direct);"
                            + "}"
                            + "var params = null;"
                            + "try { params = new URLSearchParams((window.location && window.location.search) || ''); } catch (e) { params = null; }"
                            + "if (!params) return null;"
                            + "var queryValue = params.get(key);"
                            + "return queryValue == null ? null : queryValue;")
    static native String getRuntimeConfigValue(String key);
}
