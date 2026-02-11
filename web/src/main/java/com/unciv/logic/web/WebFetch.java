package com.unciv.logic.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBuffer;

final class WebFetch {
    private WebFetch() {
    }

    @JSFunctor
    interface TextSuccess extends JSObject {
        void handle(int status, String statusText, String url, String headersJson, String text);
    }

    @JSFunctor
    interface BinarySuccess extends JSObject {
        void handle(int status, String statusText, String url, String headersJson, ArrayBuffer data);
    }

    @JSFunctor
    interface ErrorCallback extends JSObject {
        void handle(String message);
    }

    @JSBody(
            params = {"method", "url", "headerNames", "headerValues", "body", "timeoutMs", "onSuccess", "onError"},
            script =
                    "function callSuccess(status, statusText, url, headersJson, payload){\n"
                            + "  if (onSuccess && typeof onSuccess.handle === 'function') { onSuccess.handle(status, statusText, url, headersJson, payload); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.$invoke === 'function') { onSuccess.$invoke(status, statusText, url, headersJson, payload); return; }\n"
                            + "  if (typeof onSuccess === 'function') { onSuccess.call(onSuccess, status, statusText, url, headersJson, payload); return; }\n"
                            + "}\n"
                            + "function callError(message){\n"
                            + "  if (onError && typeof onError.handle === 'function') { onError.handle(message); return; }\n"
                            + "  if (onError && typeof onError.$invoke === 'function') { onError.$invoke(message); return; }\n"
                            + "  if (typeof onError === 'function') { onError.call(onError, message); return; }\n"
                            + "}\n"
                            + "var headers = {};\n"
                            + "if (headerNames) {\n"
                            + "  for (var i = 0; i < headerNames.length; i++) {\n"
                            + "    headers[headerNames[i]] = headerValues[i];\n"
                            + "  }\n"
                            + "}\n"
                            + "var controller = (typeof AbortController !== 'undefined') ? new AbortController() : null;\n"
                            + "var timer = null;\n"
                            + "if (controller && timeoutMs && timeoutMs > 0) {\n"
                            + "  timer = setTimeout(function(){ controller.abort(); }, timeoutMs);\n"
                            + "}\n"
                            + "var options = { method: method, headers: headers };\n"
                            + "if (body !== null && body !== undefined && body !== '') options.body = body;\n"
                            + "if (controller) options.signal = controller.signal;\n"
                            + "fetch(url, options)\n"
                            + "  .then(function(resp){\n"
                            + "    var headerObj = {};\n"
                            + "    if (resp.headers && resp.headers.forEach) {\n"
                            + "      resp.headers.forEach(function(value, key){ headerObj[key] = value; });\n"
                            + "    }\n"
                            + "    return resp.text().then(function(text){\n"
                            + "      if (timer) clearTimeout(timer);\n"
                            + "      callSuccess(resp.status|0, resp.statusText || '', resp.url || url, JSON.stringify(headerObj), text || '');\n"
                            + "    });\n"
                            + "  })\n"
                            + "  .catch(function(err){\n"
                            + "    if (timer) clearTimeout(timer);\n"
                            + "    var msg = (err && (err.message || err.name)) ? (err.message || err.name) : String(err);\n"
                            + "    if (typeof console !== 'undefined' && console.error) { console.error('WebFetch text error', url, msg); }\n"
                            + "    callError(msg);\n"
                            + "  });")
    static native void fetchText(
            String method,
            String url,
            String[] headerNames,
            String[] headerValues,
            String body,
            int timeoutMs,
            TextSuccess onSuccess,
            ErrorCallback onError
    );

    @JSBody(
            params = {"method", "url", "headerNames", "headerValues", "body", "timeoutMs", "onSuccess", "onError"},
            script =
                    "function callSuccess(status, statusText, url, headersJson, payload){\n"
                            + "  if (onSuccess && typeof onSuccess.handle === 'function') { onSuccess.handle(status, statusText, url, headersJson, payload); return; }\n"
                            + "  if (onSuccess && typeof onSuccess.$invoke === 'function') { onSuccess.$invoke(status, statusText, url, headersJson, payload); return; }\n"
                            + "  if (typeof onSuccess === 'function') { onSuccess.call(onSuccess, status, statusText, url, headersJson, payload); return; }\n"
                            + "}\n"
                            + "function callError(message){\n"
                            + "  if (onError && typeof onError.handle === 'function') { onError.handle(message); return; }\n"
                            + "  if (onError && typeof onError.$invoke === 'function') { onError.$invoke(message); return; }\n"
                            + "  if (typeof onError === 'function') { onError.call(onError, message); return; }\n"
                            + "}\n"
                            + "var headers = {};\n"
                            + "if (headerNames) {\n"
                            + "  for (var i = 0; i < headerNames.length; i++) {\n"
                            + "    headers[headerNames[i]] = headerValues[i];\n"
                            + "  }\n"
                            + "}\n"
                            + "var controller = (typeof AbortController !== 'undefined') ? new AbortController() : null;\n"
                            + "var timer = null;\n"
                            + "if (controller && timeoutMs && timeoutMs > 0) {\n"
                            + "  timer = setTimeout(function(){ controller.abort(); }, timeoutMs);\n"
                            + "}\n"
                            + "var options = { method: method, headers: headers };\n"
                            + "if (body !== null && body !== undefined && body !== '') options.body = body;\n"
                            + "if (controller) options.signal = controller.signal;\n"
                            + "fetch(url, options)\n"
                            + "  .then(function(resp){\n"
                            + "    var headerObj = {};\n"
                            + "    if (resp.headers && resp.headers.forEach) {\n"
                            + "      resp.headers.forEach(function(value, key){ headerObj[key] = value; });\n"
                            + "    }\n"
                            + "    return resp.arrayBuffer().then(function(buffer){\n"
                            + "      if (timer) clearTimeout(timer);\n"
                            + "      callSuccess(resp.status|0, resp.statusText || '', resp.url || url, JSON.stringify(headerObj), buffer);\n"
                            + "    });\n"
                            + "  })\n"
                            + "  .catch(function(err){\n"
                            + "    if (timer) clearTimeout(timer);\n"
                            + "    var msg = (err && (err.message || err.name)) ? (err.message || err.name) : String(err);\n"
                            + "    if (typeof console !== 'undefined' && console.error) { console.error('WebFetch bytes error', url, msg); }\n"
                            + "    callError(msg);\n"
                            + "  });")
    static native void fetchBytes(
            String method,
            String url,
            String[] headerNames,
            String[] headerValues,
            String body,
            int timeoutMs,
            BinarySuccess onSuccess,
            ErrorCallback onError
    );
}
