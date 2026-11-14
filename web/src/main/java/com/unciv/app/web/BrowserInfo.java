package com.unciv.app.web;

import org.teavm.jso.JSBody;

public final class BrowserInfo {
    private BrowserInfo() {}

    @JSBody(params = {"msg"}, script = "console.log(msg);")
    public static native void consoleLog(String msg);

    @JSBody(params = {"msg"}, script = "console.error(msg);")
    public static native void consoleError(String msg);

    /** True unless served from localhost or the page URL has a "debug" query parameter. */
    @JSBody(script =
        "var h = location.hostname;" +
            "var dev = h === 'localhost' || h === '127.0.0.1' || h === '[::1]'" +
            "  || /[?&]debug\\b/.test(location.search);" +
            "return !dev;")
    public static native boolean isRelease();

    @JSBody(script =
        "var out = [];" +
            "function add(k, v) { out.push(k + ': ' + v); }" +
            "add('User agent', navigator.userAgent);" +
            "add('Languages', (navigator.languages || [navigator.language]).join(', '));" +
            "add('Logical CPUs', navigator.hardwareConcurrency);" +
            "add('Screen', screen.width + 'x' + screen.height);" +
            "add('Window', innerWidth + 'x' + innerHeight);" +
            "add('Pixel ratio', devicePixelRatio);" +
            "add('Secure context', window.isSecureContext);" +
            "add('Cross-origin isolated', window.crossOriginIsolated);" +
            "add('WebAssembly', typeof WebAssembly !== 'undefined');" +
            "var canvas = document.createElement('canvas');" +
            "var gl = canvas.getContext('webgl2'), ver = 'WebGL2';" +
            "if (!gl) { gl = canvas.getContext('webgl'); ver = 'WebGL1'; }" +
            "if (!gl) { add('WebGL', 'unavailable'); } else {" +
            "  add('WebGL', ver + ' / ' + gl.getParameter(gl.VERSION));" +
            "  add('GLSL', gl.getParameter(gl.SHADING_LANGUAGE_VERSION));" +
            "  var ext = gl.getExtension('WEBGL_debug_renderer_info');" +
            "  add('GPU', ext" +
            "    ? gl.getParameter(ext.UNMASKED_VENDOR_WEBGL) + ' / ' + gl.getParameter(ext.UNMASKED_RENDERER_WEBGL)" +
            "    : gl.getParameter(gl.VENDOR) + ' / ' + gl.getParameter(gl.RENDERER) + ' (masked)');" +
            "  add('Max texture size', gl.getParameter(gl.MAX_TEXTURE_SIZE));" +
            "  add('Max renderbuffer size', gl.getParameter(gl.MAX_RENDERBUFFER_SIZE));" +
            "  var vp = gl.getParameter(gl.MAX_VIEWPORT_DIMS);" +
            "  add('Max viewport', vp[0] + 'x' + vp[1]);" +
            "  var lose = gl.getExtension('WEBGL_lose_context'); if (lose) lose.loseContext();" +
            "}" +
            "return out.join('\\n');")
    public static native String systemInfo();

    /** Cancels the browser's own Ctrl+letter shortcuts (open, save, print, find...) so only the game sees them.
     *  @param allowed letters to leave to the browser, e.g. "cvx" */
    @JSBody(params = {"allowed"}, script =
        "window.addEventListener('keydown', function (e) {" +
            "  if (!e.ctrlKey || e.altKey || e.shiftKey || e.metaKey) return;" +
            "  var k = (e.key && e.key.length === 1 && /[a-z]/i.test(e.key))" +
            "    ? e.key.toLowerCase()" +
            "    : (/^Key[A-Z]$/.test(e.code) ? e.code.charAt(3).toLowerCase() : '');" +
            "  if (k === '' || allowed.indexOf(k) >= 0) return;" +
            "  e.preventDefault();" +
            "}, true);")
    public static native void blockBrowserCtrlShortcuts(String allowed);
}
