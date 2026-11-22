# TeaVM Fetch engine contract

This fixture compiles the production `FetchHttpClientEngine` and its service descriptor, then runs the generated JavaScript under Node with a mocked browser `fetch` implementation. It covers GET headers/status/text/body streaming, POST body transmission, non-2xx responses, network rejection, request cancellation, response-body cancellation, and Ktor request timeout cancellation.

The fixture uses the same GDX and TeaVM source-composite setup as the web app, so it exercises the TeaVM commit pinned in `web/dependencies.lock.properties` without resolving an unpublished snapshot from Maven. For a fresh run, set `GDX_TEAVM_SOURCE_ROOT` and `TEAVM_SOURCE_ROOT` to verified checkouts or extractions of the corresponding pinned commits, then run from the Unciv root:

```powershell
$gdxSource = $env:GDX_TEAVM_SOURCE_ROOT
$teavmSource = $env:TEAVM_SOURCE_ROOT
if (-not $gdxSource -or -not $teavmSource) { throw 'Set GDX_TEAVM_SOURCE_ROOT and TEAVM_SOURCE_ROOT to the pinned source trees' }
.\gradlew.bat `
  -p web/validation/http-fetch-engine runJavaScriptContract `
  "-PgdxTeaVMSource=$gdxSource" `
  "-PteavmPath=$teavmSource" `
  --console=plain --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
```

The Node runner checks GET/POST UTF-8 bytes, streamed body data split across the encoding of `é`, headers, non-2xx response preservation, network error/reason propagation, request cancellation, response-body cancellation, and Ktor request timeout cancellation. Set `FETCH_SKIP_CANCEL=1` only to skip cancellation checks, or `FETCH_COROUTINE_CANCEL_REPRO=1` to run only the minimal Kotlin timeout/cancellable-suspension reproduction used to isolate TeaVM stack-recovery behavior.

The contract includes Fetch-visible responses with mismatched wire and decoded lengths, checking that a CORS decoded body is fully read and a basic response's matching length is preserved. Its generated Ktor case also gives HEAD and 204 responses an empty stream object plus representation headers, verifying the engine's method/status guard preserves those headers. For browser behavior, run `node web/validation/http-fetch-engine/browser-fetch-contract.cjs`; it extracts and executes the production response-header normalizer `@JSBody`, using the Playwright dependency from `web/build/validation/headless-browser/node_modules` and a local Chrome/Edge (`BROWSER_EXECUTABLE` can point to its executable). The fixture checks basic plain and gzip responses, CORS gzip with hidden `Content-Encoding`, zstd, unknown coding, HEAD and 204 responses, synthetic null-body and bodyless-method metadata, and a truncated network stream. It uses only local HTTP servers, which are closed after the check.
