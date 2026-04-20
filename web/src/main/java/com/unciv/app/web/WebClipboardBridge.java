package com.unciv.app.web;

import com.unciv.utils.ClipboardErrorReceiver;
import com.unciv.utils.ClipboardTextReceiver;
import org.teavm.jso.JSBody;

public final class WebClipboardBridge {
    private WebClipboardBridge() {
    }

    public static boolean readTextAsync(ClipboardTextReceiver onSuccess, ClipboardErrorReceiver onError) {
        if (onSuccess == null || onError == null) return false;
        return readTextNative(onSuccess, onError);
    }

    public static boolean writeTextAsync(String text) {
        return writeTextNative(text == null ? "" : text);
    }

    @JSBody(
            params = {"onSuccess", "onError"},
            script = "if (!('clipboard' in navigator) || !navigator.clipboard || !navigator.clipboard.readText) return false;\n"
                    + "try {\n"
                    + "  navigator.clipboard.readText()\n"
                    + "    .then(function(text) { onSuccess.onText(text == null ? '' : text); })\n"
                    + "    .catch(function(err) {\n"
                    + "      var message = err && err.message ? err.message : '' + err;\n"
                    + "      onError.onError(message);\n"
                    + "    });\n"
                    + "  return true;\n"
                    + "} catch (err) {\n"
                    + "  var message = err && err.message ? err.message : '' + err;\n"
                    + "  onError.onError(message);\n"
                    + "  return false;\n"
                    + "}"
    )
    private static native boolean readTextNative(ClipboardTextReceiver onSuccess, ClipboardErrorReceiver onError);

    @JSBody(
            params = {"text"},
            script = "if (!('clipboard' in navigator) || !navigator.clipboard || !navigator.clipboard.writeText) return false;\n"
                    + "try {\n"
                    + "  navigator.clipboard.writeText(text == null ? '' : text).catch(function(){});\n"
                    + "  return true;\n"
                    + "} catch (err) {\n"
                    + "  return false;\n"
                    + "}"
    )
    private static native boolean writeTextNative(String text);
}
