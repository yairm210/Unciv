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
                            + "if (ctx.fontKerning !== undefined) ctx.fontKerning = 'none';"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "var m = ctx.measureText('Hgjpqy');"
                            + "var ascent = m.fontBoundingBoxAscent || m.actualBoundingBoxAscent || (fontSize * 0.8);"
                            + "var descent = m.fontBoundingBoxDescent || m.actualBoundingBoxDescent || (fontSize * 0.2);"
                            + "var height = Math.max(1, Math.ceil(ascent + descent));"
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
                            + "var height = Math.max(1, Math.ceil(ascent + descent));"
                            + "canvas.width = width;"
                            + "canvas.height = height;"
                            + "ctx = canvas.getContext('2d', { willReadFrequently: true });"
                            + "root.__uncivFontCtx = ctx;"
                            + "ctx.font = fontSize + 'px ' + fontFamily;"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "ctx.fillStyle = 'rgba(255,255,255,1)';"
                            + "ctx.clearRect(0, 0, width, height);"
                            + "var baseline = Math.ceil(ascent);"
                            + "ctx.fillText(text, 0, baseline);"
                            + "var data = ctx.getImageData(0, 0, width, height).data;"
                            + "var out = new Array(3 + data.length);"
                            + "out[0] = width;"
                            + "out[1] = height;"
                            + "out[2] = baseline;"
                            + "for (var i = 0; i < data.length; i++) out[3 + i] = data[i];"
                            + "return out;")
    public static native int[] rasterizeGlyph(String text, int fontSize, String fontFamily);

    @JSBody(
            params = {"text", "fontSize", "fontFamily", "ascent", "descent", "height", "leading"},
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
                            + "var measuredWidth = m.width;"
                            + "var width = Math.max(1, Math.ceil(measuredWidth));"
                            + "if (measuredWidth <= 0) width = Math.max(width, Math.ceil(height));"
                            + "var baseline = Math.ceil(Math.max(1, ascent + Math.max(0, leading)));"
                            + "if (baseline < 1) baseline = 1;"
                            + "var fixedHeight = Math.max(1, Math.ceil(height));"
                            + "if (baseline > fixedHeight - 1) fixedHeight = baseline + 1;"
                            + "canvas.width = width;"
                            + "canvas.height = fixedHeight;"
                            + "ctx = canvas.getContext('2d', { willReadFrequently: true });"
                            + "root.__uncivFontCtx = ctx;"
                            + "ctx.font = fontSize + 'px ' + fontFamily;"
                            + "if (ctx.fontKerning !== undefined) ctx.fontKerning = 'none';"
                            + "ctx.textAlign = 'left';"
                            + "ctx.textBaseline = 'alphabetic';"
                            + "ctx.fillStyle = 'rgba(255,255,255,1)';"
                            + "ctx.clearRect(0, 0, width, fixedHeight);"
                            + "ctx.fillText(text, 0, baseline);"
                            + "var data = ctx.getImageData(0, 0, width, fixedHeight).data;"
                            + "var out = new Array(2 + data.length);"
                            + "out[0] = width;"
                            + "out[1] = fixedHeight;"
                            + "for (var i = 0; i < data.length; i++) out[2 + i] = data[i];"
                            + "return out;")
    public static native int[] rasterizeGlyphAligned(
            String text,
            int fontSize,
            String fontFamily,
            float ascent,
            float descent,
            float height,
            float leading
    );
}
