package com.unciv.app.web

import org.teavm.jso.JSBody
import org.teavm.jso.JSObject

/** Canvas-backed glyph rasterization for browser fonts. */
object WebFontRasterizer {
    @JvmStatic
    @JSBody(params = ["bytes", "familyName"], script = """
        const root = typeof window !== 'undefined' ? window : globalThis;
        if (!root.document || typeof root.FontFace !== 'function')
            throw new Error('Custom font loading requires the CSS Font Loading API');
        const face = new root.FontFace(familyName, new Uint8Array(bytes).buffer);
        if (face.status !== 'loaded') {
            // Consume the rejected loaded promise for invalid binary data.
            face.load().catch(() => {});
            throw new Error('Browser did not synchronously load the supplied font data');
        }
        root.document.fonts.add(face);
        return face;
    """)
    external fun registerFont(bytes: ByteArray, familyName: String): JSObject

    @JvmStatic
    @JSBody(params = ["face"], script = """
        const root = typeof window !== 'undefined' ? window : globalThis;
        if (root.document && root.document.fonts) root.document.fonts.delete(face);
    """)
    external fun removeFont(face: JSObject)

    /**
     * Measures a shared reference box for glyph alignment. Fallback fonts and combining marks
     * can require a larger bitmap than the mixed-script sample predicts.
     */
    @JvmStatic
    @JSBody(
        params = ["fontSize", "fontFamily"],
        script = """
            const root = typeof window !== 'undefined' ? window : globalThis;
            if (!root.document) throw new Error('Canvas font rendering requires a browser document');
            if (!root.__uncivFontCanvas) root.__uncivFontCanvas = root.document.createElement('canvas');
            const ctx = root.__uncivFontCanvas.getContext('2d', { willReadFrequently: true });
            if (!ctx) throw new Error('Could not create a 2D canvas context for font metrics');
            ctx.font = fontSize + 'px ' + fontFamily;
            const m = ctx.measureText('Hgjpqy ÅÖÉéß Ĥắçİ ΩЖ עברית العربية 中あア한 😀🎉');
            const rawAscent = Number.isFinite(m.fontBoundingBoxAscent)
                ? Math.max(m.fontBoundingBoxAscent, m.actualBoundingBoxAscent)
                : m.actualBoundingBoxAscent;
            const rawDescent = Number.isFinite(m.fontBoundingBoxDescent)
                ? Math.max(m.fontBoundingBoxDescent, m.actualBoundingBoxDescent)
                : m.actualBoundingBoxDescent;
            if (!Number.isFinite(rawAscent) || !Number.isFinite(rawDescent) || rawAscent + rawDescent <= 0)
                throw new Error('Browser returned invalid font metrics');
            const ascent = Math.ceil(rawAscent);
            const descent = Math.ceil(rawDescent);
            const height = ascent + descent;
            const leading = 0;
            return [ascent, descent, height, leading];
        """
    )
    external fun measureMetrics(fontSize: Int, fontFamily: String): FloatArray

    /**
     * Expands the bitmap to contain the glyph while preserving the shared baseline.
     * Returns width, height, yOffset, then RGBA bytes. A negative yOffset moves the bitmap
     * bottom down in BitmapFont coordinates to accommodate ink below the shared box.
     */
    @JvmStatic
    @JSBody(
        params = ["text", "fontSize", "fontFamily", "ascent", "height", "leading"],
        script = """
            const root = typeof window !== 'undefined' ? window : globalThis;
            if (!root.document) throw new Error('Canvas glyph rendering requires a browser document');
            if (!root.__uncivFontCanvas) root.__uncivFontCanvas = root.document.createElement('canvas');
            const canvas = root.__uncivFontCanvas;
            let ctx = canvas.getContext('2d', { willReadFrequently: true });
            if (!ctx) throw new Error('Could not create a 2D canvas context for glyph rendering');
            if (text == null || text.length === 0) text = ' ';
            ctx.font = fontSize + 'px ' + fontFamily;
            ctx.textAlign = 'left';
            ctx.textBaseline = 'alphabetic';
            const m = ctx.measureText(text);
            const bounds = [m.width, m.actualBoundingBoxLeft, m.actualBoundingBoxRight,
                m.actualBoundingBoxAscent, m.actualBoundingBoxDescent];
            if (!bounds.every(Number.isFinite)) throw new Error('Browser returned invalid glyph bounds');
            const left = Math.min(0, -m.actualBoundingBoxLeft);
            const right = Math.max(m.width, m.actualBoundingBoxRight);
            const width = Math.max(1, Math.ceil(right - left));
            const sharedBaseline = Math.ceil(ascent + leading);
            const sharedDescent = Math.max(1, Math.ceil(height)) - sharedBaseline;
            const baseline = Math.max(sharedBaseline, Math.ceil(m.actualBoundingBoxAscent));
            const descent = Math.max(sharedDescent, Math.ceil(m.actualBoundingBoxDescent));
            height = baseline + descent;
            // BitmapFont draws from the bottom. Extend downwards without moving the baseline.
            const yOffset = sharedDescent - descent;
            canvas.width = width;
            canvas.height = height;
            ctx = canvas.getContext('2d', { willReadFrequently: true });
            if (!ctx) throw new Error('Could not restore a 2D canvas context for glyph rendering');
            ctx.font = fontSize + 'px ' + fontFamily;
            ctx.textAlign = 'left';
            ctx.textBaseline = 'alphabetic';
            ctx.fillStyle = 'rgba(255,255,255,1)';
            ctx.clearRect(0, 0, width, height);
            ctx.fillText(text, -left, baseline);
            const data = ctx.getImageData(0, 0, width, height).data;
            const out = new Array(3 + data.length);
            out[0] = width;
            out[1] = height;
            out[2] = yOffset;
            for (let i = 0; i < data.length; i++) out[3 + i] = data[i];
            return out;
        """
    )
    external fun rasterizeGlyphAligned(
        text: String,
        fontSize: Int,
        fontFamily: String,
        ascent: Float,
        height: Float,
        leading: Float,
    ): IntArray
}
