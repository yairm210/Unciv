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
            const m = ctx.measureText('Hgjpqy ÅÖÉéß Ĥắçİ ΩЖ עברית العربية 中あア한 😀🎉 ' +
                'हिन्दी ऑ क्षि श्रृ द्ध ट्ठु ' +
                'বাংলা ঁ র্খি শ্রী ক্ষৈ ঋ ' +
                'ไทย ปี้ ฟิ้ ผู้ ญ ฐู ฎุ ฬ ฤ');
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
    /** Shared metrics for all glyphs of a font, so every rasterized glyph gets the same
        baseline and height and lines up when drawn together.
        - The sample string mixes scripts so the result covers the tallest and deepest glyphs we
          expect: Latin descenders (gjpqy), accented capitals (ÅÖÉ), stacked Vietnamese (ắ),
          Greek/Cyrillic, Hebrew, Arabic, CJK and emoji.
        - fontBoundingBox* gives the font-wide extent; it is max'ed with actualBoundingBox* of the
          sample because fallback fonts may exceed the primary font's box.
          Browsers without fontBoundingBox* support fall back to the sample's actual bounds.
        - Math.ceil avoids clipping from sub-pixel rounding.
        - leading is 0: the canvas has no line gap, and the baseline is derived from ascent alone.
        - Devanagari: chandra ऑ and tall signs ि ी ै above, ु ू ृ below, stacked conjuncts द्ध ट्ठु.
        - Bangla: ঁ and reph র্ above, ্র and ু ূ ৃ below, tall independent vowels ঐ ঔ ঋ.
        - Thai: ปี้ ฟิ้ (ascender + upper vowel + tone mark, the tallest stack), ผู้, descenders ญ ฐ.

        The sample cannot cover every possible text, therefore [rasterizeGlyphAligned] tolerates overflow.
    */
    external fun measureMetrics(fontSize: Int, fontFamily: String): FloatArray

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
            const baseline = Math.ceil(ascent + leading);
            height = Math.max(1, Math.ceil(height));
            if (baseline < m.actualBoundingBoxAscent || baseline + m.actualBoundingBoxDescent > height) {
                const warned = root.__uncivFontClipWarnings || 0;
                if (warned < 10) {
                    root.__uncivFontClipWarnings = warned + 1;
                    console.warn('Glyph exceeds shared browser font metrics and will be clipped: ' + text
                        + (warned === 9 ? ' (further warnings suppressed)' : ''));
                }
            }
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
            const out = new Array(2 + data.length);
            out[0] = width;
            out[1] = height;
            for (let i = 0; i < data.length; i++) out[2 + i] = data[i];
            return out;
        """
    )
    /**
     * Renders text on a shared canvas using the metrics from measureMetrics.
     *
     *  A glyph overflowing those metrics is NOT an error: the canvas clips the overflowing pixels
     *  at its top/bottom edge, which is cosmetic only. A console warning is emitted for the first
     *  10 such glyphs (to avoid log spam), and the last one notes that further warnings are
     *  suppressed.
     */
    external fun rasterizeGlyphAligned(
        text: String,
        fontSize: Int,
        fontFamily: String,
        ascent: Float,
        height: Float,
        leading: Float,
    ): IntArray
}
