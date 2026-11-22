let passed = false;
let abortObserved = false;
let bodyCancelObserved = false;
let requestTimeoutObserved = false;
let statusRequest = null;
let postedBody = null;
let runtimeFailure = null;
const cancellationRepro = process.env.FETCH_COROUTINE_CANCEL_REPRO === "1";
const skipCancellation = process.env.FETCH_SKIP_CANCEL === "1";
const successMarker = cancellationRepro
    ? "Coroutine cancellation reproduction passed"
    : "Fetch engine contract passed";
const timeout = setTimeout(() => {
    console.error("Fetch engine contract timed out");
    process.exit(2);
}, 10000);

global.fetch = async (url, options) => {
    if (url.endsWith("/status")) {
        statusRequest = options;
        const body = new ReadableStream({
            start(controller) {
                const encoder = new TextEncoder();
                const bytes = encoder.encode("streamed café 🧭 response body");
                const split = bytes.indexOf(0xC3) + 1; // Split the two-byte UTF-8 encoding of "é".
                controller.enqueue(bytes.slice(0, split));
                controller.enqueue(bytes.slice(split));
                controller.close();
            }
        });
        const response = new Response(body, {
            status: 207,
            statusText: "Multi-Status",
            headers: {
                "X-Fetch-Probe": "streamed",
                "Content-Length": String(new TextEncoder().encode("streamed café 🧭 response body").length)
            }
        });
        Object.defineProperty(response, "type", { value: "basic" });
        return response;
    }
    if (url.endsWith("/compressed-body")) {
        // Model the Fetch-visible response: body has already been decompressed, while
        // the CORS-visible Content-Length is the smaller compressed wire size.
        const response = new Response("x".repeat(1775), {
            status: 200,
            headers: { "Content-Length": "347" }
        });
        Object.defineProperty(response, "type", { value: "cors" });
        return response;
    }
    if (url.endsWith("/head")) {
        const response = new Response(null, {
            status: 200,
            headers: { "Content-Length": "123", "Content-Encoding": "x-custom" }
        });
        Object.defineProperty(response, "type", { value: "basic" });
        // Chromium can expose an empty ReadableStream for HEAD. Exercise the engine's
        // request-method guard even when Fetch supplies a stream object.
        Object.defineProperty(response, "body", { value: new ReadableStream({ start(controller) { controller.close(); } }) });
        return response;
    }
    if (url.endsWith("/no-content")) {
        const response = new Response(null, {
            status: 204,
            headers: { "X-Metadata": "kept" }
        });
        Object.defineProperty(response, "type", { value: "basic" });
        Object.defineProperty(response, "body", { value: new ReadableStream({ start(controller) { controller.close(); } }) });
        return response;
    }
    if (url.endsWith("/post")) {
        postedBody = new TextDecoder().decode(options.body);
        return new Response("post accepté 🏁", { status: 201 });
    }
    if (url.endsWith("/error")) {
        return new Response("server unavailable", { status: 503 });
    }
    if (url.endsWith("/mid-body")) {
        const body = new ReadableStream({
            start(controller) {
                controller.enqueue(new TextEncoder().encode("partial body"));
            },
            cancel() {
                bodyCancelObserved = true;
            }
        });
        return new Response(body, { status: 200 });
    }
    if (url.endsWith("/network")) {
        return Promise.reject(new TypeError("network offline"));
    }
    if (url.endsWith("/abort")) {
        return new Promise((resolve, reject) => {
            options.signal.addEventListener("abort", () => {
                abortObserved = true;
                reject(new DOMException("Aborted", "AbortError"));
            }, { once: true });
        });
    }
    if (url.endsWith("/request-timeout")) {
        return new Promise((resolve, reject) => {
            options.signal.addEventListener("abort", () => {
                requestTimeoutObserved = true;
                reject(new DOMException("Aborted", "AbortError"));
            }, { once: true });
        });
    }
    throw new Error(`Unexpected Fetch URL: ${url}`);
};

const originalInfo = console.info;
console.info = (...args) => {
    const line = args.join(" ");
    originalInfo(...args);
    if (line === successMarker) passed = true;
};
const originalLog = console.log;
console.log = (...args) => {
    const line = args.join(" ");
    originalLog(...args);
    if (line === successMarker) passed = true;
};
const originalError = console.error;
console.error = (...args) => {
    if (String(args[0]).startsWith("SLF4J(W):")) {
        originalError(...args);
        return;
    }
    runtimeFailure = args.map(String).join(" ");
    originalError(...args);
};

process.on("unhandledRejection", error => {
    runtimeFailure = error?.stack ?? String(error);
});
process.on("uncaughtException", error => {
    runtimeFailure = error?.stack ?? String(error);
});

let runtime;
try {
    runtime = require("./build/generated/teavm/js/unciv-fetch-engine-validation.js");
} catch (error) {
    clearTimeout(timeout);
    console.error("Fetch engine contract failed while loading generated JavaScript", error?.stack ?? error);
    process.exit(1);
}
if (typeof runtime.main !== "function") throw new Error("TeaVM Fetch entry point was not generated");
runtime.main(cancellationRepro ? ["--cancel-repro"] : (skipCancellation ? ["--skip-cancel"] : []), error => {
    if (error) {
        clearTimeout(timeout);
        console.error("Fetch engine contract failed", error?.stack ?? error);
        process.exit(1);
    }
});

const checkResult = setInterval(() => {
    if (runtimeFailure) {
        clearInterval(checkResult);
        clearTimeout(timeout);
        console.error("Fetch engine contract failed", runtimeFailure);
        process.exit(1);
    }
    if (!passed) return;
    clearInterval(checkResult);
    clearTimeout(timeout);
    const header = statusRequest?.headers?.get("X-Unciv-Probe");
    if (!cancellationRepro && (header !== "preserved" || postedBody !== "request café 🧭" ||
        (!skipCancellation && (!abortObserved || !bodyCancelObserved || !requestTimeoutObserved)))) {
        console.error("Fetch engine contract failed", { passed, abortObserved, bodyCancelObserved, requestTimeoutObserved, header, postedBody });
        process.exit(1);
    }
    process.exit(0);
}, 25);
