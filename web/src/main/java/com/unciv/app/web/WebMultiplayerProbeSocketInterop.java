package com.unciv.app.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

final class WebMultiplayerProbeSocketInterop {
    private WebMultiplayerProbeSocketInterop() {
    }

    @JSFunctor
    interface OpenCallback extends JSObject {
        void handle();
    }

    @JSFunctor
    interface MessageCallback extends JSObject {
        void handle(String data);
    }

    @JSFunctor
    interface ErrorCallback extends JSObject {
        void handle(String message);
    }

    @JSFunctor
    interface CloseCallback extends JSObject {
        void handle(int code, String reason);
    }

    @JSBody(
            params = {"url", "onOpen", "onMessage", "onError", "onClose"},
            script =
                    "try {\n"
                            + "  var ws = new WebSocket(url);\n"
                            + "  ws.onopen = function(){ onOpen(); };\n"
                            + "  ws.onmessage = function(evt){ onMessage(String(evt.data || '')); };\n"
                            + "  ws.onerror = function(){ onError('WebSocket error'); };\n"
                            + "  ws.onclose = function(evt){ onClose(evt && evt.code ? evt.code : 1006, evt && evt.reason ? evt.reason : ''); };\n"
                            + "  return ws;\n"
                            + "} catch (err) {\n"
                            + "  onError(err && (err.message || err.name) ? (err.message || err.name) : String(err));\n"
                            + "  return null;\n"
                            + "}\n")
    static native Object connect(
            String url,
            OpenCallback onOpen,
            MessageCallback onMessage,
            ErrorCallback onError,
            CloseCallback onClose
    );

    @JSBody(params = {"socket", "data"}, script = "if (socket && socket.send) socket.send(data);")
    static native void send(Object socket, String data);

    @JSBody(params = {"socket"}, script = "return socket && socket.readyState !== undefined ? socket.readyState : -1;")
    static native int readyState(Object socket);

    @JSBody(params = {"socket", "code", "reason"}, script = "if (socket && socket.close) socket.close(code || 1000, reason || '');")
    static native void close(Object socket, int code, String reason);

    @JSBody(params = {"value"}, script = "return encodeURIComponent(value || '');")
    static native String encodeURIComponent(String value);
}
