package com.unciv.logic.github;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;

final class WebZipInterop {
    private WebZipInterop() {
    }

    @JSFunctor
    interface EntryCallback extends JSObject {
        void handle(String name, ArrayBuffer data);
    }

    @JSFunctor
    interface ProgressCallback extends JSObject {
        void handle(int processed, int total);
    }

    @JSFunctor
    interface DoneCallback extends JSObject {
        void handle();
    }

    @JSFunctor
    interface ErrorCallback extends JSObject {
        void handle(String message);
    }

    @JSBody(
            params = {"zipData", "onEntry", "onProgress", "onDone", "onError"},
            script =
                    "if (typeof window === 'undefined') { onError('unavailable'); return; }\n"
                            + "function ensureZip(callback){\n"
                            + "  if (typeof JSZip !== 'undefined') { callback(); return; }\n"
                            + "  if (window.__uncivJsZipLoading) {\n"
                            + "    window.__uncivJsZipLoading.push(callback); return;\n"
                            + "  }\n"
                            + "  window.__uncivJsZipLoading = [callback];\n"
                            + "  var script = document.createElement('script');\n"
                            + "  script.src = 'jszip.min.js';\n"
                            + "  script.async = true;\n"
                            + "  script.onload = function(){\n"
                            + "    var pending = window.__uncivJsZipLoading || [];\n"
                            + "    window.__uncivJsZipLoading = null;\n"
                            + "    for (var i = 0; i < pending.length; i++) pending[i]();\n"
                            + "  };\n"
                            + "  script.onerror = function(){ onError('Failed to load jszip.min.js'); };\n"
                            + "  document.head.appendChild(script);\n"
                            + "}\n"
                            + "ensureZip(function(){\n"
                            + "  JSZip.loadAsync(zipData).then(function(zip){\n"
                            + "    var names = Object.keys(zip.files || {});\n"
                            + "    var total = names.length;\n"
                            + "    var index = 0;\n"
                            + "    function next(){\n"
                            + "      if (index >= total) { onDone(); return; }\n"
                            + "      var name = names[index++];\n"
                            + "      var entry = zip.files[name];\n"
                            + "      if (!entry || entry.dir) { if (onProgress) onProgress(index, total); next(); return; }\n"
                            + "      entry.async('arraybuffer').then(function(buffer){\n"
                            + "        onEntry(name, buffer);\n"
                            + "        if (onProgress) onProgress(index, total);\n"
                            + "        next();\n"
                            + "      }).catch(function(err){\n"
                            + "        onError(err && (err.message || err.name) ? (err.message || err.name) : String(err));\n"
                            + "      });\n"
                            + "    }\n"
                            + "    next();\n"
                            + "  }).catch(function(err){\n"
                            + "    onError(err && (err.message || err.name) ? (err.message || err.name) : String(err));\n"
                            + "  });\n"
                            + "});\n")
    static native void unzip(
            ArrayBuffer zipData,
            EntryCallback onEntry,
            ProgressCallback onProgress,
            DoneCallback onDone,
            ErrorCallback onError
    );
}
