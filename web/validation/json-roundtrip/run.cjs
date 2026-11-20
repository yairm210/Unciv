Object.defineProperty(globalThis, "crypto", { value: require("node:crypto").webcrypto, configurable: true });
let passed = false;
const timeout = setTimeout(() => {
    console.error("Unciv JSON contract timed out");
    process.exit(2);
}, 15000);
const originalInfo = console.info;
console.info = (...args) => {
    originalInfo(...args);
    if (args.join(" ") === "Unciv JSON contract passed") passed = true;
};
try {
    const runtime = require("./build/generated/teavm/js/unciv-json-validation.js");
    runtime.main([], error => {
        clearTimeout(timeout);
        if (error || !passed) {
            console.error(error || "Unciv JSON contract did not reach success marker");
            process.exitCode = 1;
        }
    });
} catch (error) {
    clearTimeout(timeout);
    console.error(error);
    process.exitCode = 1;
}
