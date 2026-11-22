package com.unciv.logic.multiplayer.apiv2

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.URLProtocol
import io.ktor.http.appendPathSegments
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val pingPayload = byteArrayOf(0x31, 0x32, 0x33)
private val explicitPongPayload = byteArrayOf(0x55, 0x66, 0x77)

fun main() = runBlocking {
    val client = HttpClient { install(WebSockets) }
    check(client.engine.javaClass.name.contains("CIO", ignoreCase = true)) {
        "HttpClient() selected ${client.engine.javaClass.name}, expected the desktop CIO engine"
    }

    val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
    val serverResult = CompletableFuture<Unit>()
    val serverThread = Thread({
        try {
            serveRawPing(server)
            serverResult.complete(Unit)
        } catch (failure: Throwable) {
            serverResult.completeExceptionally(failure)
        } finally {
            runCatching { server.close() }
        }
    }, "raw-websocket-contract-server").apply { isDaemon = true; start() }

    try {
        val session = withTimeout(5_000) {
            client.uncivWebSocketRawSession {
                url {
                    protocol = URLProtocol.WS
                    host = "127.0.0.1"
                    port = server.localPort
                    appendPathSegments("raw")
                }
            }
        }
        val frame = withTimeout(5_000) { session.incoming.receive() }
        check(frame is Frame.Ping) { "Raw session exposed ${frame.frameType}, expected PING" }
        check(frame.data.contentEquals(pingPayload)) { "Raw session changed the PING payload" }

        session.outgoing.send(Frame.Pong(explicitPongPayload))
        serverResult.get(5, TimeUnit.SECONDS)
        session.outgoing.close()
        println("Desktop network contract passed")
    } finally {
        runCatching { client.close() }
        runCatching { server.close() }
        serverThread.join(1_000)
    }
}

private fun serveRawPing(server: ServerSocket) {
    server.accept().use { socket ->
        socket.soTimeout = 5_000
        val input = socket.getInputStream()
        val headers = readHttpHeaders(input)
        val websocketKey = headers.lineSequence()
            .firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?: error("WebSocket handshake did not include Sec-WebSocket-Key")
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((websocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.US_ASCII))
        )
        socket.getOutputStream().apply {
            write(("HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray(StandardCharsets.US_ASCII))
            flush()
            write(byteArrayOf(0x89.toByte(), pingPayload.size.toByte()) + pingPayload)
            flush()
        }

        // CIO may emit its own PONG; require the distinct PONG sent explicitly by the test client.
        repeat(4) {
            val incoming = readClientFrame(input)
            if (incoming.opcode == 0xA && incoming.payload.contentEquals(explicitPongPayload)) return
        }
        error("Server did not receive the explicit raw-session PONG")
    }
}

private fun readHttpHeaders(input: InputStream): String {
    val bytes = ByteArrayOutputStream()
    var crlf = 0
    while (bytes.size() < 16_384) {
        val next = input.read()
        if (next < 0) error("Peer closed before completing the WebSocket handshake")
        bytes.write(next)
        crlf = when {
            crlf == 0 && next == '\r'.code -> 1
            crlf == 1 && next == '\n'.code -> 2
            crlf == 2 && next == '\r'.code -> 3
            crlf == 3 && next == '\n'.code -> 4
            next == '\r'.code -> 1
            else -> 0
        }
        if (crlf == 4) return bytes.toString(StandardCharsets.ISO_8859_1)
    }
    error("WebSocket handshake headers exceeded 16 KiB")
}

private data class ClientFrame(val opcode: Int, val payload: ByteArray)

private fun readClientFrame(input: InputStream): ClientFrame {
    val first = input.readUnsignedByte()
    val second = input.readUnsignedByte()
    val masked = second and 0x80 != 0
    var length = (second and 0x7f).toLong()
    if (length == 126L) length = (input.readUnsignedByte() shl 8 or input.readUnsignedByte()).toLong()
    else if (length == 127L) {
        length = 0
        repeat(8) { length = (length shl 8) or input.readUnsignedByte().toLong() }
    }
    require(length <= 1_048_576) { "Unexpected oversized client frame: $length bytes" }
    val mask = if (masked) ByteArray(4).also(input::readFully) else ByteArray(0)
    val payload = ByteArray(length.toInt()).also(input::readFully)
    if (masked) payload.indices.forEach { payload[it] = (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
    return ClientFrame(first and 0x0f, payload)
}

private fun InputStream.readUnsignedByte(): Int {
    val value = read()
    if (value < 0) error("Unexpected end of WebSocket frame")
    return value
}

private fun InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        if (count < 0) error("Unexpected end of WebSocket frame")
        offset += count
    }
}
