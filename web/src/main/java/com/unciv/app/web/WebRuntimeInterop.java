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

    @JSBody(
            script =
                    "if (typeof window === 'undefined') return false;"
                            + "var forced = null;"
                            + "var cfg = window.__uncivRuntimeConfig;"
                            + "if (cfg && Object.prototype.hasOwnProperty.call(cfg, 'webMobile')) {"
                            + "  var cfgValue = cfg.webMobile;"
                            + "  forced = cfgValue == null ? null : String(cfgValue);"
                            + "} else if (Object.prototype.hasOwnProperty.call(window, 'webMobile')) {"
                            + "  var directValue = window.webMobile;"
                            + "  forced = directValue == null ? null : String(directValue);"
                            + "} else {"
                            + "  try {"
                            + "    var params = new URLSearchParams((window.location && window.location.search) || '');"
                            + "    var queryValue = params.get('webMobile');"
                            + "    forced = queryValue == null ? null : queryValue;"
                            + "  } catch (e) {"
                            + "    forced = null;"
                            + "  }"
                            + "}"
                            + "if (forced != null) {"
                            + "  var normalized = String(forced).trim().toLowerCase();"
                            + "  if (normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on') return true;"
                            + "  if (normalized === '0' || normalized === 'false' || normalized === 'no' || normalized === 'off') return false;"
                            + "}"
                            + "var nav = typeof navigator !== 'undefined' ? navigator : null;"
                            + "var ua = nav && typeof nav.userAgent === 'string' ? nav.userAgent : '';"
                            + "var coarse = false;"
                            + "var fine = false;"
                            + "if (typeof window.matchMedia === 'function') {"
                            + "  coarse = !!(window.matchMedia('(pointer: coarse)').matches || window.matchMedia('(any-pointer: coarse)').matches);"
                            + "  fine = !!(window.matchMedia('(pointer: fine)').matches || window.matchMedia('(any-pointer: fine)').matches);"
                            + "}"
                            + "var width = typeof window.innerWidth === 'number' ? window.innerWidth : 0;"
                            + "var height = typeof window.innerHeight === 'number' ? window.innerHeight : 0;"
                            + "var shortestEdge = Math.min(width || 0, height || 0);"
                            + "var touchPoints = nav && typeof nav.maxTouchPoints === 'number' ? nav.maxTouchPoints : 0;"
                            + "if (fine && shortestEdge >= 900) return false;"
                            + "if (coarse && (shortestEdge > 0 && shortestEdge <= 900)) return true;"
                            + "if (/(Android|iPhone|iPad|iPod|Mobile|Silk|Kindle|Opera Mini|IEMobile|Windows Phone)/i.test(ua)) return true;"
                            + "return coarse && touchPoints > 0;")
    static native boolean isLikelyMobileDevice();

    @JSBody(
            params = "mobile",
            script =
                    "if (typeof document === 'undefined') return;"
                            + "var root = document.documentElement;"
                            + "if (!root || !root.classList) return;"
                            + "root.classList.remove('unciv-web-mobile', 'unciv-web-desktop');"
                            + "root.classList.add(mobile ? 'unciv-web-mobile' : 'unciv-web-desktop');")
    static native void applyDeviceClass(boolean mobile);
}
