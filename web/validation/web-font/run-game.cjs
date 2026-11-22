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

function assert(condition, message) {
    if (!condition) throw new Error(message);
}

async function main() {
    const fontPath = process.env.WEB_FONT_TEST_TTF;
    if (!fontPath) throw new Error('Set WEB_FONT_TEST_TTF to a local TTF/OTF font with metrics distinct from Arial');
    const fontBytes = fs.readFileSync(fontPath);
    const appUrl = process.env.UNCIV_PREVIEW_URL || 'http://127.0.0.1:18081/';
    const profileDirectory = process.env.WEB_FONT_PROFILE ||
        path.resolve(__dirname, '../../build/validation/web-font/game-profile');
    const { chromium } = loadPlaywright();
    const launchOptions = {
        headless: true,
        viewport: { width: 1280, height: 720 },
        args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'],
    };
    if (process.env.CHROME_PATH) launchOptions.executablePath = process.env.CHROME_PATH;
    else if (process.platform === 'win32' && fs.existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe'))
        launchOptions.executablePath = 'C:/Program Files/Google/Chrome/Application/chrome.exe';

    const context = await chromium.launchPersistentContext(profileDirectory, launchOptions);
    try {
        const page = context.pages()[0] || await context.newPage();
        const pageErrors = [];
        const failedRequests = [];
        page.on('pageerror', error => pageErrors.push(error.message));
        page.on('requestfailed', request => failedRequests.push({ url: request.url(), error: request.failure()?.errorText }));
        await page.addInitScript(() => {
            window.__uncivFontMeasureFamilies = [];
            window.__uncivFontUnhandledRejections = [];
            window.addEventListener('unhandledrejection', event => {
                window.__uncivFontUnhandledRejections.push(String(event.reason));
                event.preventDefault();
            });
            const original = CanvasRenderingContext2D.prototype.measureText;
            CanvasRenderingContext2D.prototype.measureText = function (text) {
                window.__uncivFontMeasureFamilies.push({ font: this.font, text: String(text) });
                return original.call(this, text);
            };
        });
        const seedUrl = new URL('__font_fixture_seed', appUrl).href;
        // Seed IndexedDB on the game origin before loading the application.
        await page.route(seedUrl, route => route.fulfill({
            contentType: 'text/html',
            body: '<!doctype html><title>Font fixture</title>',
        }), { times: 1 });
        await page.goto(seedUrl, { waitUntil: 'domcontentloaded' });
        await page.evaluate(async ({ settings, fontBase64 }) => {
            await new Promise((resolve, reject) => {
                const request = indexedDB.deleteDatabase('unciv');
                request.onsuccess = resolve;
                request.onerror = () => reject(request.error);
                request.onblocked = () => reject(new Error('Unciv IndexedDB delete was blocked'));
            });
            const db = await new Promise((resolve, reject) => {
                const request = indexedDB.open('unciv', 1);
                request.onupgradeneeded = () => request.result.createObjectStore('FILE_DATA');
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(request.error);
            });
            try {
                const transaction = db.transaction('FILE_DATA', 'readwrite');
                const store = transaction.objectStore('FILE_DATA');
                const date = new Date();
                for (const path of ['/mods/', '/mods/FontFixture/', '/mods/FontFixture/fonts/'])
                    store.put({ type: 1, date }, path);
                const binary = Uint8Array.from(atob(fontBase64), char => char.charCodeAt(0));
                store.put({ type: 2, date, contents: new Int8Array(binary) }, '/mods/FontFixture/fonts/Distinctive.ttf/');
                const settingsBytes = new TextEncoder().encode(JSON.stringify(settings));
                store.put({ type: 2, date, contents: new Int8Array(settingsBytes) }, '/GameSettings.json/');
                await new Promise((resolve, reject) => {
                    transaction.oncomplete = resolve;
                    transaction.onerror = () => reject(transaction.error);
                    transaction.onabort = () => reject(transaction.error || new Error('Seed transaction aborted'));
                });
            } finally {
                db.close();
            }
        }, {
            settings: {
                fontFamilyData: {
                    localName: 'Distinctive (FontFixture)',
                    invariantName: 'mods/FontFixture/fonts/Distinctive.ttf',
                    filePath: 'mods/FontFixture/fonts/Distinctive.ttf',
                },
                version: 1,
            },
            fontBase64: fontBytes.toString('base64'),
        });

        await page.goto(appUrl, { waitUntil: 'domcontentloaded', timeout: 120000 });
        await page.waitForFunction(() => Array.from(document.fonts || []).some(face => face.family.includes('UncivModFont')),
            null, { timeout: 120000 });
        await page.waitForTimeout(1500);
        const result = await page.evaluate(() => {
            const face = Array.from(document.fonts).find(fontFace => fontFace.family.includes('UncivModFont'));
            const canvas = document.createElement('canvas');
            const context = canvas.getContext('2d');
            context.font = `64px '${face.family}', Arial, sans-serif`;
            const customAdvance = context.measureText('WMWMWM').width;
            context.font = '64px Arial, sans-serif';
            const fallbackAdvance = context.measureText('WMWMWM').width;
            return {
                family: face.family,
                status: face.status,
                customAdvance,
                fallbackAdvance,
                appUsedCustomFamily: window.__uncivFontMeasureFamilies.some(call => call.font.includes(face.family)),
                unhandledRejections: [...window.__uncivFontUnhandledRejections],
            };
        });
        assert(result.status === 'loaded', `FontFace status was ${result.status}`);
        assert(result.appUsedCustomFamily, 'The generated Unciv app did not measure glyphs with its selected custom family');
        assert(result.customAdvance !== result.fallbackAdvance,
            `Custom font advance matched fallback (${result.customAdvance})`);
        assert(result.unhandledRejections.length === 0,
            `Valid custom font produced unhandled rejections: ${result.unhandledRejections.join('; ')}`);
        assert(pageErrors.length === 0, `Generated app page errors: ${pageErrors.join('; ')}`);

        await page.evaluate(async () => {
            const db = await new Promise((resolve, reject) => {
                const request = indexedDB.open('unciv', 1);
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(request.error);
            });
            try {
                const transaction = db.transaction('FILE_DATA', 'readwrite');
                transaction.objectStore('FILE_DATA').put({
                    type: 2,
                    date: new Date(),
                    contents: new Int8Array([0, 1, 2, 3]),
                }, '/mods/FontFixture/fonts/Distinctive.ttf/');
                await new Promise((resolve, reject) => {
                    transaction.oncomplete = resolve;
                    transaction.onerror = () => reject(transaction.error);
                    transaction.onabort = () => reject(transaction.error || new Error('Malformed font update aborted'));
                });
            } finally {
                db.close();
            }
        });
        await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 });
        await page.waitForFunction(() => window.__uncivFontMeasureFamilies.some(call =>
            call.text.includes('Hgjpqy') && call.font.includes('Arial, Helvetica, sans-serif')),
        null, { timeout: 120000 });
        await page.waitForTimeout(500);
        const malformedResult = await page.evaluate(() => ({
            registeredCustomFaces: Array.from(document.fonts || []).filter(face => face.family.includes('UncivModFont'))
                .map(face => ({ family: face.family, status: face.status })),
            usedFallback: window.__uncivFontMeasureFamilies.some(call =>
                call.text.includes('Hgjpqy') && call.font.includes('Arial, Helvetica, sans-serif')),
            unhandledRejections: [...window.__uncivFontUnhandledRejections],
        }));
        assert(malformedResult.registeredCustomFaces.length === 0,
            `Malformed custom font left a registered face: ${JSON.stringify(malformedResult.registeredCustomFaces)}`);
        assert(malformedResult.usedFallback, 'Malformed custom font did not fall back to browser sans during Kotlin startup');
        assert(malformedResult.unhandledRejections.length === 0,
            `Malformed custom font produced unhandled rejections: ${malformedResult.unhandledRejections.join('; ')}`);
        assert(pageErrors.length === 0, `Generated app page errors: ${pageErrors.join('; ')}`);
        console.log(JSON.stringify({ result, malformedResult, pageErrors, failedRequests }, null, 2));
        console.log('Generated Unciv valid and malformed font integration passed');
    } finally {
        await context.close();
    }
}

main().catch(error => {
    console.error(error.stack || error);
    process.exitCode = 1;
});
