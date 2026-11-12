const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const zlib = require("node:zlib");
const { chromium } = require("../../build/validation/headless-browser/node_modules/playwright");

const sourcePath = path.resolve(__dirname, "../../src/main/kotlin/com/unciv/app/web/FetchHttpClientEngine.kt");
const source = fs.readFileSync(sourcePath, "utf8");
const normalizerMatch = source.match(/@JSBody\(params = \["response", "hasBody"\], script = """([\s\S]*?)"""\)/);
if (!normalizerMatch) throw new Error("Could not extract production Fetch response header normalizer");
const normalizeHeaders = normalizerMatch[1];

function listen(server) {
    return new Promise(resolve => server.listen(0, "127.0.0.1", () => resolve(server.address().port)));
}

function send(response, body, headers = {}) {
    response.writeHead(200, { "Content-Type": "text/plain", "Content-Length": body.length, ...headers });
    response.end(body);
}

async function main() {
    const decoded = Buffer.from("decoded payload ".repeat(110));
    const compressed = zlib.gzipSync(decoded);
    const zstdCompressed = zlib.zstdCompressSync(decoded);
    const plain = Buffer.from("plain body");
    const unknownBody = Buffer.from("unknown-encoding-body");
    const app = http.createServer((request, response) => {
        if (request.url === "/plain") {
            send(response, plain);
        } else if (request.url === "/gzip") {
            send(response, compressed, { "Content-Encoding": "gzip" });
        } else if (request.url === "/zstd") {
            send(response, zstdCompressed, { "Content-Encoding": "zstd" });
        } else if (request.url === "/unknown") {
            send(response, unknownBody, { "Content-Encoding": "x-custom" });
        } else if (request.url === "/head") {
            response.writeHead(200, {
                "Content-Type": "text/plain",
                "Content-Length": "123",
                "Content-Encoding": "x-custom",
                "X-Metadata": "kept"
            });
            response.end();
        } else if (request.url === "/no-content") {
            response.writeHead(204, { "X-Metadata": "kept" });
            response.end();
        } else {
            response.writeHead(200, { "Content-Type": "text/html" });
            response.end("<!doctype html><title>Fetch contract</title>");
        }
    });
    const crossOrigin = http.createServer((request, response) => {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "Content-Length");
        response.setHeader("Content-Type", "text/plain");
        if (request.url === "/gzip") {
            response.writeHead(200, { "Content-Encoding": "gzip", "Content-Length": compressed.length });
            response.end(compressed);
        } else if (request.url === "/stream-error") {
            response.writeHead(200, { "Content-Length": "200", "X-Metadata": "stream-error" });
            response.write("partial");
            response.socket.destroy();
        } else {
            response.writeHead(404);
            response.end();
        }
    });

    let browser;
    try {
        const [appPort, crossPort] = await Promise.all([listen(app), listen(crossOrigin)]);
        browser = await chromium.launch({
            headless: true,
            ...(process.env.BROWSER_EXECUTABLE ? { executablePath: process.env.BROWSER_EXECUTABLE } : {})
        });
        const page = await browser.newPage();
        await page.goto(`http://127.0.0.1:${appPort}/`);
        const result = await page.evaluate(async ({ normalizer, crossUrl, expected }) => {
            const normalize = new Function("response", "hasBody", normalizer);
            async function inspect(url, init) {
                const response = await fetch(url, init);
                const body = response.body;
                const method = (init?.method ?? "GET").toUpperCase();
                const hasBody = body !== null && method !== "HEAD" && ![204, 205, 304].includes(response.status);
                const headers = Object.fromEntries(normalize(response, hasBody));
                const bytes = body === null ? null : await response.arrayBuffer().then(buffer => buffer.byteLength);
                return { type: response.type, status: response.status, headers, bodyIsNull: body === null, bytes };
            }

            const plain = await inspect("/plain");
            const gzip = await inspect("/gzip");
            const zstd = await inspect("/zstd");
            const corsGzip = await inspect(`${crossUrl}/gzip`);
            const head = await inspect("/head", { method: "HEAD" });
            const noContent = await inspect("/no-content");
            const unknown = await inspect("/unknown");
            let streamErrorPropagated = false;
            try {
                const response = await fetch(`${crossUrl}/stream-error`);
                await response.arrayBuffer();
            } catch (_) {
                streamErrorPropagated = true;
            }
            const nullBody = new Response(null, {
                headers: { "Content-Length": "123", "Content-Encoding": "x-custom", "X-Metadata": "kept" }
            });
            Object.defineProperty(nullBody, "type", { value: "basic" });
            const nullBodyHeaders = Object.fromEntries(normalize(nullBody, nullBody.body !== null));
            const simulatedHead = new Response(null, { headers: { "Content-Length": "123", "Content-Encoding": "x-custom" } });
            Object.defineProperty(simulatedHead, "type", { value: "basic" });
            Object.defineProperty(simulatedHead, "body", { value: new ReadableStream({ start(controller) { controller.close(); } }) });
            const simulatedHeadHeaders = Object.fromEntries(normalize(simulatedHead, false));
            return { plain, gzip, zstd, corsGzip, head, noContent, unknown, nullBodyHeaders, simulatedHeadHeaders, streamErrorPropagated, expected };
        }, {
            normalizer: normalizeHeaders,
            crossUrl: `http://127.0.0.1:${crossPort}`,
            expected: { plain: plain.length, decoded: decoded.length, compressed: compressed.length, zstdCompressed: zstdCompressed.length, unknown: unknownBody.length }
        });

        const assertions = [
            [result.plain.type === "basic" && result.plain.headers["content-length"] === String(plain.length) && result.plain.bytes === plain.length, "basic plain length was not preserved"],
            [result.gzip.type === "basic" && !("content-length" in result.gzip.headers) && !("content-encoding" in result.gzip.headers) && result.gzip.bytes === decoded.length, "basic gzip metadata/body normalization failed"],
            [result.corsGzip.type === "cors" && !("content-length" in result.corsGzip.headers) && result.corsGzip.headers["content-encoding"] === undefined && result.corsGzip.bytes === decoded.length, "CORS hidden-encoding normalization failed"],
            [result.zstd.type === "basic" && !("content-length" in result.zstd.headers) && !("content-encoding" in result.zstd.headers) && result.zstd.bytes === decoded.length, "zstd decode/metadata normalization failed"],
            [result.head.status === 200 && result.head.bytes === 0 && result.head.headers["content-encoding"] === "x-custom" && result.head.headers["x-metadata"] === "kept", "HEAD metadata/body handling failed"],
            [result.noContent.status === 204 && result.noContent.bytes === 0 && result.noContent.headers["x-metadata"] === "kept", "204 metadata/body handling failed"],
            [result.unknown.type === "basic" && !("content-length" in result.unknown.headers) && result.unknown.headers["content-encoding"] === "x-custom" && result.unknown.bytes === unknownBody.length, "unknown coding metadata/body handling failed"],
            [result.nullBodyHeaders["content-length"] === "123" && result.nullBodyHeaders["content-encoding"] === "x-custom" && result.nullBodyHeaders["x-metadata"] === "kept", "null-body representation metadata was not preserved"],
            [result.simulatedHeadHeaders["content-length"] === "123" && result.simulatedHeadHeaders["content-encoding"] === "x-custom", "bodyless-method metadata was not preserved"],
            [result.streamErrorPropagated, "stream read failure was swallowed"]
        ];
        const failure = assertions.find(([ok]) => !ok);
        if (failure) throw new Error(`${failure[1]}: ${JSON.stringify(result)}`);
        console.log(JSON.stringify(result, null, 2));
    } finally {
        if (browser) await browser.close();
        app.close();
        crossOrigin.close();
    }
}

main().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
