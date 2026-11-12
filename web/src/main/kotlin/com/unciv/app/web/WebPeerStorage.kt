package com.unciv.app.web

import com.unciv.logic.multiplayer.storage.MultiplayerFileNotFoundException
import com.unciv.logic.multiplayer.storage.PlatformMultiplayerStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import org.teavm.jso.JSBody
import org.teavm.jso.JSFunctor
import org.teavm.jso.JSObject
import java.io.IOException

internal object WebPeerStorage : PlatformMultiplayerStorage {
    override val serverUrl = "https://peer.unciv.invalid"
    override val isConnected get() = WebPeerBridge.isConnected()

    private val client by lazy { HttpClient(FetchHttpClientEngineFactory) }

    override suspend fun saveFileData(fileName: String, data: String) {
        val response = client.put("$serverUrl/files/$fileName") { setBody(data) }
        if (!response.status.isSuccess()) throw IOException("Peer save failed (${response.status})")
    }

    override suspend fun loadFileData(fileName: String): ByteArray {
        val response = client.get("$serverUrl/files/$fileName")
        if (response.status == HttpStatusCode.NotFound) throw MultiplayerFileNotFoundException(null)
        if (!response.status.isSuccess()) throw IOException("Peer load failed (${response.status})")
        return response.bodyAsBytes()
    }
}

internal object WebPeerBridge {
    @JSFunctor
    fun interface Ready : JSObject {
        fun run()
    }

    @JSFunctor
    fun interface Error : JSObject {
        fun show(message: String)
    }

    @JvmStatic
    @JSBody(script = "return !!(window.RTCPeerConnection && window.indexedDB && window.HTMLDialogElement);")
    external fun isSupported(): Boolean

    @JvmStatic
    @JSBody(script = "return !!(window.uncivPeer && window.uncivPeer.isConnected);")
    external fun isConnected(): Boolean

    @JvmStatic
    @JSBody(
        params = ["playerId", "onReady", "onError"],
        script = """
            if (!window.uncivPeer && !window.uncivPeerScript) {
                const script = document.createElement('script');
                script.src = new URL('scripts/unciv-peer.js', document.baseURI).href;
                window.uncivPeerScript = new Promise((resolve, reject) => {
                    script.onload = resolve;
                    script.onerror = () => {
                        delete window.uncivPeerScript;
                        script.remove();
                        reject(new Error('Could not load the WebRTC script.'));
                    };
                });
                document.head.appendChild(script);
            }
            Promise.resolve(window.uncivPeerScript)
                .then(() => window.uncivPeer.showSetup(playerId, onReady))
                .catch(error => onError(error.message));
        """
    )
    external fun showSetup(playerId: String, onReady: Ready, onError: Error)
}
