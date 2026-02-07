package com.unciv.app.web;

import org.teavm.jso.JSBody;

public final class WebFontRasterizer {
    private WebFontRasterizer() {
    }

    @JSBody(
            params = {"fontSize", "fontFamily"},
            script =
                    "var root = typeof window !== 'undefined' ? window : globalThis;"
                            + "if (!root.__uncivFontCanvas) {"
                            + "  root.__uncivFontCanvas = root.document.createElement('canvas');"
                            + "  root.__uncivFontCtx = root.__uncivFontCanvas.getContext('2d', { willReadFrequently: true });"
                            + "}"
                            + "var ctx = root.__uncivFontCtx;"
                            + "ctx.font = fontSize + 'px ' + fontFamily;"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "var m = ctx.measureText('Mg');"
                            + "var ascent = m.actualBoundingBoxAscent || (fontSize * 0.8);"
                            + "var descent = m.actualBoundingBoxDescent || (fontSize * 0.2);"
                            + "var height = Math.max(1, Math.ceil(ascent + descent + 2));"
                            + "var leading = Math.max(0, height - (ascent + descent));"
                            + "return [ascent, descent, height, leading];")
    public static native float[] measureMetrics(int fontSize, String fontFamily);

    @JSBody(
            params = {"text", "fontSize", "fontFamily"},
            script =
                    "var root = typeof window !== 'undefined' ? window : globalThis;"
                            + "if (!root.__uncivFontCanvas) {"
                            + "  root.__uncivFontCanvas = root.document.createElement('canvas');"
                            + "  root.__uncivFontCtx = root.__uncivFontCanvas.getContext('2d', { willReadFrequently: true });"
                            + "}"
                            + "if (text == null || text.length === 0) text = ' ';"
                            + "var canvas = root.__uncivFontCanvas;"
                            + "var ctx = root.__uncivFontCtx;"
                            + "ctx.font = fontSize + 'px ' + fontFamily;"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "var m = ctx.measureText(text);"
                            + "var ascent = m.actualBoundingBoxAscent || (fontSize * 0.8);"
                            + "var descent = m.actualBoundingBoxDescent || (fontSize * 0.2);"
                            + "var width = Math.max(1, Math.ceil(m.width));"
                            + "var height = Math.max(1, Math.ceil(ascent + descent + 2));"
                            + "canvas.width = width;"
                            + "canvas.height = height;"
                            + "ctx = canvas.getContext('2d', { willReadFrequently: true });"
                            + "root.__uncivFontCtx = ctx;"
                            + "ctx.font = fontSize + 'px ' + fontFamily;"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "ctx.fillStyle = 'rgba(255,255,255,1)';"
                            + "ctx.clearRect(0, 0, width, height);"
                            + "var baseline = Math.ceil(ascent) + 1;"
                            + "ctx.fillText(text, 0, baseline);"
                            + "var data = ctx.getImageData(0, 0, width, height).data;"
                            + "var out = new Array(3 + data.length);"
                            + "out[0] = width;"
                            + "out[1] = height;"
                            + "out[2] = baseline;"
                            + "for (var i = 0; i < data.length; i++) out[3 + i] = data[i];"
                            + "return out;")
    public static native int[] rasterizeGlyph(String text, int fontSize, String fontFamily);
}
