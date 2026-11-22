const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

function loadPlaywright() {
    if (process.env.PLAYWRIGHT_MODULE) return require(process.env.PLAYWRIGHT_MODULE);
    try { return require('playwright'); }
    catch (_) { return require(path.resolve(__dirname, '../../build/validation/headless-browser/node_modules/playwright')); }
}

const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

async function main() {
    const { chromium } = loadPlaywright();
    const outDir = path.resolve(process.env.UNCIV_DISPLAY_ARTIFACTS || path.resolve(__dirname, '../../build/validation/display-options'));
    fs.mkdirSync(outDir, { recursive: true });
    const profile = path.resolve(process.env.UNCIV_DISPLAY_PROFILE || fs.mkdtempSync(path.join(os.tmpdir(), 'unciv-display-options-profile-')));
    const url = process.env.UNCIV_PREVIEW_URL || 'http://127.0.0.1:18081/';
    const options = { headless: true, viewport: { width: 1280, height: 720 }, args: ['--no-sandbox', '--use-gl=angle', '--use-angle=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist'] };
    if (process.env.CHROME_PATH) options.executablePath = process.env.CHROME_PATH;
    else if (process.platform === 'win32' && fs.existsSync('C:/Program Files/Google/Chrome/Application/chrome.exe')) options.executablePath = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
    const context = await chromium.launchPersistentContext(profile, options);
    const errors = [];
    try {
        const page = context.pages()[0] || await context.newPage();
        page.on('pageerror', error => errors.push(error.stack || error.message));
        await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 120000 });
        await delay(16000);
        await page.screenshot({ path: path.join(outDir, '01-startup.png') });
        const checkCanvasViewport = async (width, height) => {
            const canvases = await page.locator('canvas').evaluateAll(items => items.map(canvas => {
                const rect = canvas.getBoundingClientRect();
                return { width: rect.width, height: rect.height };
            }));
            if (!canvases.some(canvas => Math.abs(canvas.width - width) < 2 && Math.abs(canvas.height - height) < 2))
                throw new Error(`No game canvas matched ${width}x${height}; observed ${JSON.stringify(canvases)}`);
        };
        await checkCanvasViewport(1280, 720);

        // Fresh profiles first show the language picker. Choose English and confirm.
        await page.mouse.click(640, 112);
        await delay(500);
        await page.mouse.click(1180, 665);
        await delay(10000);
        await page.screenshot({ path: path.join(outDir, '02-main-menu.png') });
        await page.mouse.click(870, 540); // Main menu > Options
        await delay(2500);
        await page.screenshot({ path: path.join(outDir, '03-gameplay.png') });

        // The default Gameplay page should be safe; visit the regression-prone Display page.
        await page.mouse.click(430, 80);
        await delay(1200);
        await page.screenshot({ path: path.join(outDir, '04-display.png') });
        await page.setViewportSize({ width: 1024, height: 768 });
        await delay(1500);
        await checkCanvasViewport(1024, 768);
        await page.screenshot({ path: path.join(outDir, '05-display-resized.png') });
        await page.setViewportSize({ width: 1280, height: 720 });
        await delay(1000);
        await page.reload({ waitUntil: 'domcontentloaded', timeout: 120000 });
        await delay(16000);
        await checkCanvasViewport(1280, 720);
        await page.screenshot({ path: path.join(outDir, '06-refreshed.png') });
        await page.mouse.click(870, 540);
        await delay(2500);
        await page.mouse.click(430, 80);
        await delay(1500);
        await page.screenshot({ path: path.join(outDir, '07-display-after-refresh.png') });

        // Visit the visible option tabs in the same modal/session.
        await page.mouse.click(600, 80); await delay(700);
        await page.screenshot({ path: path.join(outDir, '08-gameplay-reopen.png') });
        await page.mouse.click(790, 80); await delay(700);
        await page.screenshot({ path: path.join(outDir, '09-automation.png') });
        await page.mouse.click(925, 80); await delay(700);
        await page.screenshot({ path: path.join(outDir, '10-language.png') });
        console.log(JSON.stringify({ artifacts: outDir, pageErrors: errors }));
        if (errors.length) throw new Error(`Page errors: ${errors.join('\n')}`);
    } finally { await context.close(); }
}

main().catch(error => { console.error(error.stack || error); process.exit(1); });
