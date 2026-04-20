package com.unciv.utils

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import java.lang.reflect.Method

fun interface ClipboardTextReceiver {
    fun onText(text: String)
}

fun interface ClipboardErrorReceiver {
    fun onError(message: String)
}

/**
 * Shared clipboard helper with a web-native bridge fallback.
 *
 * On web, TeaVM's default clipboard implementation caches clipboard content and only refreshes from browser
 * paste events. We therefore try a direct browser clipboard read/write bridge where available.
 */
object AppClipboard {
    private const val webBridgeClassName = "com.unciv.app.web.WebClipboardBridge"

    @Volatile
    private var bridgeResolved = false
    private var readTextAsyncMethod: Method? = null
    private var writeTextAsyncMethod: Method? = null

    fun getText(): String = Gdx.app.clipboard.contents

    fun hasText(): Boolean = getText().isNotEmpty()

    fun writeText(text: String) {
        Gdx.app.clipboard.contents = text
        invokeWebWrite(text)
    }

    fun readText(onText: (String) -> Unit, onError: ((String) -> Unit)? = null) {
        val fallback = getText()
        if (!invokeWebRead(
                onText = {
                    Gdx.app.clipboard.contents = it
                    onText(it)
                },
                onError = { message ->
                    if (fallback.isNotEmpty()) onText(fallback)
                    else onError?.invoke(message)
                },
            )
        ) {
            onText(fallback)
        }
    }

    /**
     * Fast path for text-field paste actions where stale cached content is better than waiting.
     * Uses cached clipboard immediately and refreshes from browser clipboard when available.
     */
    fun readTextPreferCached(onText: (String) -> Unit, onError: ((String) -> Unit)? = null) {
        val fallback = getText()
        if (fallback.isNotEmpty()) onText(fallback)
        if (!invokeWebRead(
                onText = {
                    Gdx.app.clipboard.contents = it
                    if (it != fallback) onText(it)
                },
                onError = { message ->
                    if (fallback.isEmpty()) onError?.invoke(message)
                },
            )
        ) {
            if (fallback.isEmpty()) onText(fallback)
        }
    }

    private fun isWebApp(): Boolean = Gdx.app.type == Application.ApplicationType.WebGL

    @Synchronized
    private fun resolveBridgeIfNeeded() {
        if (bridgeResolved) return
        bridgeResolved = true
        if (!isWebApp()) return
        try {
            val bridgeClass = Class.forName(webBridgeClassName)
            readTextAsyncMethod = bridgeClass.getMethod(
                "readTextAsync",
                ClipboardTextReceiver::class.java,
                ClipboardErrorReceiver::class.java
            )
            writeTextAsyncMethod = bridgeClass.getMethod("writeTextAsync", String::class.java)
        } catch (_: Throwable) {
            readTextAsyncMethod = null
            writeTextAsyncMethod = null
        }
    }

    private fun invokeWebWrite(text: String) {
        if (!isWebApp()) return
        resolveBridgeIfNeeded()
        try {
            writeTextAsyncMethod?.invoke(null, text)
        } catch (_: Throwable) {
            // Best-effort bridge call, fallback is already set on Gdx clipboard.
        }
    }

    private fun invokeWebRead(onText: (String) -> Unit, onError: (String) -> Unit): Boolean {
        if (!isWebApp()) return false
        resolveBridgeIfNeeded()
        val method = readTextAsyncMethod ?: return false

        return try {
            val success = ClipboardTextReceiver { text ->
                Gdx.app.postRunnable { onText(text) }
            }
            val error = ClipboardErrorReceiver { message ->
                Gdx.app.postRunnable { onError(message) }
            }
            method.invoke(null, success, error) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
