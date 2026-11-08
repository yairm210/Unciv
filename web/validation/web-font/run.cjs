const fs = require('node:fs');
const path = require('node:path');

function loadPlaywright() {
    if (process.env.PLAYWRIGHT_MODULE) return require(process.env.PLAYWRIGHT_MODULE);
    try {
        return require('playwright');
    } catch (_) {
        return require(path.resolve(__dirname, '../../build/validation/headless-browser/node_modules/playwright'));
    }
}

function jsBody(source, functionName) {
    const functionIndex = source.indexOf(`external fun ${functionName}(`);
    if (functionIndex < 0) throw new Error(`Could not find production ${functionName} external function`);
    const annotationIndex = source.lastIndexOf('@JSBody', functionIndex);
    const open = source.indexOf('"""', annotationIndex);
    const close = source.indexOf('"""', open + 3);
    if (annotationIndex < 0 || open < 0 || close < 0)
        throw new Error(`Could not extract production @JSBody for ${functionName}`);
    const annotation = source.slice(annotationIndex, open);
    const paramsMatch = annotation.match(/params\s*=\s*\[([^\]]*)\]/);
    if (!paramsMatch) throw new Error(`Could not extract @JSBody parameters for ${functionName}`);
    const params = [...paramsMatch[1].matchAll(/"([^"]+)"/g)].map(match => match[1]);
    return { params, script: source.slice(open + 3, close) };
}

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

async function main() {
    const fontPath = process.env.WEB_FONT_TEST_TTF;
    if (!fontPath) throw new Error('Set WEB_FONT_TEST_TTF to a local TTF/OTF font with metrics distinct from Arial');
    const fontBytes = fs.readFileSync(fontPath);
    const rasterizerPath = path.resolve(__dirname, '../../src/main/kotlin/com/unciv/app/web/WebFontRasterizer.kt');
    const rasterizerSource = fs.readFileSync(rasterizerPath, 'utf8');
    const register = jsBody(rasterizerSource, 'registerFont');
    const remove = jsBody(rasterizerSource, 'removeFont');
    const measure = jsBody(rasterizerSource, 'measureMetrics');
    const rasterize = jsBody(rasterizerSource, 'rasterizeGlyphAligned');

    const webFontPath = path.resolve(__dirname, '../../src/main/kotlin/com/unciv/app/web/WebFont.kt');
    const webFontSource = fs.readFileSync(webFontPath, 'utf8');
    const scannerStart = webFontSource.indexOf('override fun getModFonts(');
    const scannerEnd = webFontSource.indexOf('\n    }', scannerStart);
    assert(scannerStart >= 0 && scannerEnd > scannerStart, 'Could not locate WebFont FileHandle mod-font scanner');
    const scanner = webFontSource.slice(scannerStart, scannerEnd);
    for (const api of ['modsDir.isDirectory', 'modsDir.list()', 'mod.isDirectory', 'fontsDir.exists()', 'fontsDir.list()'])
        assert(scanner.includes(api), `FileHandle mod-font scanner no longer uses ${api}`);
    assert(!scanner.includes('.file()'), 'WebGL mod-font scanner must not call FileHandle.file()');
    assert(scanner.includes('"$modsDirName/${mod.name()}/fonts/${font.name()}"'), 'Discovered path must remain relative to the local data folder');
    assert(webFontSource.includes('getLocalFile(filePath).readBytes()'), 'WebFont must read font bytes through FileHandle APIs');

    const { chromium } = loadPlaywright();
    const chromiumOptions = { headless: true, args: ['--no-sandbox'] };
    if (process.env.CHROME_PATH) chromiumOptions.executablePath = process.env.CHROME_PATH;
    else if (process.platform === 'win32' && fs.existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe'))
        chromiumOptions.executablePath = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

    const browser = await chromium.launch(chromiumOptions);
    try {
        const page = await browser.newPage();
        const pageErrors = [];
        page.on('pageerror', error => pageErrors.push(error.message));
        await page.goto('about:blank');
        const result = await page.evaluate(({ fontBase64, functions }) => {
            const compile = ({ params, script }) => new Function(...params, script);
            const registerFont = compile(functions.register);
            const removeFont = compile(functions.remove);
            const measureMetrics = compile(functions.measure);
            const rasterizeGlyphAligned = compile(functions.rasterize);

            const raw = atob(fontBase64);
            const bytes = new Uint8Array(raw.length);
            for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
            // TeaVM's Java byte[] is accepted by the same Uint8Array conversion used by the production bridge.
            const javaBytes = new Int8Array(bytes.buffer);
            const firstFace = registerFont(javaBytes, 'UncivFontProbe1');
            if (firstFace.status !== 'loaded') throw new Error(`Valid binary face state was ${firstFace.status}`);
            if (!document.fonts.has(firstFace)) throw new Error('Valid FontFace was not added to document.fonts');

            const customFamily = "'UncivFontProbe1', Arial, Helvetica, sans-serif";
            const firstMetrics = measureMetrics(64, customFamily);
            const fallbackMetrics = measureMetrics(64, 'Arial, Helvetica, sans-serif');
            const metricCanvas = document.createElement('canvas').getContext('2d');
            metricCanvas.font = '64px ' + customFamily;
            const customAdvance = metricCanvas.measureText('WMWMWM').width;
            metricCanvas.font = '64px Arial, Helvetica, sans-serif';
            const fallbackAdvance = metricCanvas.measureText('WMWMWM').width;
            const customGlyph = rasterizeGlyphAligned('WMWMWM', 64, customFamily,
                firstMetrics[0], firstMetrics[2], firstMetrics[3]);
            const fallbackGlyph = rasterizeGlyphAligned('WMWMWM', 64, 'Arial, Helvetica, sans-serif',
                fallbackMetrics[0], fallbackMetrics[2], fallbackMetrics[3]);
            if (JSON.stringify(customGlyph) === JSON.stringify(fallbackGlyph))
                throw new Error('Custom font rasterized the probe glyph identically to Arial');
            if (!(customAdvance > 0) || customAdvance === fallbackAdvance)
                throw new Error(`Custom font advance metric did not differ from Arial (${customAdvance} vs ${fallbackAdvance})`);

            removeFont(firstFace);
            if (document.fonts.has(firstFace)) throw new Error('Switch/reset did not remove the previous FontFace');
            const secondFace = registerFont(javaBytes, 'UncivFontProbe2');
            if (secondFace.status !== 'loaded' || !document.fonts.has(secondFace))
                throw new Error('Switching to another custom family did not register its FontFace');
            const secondMetrics = measureMetrics(64, "'UncivFontProbe2', Arial, sans-serif");
            if (JSON.stringify(firstMetrics) !== JSON.stringify(secondMetrics))
                throw new Error('Re-registering the same font under a new family changed its metrics');
            removeFont(secondFace);
            if (document.fonts.has(secondFace)) throw new Error('Reset did not unregister the active FontFace');

            let malformedFailure = null;
            try {
                registerFont(new Int8Array([0, 1, 2, 3]), 'UncivMalformedProbe');
            } catch (error) {
                malformedFailure = { name: error.name, message: error.message };
            }
            if (!malformedFailure) throw new Error('Malformed font bytes unexpectedly registered successfully');

            return {
                validFaceStatus: firstFace.status,
                fontBytes: javaBytes.length,
                customMetrics: firstMetrics,
                fallbackMetrics,
                customAdvance,
                fallbackAdvance,
                customGlyphDimensions: customGlyph.slice(0, 2),
                fallbackGlyphDimensions: fallbackGlyph.slice(0, 2),
                switchReset: !document.fonts.has(firstFace) && !document.fonts.has(secondFace),
                malformedFailure,
            };
        }, {
            fontBase64: fontBytes.toString('base64'),
            functions: { register, remove, measure, rasterize },
        });
        assert(result.switchReset, 'Custom font switch/reset did not finish cleanly');
        assert(pageErrors.length === 0, `Browser page errors: ${pageErrors.join('; ')}`);
        console.log(JSON.stringify({ result, fileHandleDiscovery: 'source contract passed', pageErrors }, null, 2));
        console.log('Web font contract passed');
    } finally {
        await browser.close();
    }
}

main().catch(error => {
    console.error(error.stack || error);
    process.exitCode = 1;
});
