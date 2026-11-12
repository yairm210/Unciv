/*
 * Unciv browser peer transport.
 * Copyright 2026 The Unciv contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
(() => {
    "use strict";

    const virtualOrigin = "https://peer.unciv.invalid";
    const channelLabel = "unciv-saves-v1";
    const maxFileBytes = 32 * 1024 * 1024;
    const maxTotalBytes = 256 * 1024 * 1024;
    const chunkBytes = 16 * 1024;
    const requestTimeout = 30_000;
    const defaultIceServers = [{ urls: "stun:stun.l.google.com:19302" }];

    const changes = new EventTarget();
    let current = null;
    let database;
    let setup;

    function connectionError(message) {
        return new TypeError(message);
    }

    function notify() {
        changes.dispatchEvent(new Event("change"));
    }

    function close(session, message) {
        if (!session || current !== session) return;
        current = null;
        clearTimeout(session.incoming?.timer);
        session.abort.abort(connectionError(message));
        session.channel?.close();
        session.pc.close();
        notify();
    }

    function disconnect() {
        close(current, "WebRTC peer disconnected.");
    }

    function start(role, iceServers = defaultIceServers) {
        // Keep an existing session if the requested ICE configuration is invalid.
        const pc = new RTCPeerConnection({ iceServers });
        disconnect();
        const session = {
            role, pc, abort: new AbortController(), channel: null,
            phase: "signaling", incoming: null, pending: null, serving: false,
            queue: Promise.resolve(), queued: 0
        };
        current = session;
        pc.onconnectionstatechange = () => {
            if (pc.connectionState === "failed" || pc.connectionState === "closed")
                close(session, "WebRTC connection " + pc.connectionState + ".");
        };
        pc.oniceconnectionstatechange = () => {
            if (pc.iceConnectionState === "failed") close(session, "WebRTC ICE connection failed.");
        };
        notify();
        return session;
    }

    function attach(session, channel) {
        if (current !== session || session.channel || channel.label !== channelLabel) {
            channel.close();
            return;
        }
        session.channel = channel;
        channel.binaryType = "arraybuffer";
        channel.bufferedAmountLowThreshold = 64 * 1024;
        channel.onopen = () => { if (current === session) notify(); };
        channel.onclose = () => close(session, "WebRTC peer disconnected.");
        channel.onerror = () => close(session, "WebRTC data channel failed.");
        channel.onmessage = event => {
            if (current !== session) return;
            receive(session, event.data).catch(error => close(session, error.message));
        };
        notify();
    }

    function isConnected(session = current) {
        return session?.channel?.readyState === "open";
    }

    function waitFor(session, target, event, ready, timeout, message) {
        const signal = session.abort.signal;
        signal.throwIfAborted();
        if (ready()) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => finish(connectionError(message)), timeout);
            function finish(error) {
                clearTimeout(timer);
                target.removeEventListener(event, check);
                signal.removeEventListener("abort", abort);
                if (error) reject(error);
                else resolve();
            }
            function abort() { finish(signal.reason); }
            function check() { if (ready()) finish(); }
            target.addEventListener(event, check);
            signal.addEventListener("abort", abort, { once: true });
        });
    }

    function untilDisconnected(session, operation) {
        const signal = session.abort.signal;
        let abort;
        return new Promise((resolve, reject) => {
            abort = () => reject(signal.reason);
            if (signal.aborted) abort();
            else signal.addEventListener("abort", abort, { once: true });
            operation.then(resolve, reject);
        }).finally(() => signal.removeEventListener("abort", abort));
    }

    function parseSignal(text, type) {
        if (typeof text !== "string" || text.length > 128 * 1024)
            throw new TypeError("Paste the complete WebRTC " + type + " copied from Unciv.");
        let signal;
        try { signal = JSON.parse(text); } catch (_) {
            throw new TypeError("Paste the complete WebRTC " + type + " copied from Unciv.");
        }
        if (signal?.type !== type || typeof signal.sdp !== "string" || !signal.sdp.trim())
            throw new TypeError("Paste the complete WebRTC " + type + " copied from Unciv.");
        return { type, sdp: signal.sdp };
    }

    async function gatherSignal(session, description, timeout = 20_000) {
        const { pc, abort } = session;
        await untilDisconnected(session, pc.setLocalDescription(description));
        await waitFor(session, pc, "icegatheringstatechange", () => pc.iceGatheringState === "complete",
            timeout, "ICE gathering timed out. Check the ICE server settings and try again.");
        abort.signal.throwIfAborted();
        return JSON.stringify(pc.localDescription.toJSON());
    }

    async function createHostOffer({ iceServers, iceGatherTimeoutMs } = {}) {
        const session = start("host", iceServers);
        try {
            const { pc } = session;
            attach(session, pc.createDataChannel(channelLabel, { ordered: true }));
            const offer = await gatherSignal(session, await untilDisconnected(session, pc.createOffer()), iceGatherTimeoutMs);
            session.phase = "offer";
            notify();
            return offer;
        } catch (error) {
            close(session, error.message);
            throw error;
        }
    }

    async function createJoinAnswer(text, { iceServers, iceGatherTimeoutMs } = {}) {
        const offer = parseSignal(text, "offer");
        const session = start("guest", iceServers);
        try {
            const { pc } = session;
            pc.ondatachannel = event => attach(session, event.channel);
            await untilDisconnected(session, pc.setRemoteDescription(offer));
            const answer = await gatherSignal(session, await untilDisconnected(session, pc.createAnswer()), iceGatherTimeoutMs);
            session.phase = "answer";
            notify();
            return answer;
        } catch (error) {
            close(session, error.message);
            throw error;
        }
    }

    async function acceptAnswer(text, { connectTimeoutMs = 30_000 } = {}) {
        const session = current;
        if (session?.role !== "host" || session.phase !== "offer")
            throw new Error("Create a host offer before accepting an answer.");
        const answer = parseSignal(text, "answer");
        session.phase = "connecting";
        notify();
        try {
            await untilDisconnected(session, session.pc.setRemoteDescription(answer));
            await waitFor(session, session.channel, "open", () => isConnected(session), connectTimeoutMs,
                "WebRTC could not connect. Check the network or try a TURN server.");
            session.abort.signal.throwIfAborted();
        } catch (error) {
            if (current === session && session.pc.signalingState === "have-local-offer") {
                session.phase = "offer";
                notify();
            } else {
                close(session, error.message);
            }
            throw error;
        }
    }

    function openDatabase() {
        if (database) return database;
        database = new Promise((resolve, reject) => {
            const open = indexedDB.open("unciv-webrtc", 1);
            open.onupgradeneeded = () => open.result.createObjectStore("saves");
            open.onerror = () => reject(open.error);
            open.onsuccess = () => {
                const db = open.result;
                db.onversionchange = () => {
                    db.close();
                    database = undefined;
                };
                resolve(db);
            };
        }).catch(error => {
            database = undefined;
            throw error;
        });
        return database;
    }

    async function accessFile(session, method, key, body, requestSignal) {
        const db = await openDatabase();
        const sessionSignal = session.abort.signal;
        sessionSignal.throwIfAborted();
        requestSignal?.throwIfAborted();
        return new Promise((resolve, reject) => {
            const tx = db.transaction("saves", method === "GET" ? "readonly" : "readwrite");
            const store = tx.objectStore("saves");
            let result;
            function abort() {
                try { tx.abort(); } catch (error) {
                    if (error.name !== "InvalidStateError") throw error;
                }
            }
            function cleanup() {
                sessionSignal.removeEventListener("abort", abort);
                requestSignal?.removeEventListener("abort", abort);
            }
            sessionSignal.addEventListener("abort", abort, { once: true });
            requestSignal?.addEventListener("abort", abort, { once: true });
            tx.oncomplete = () => { cleanup(); resolve(result); };
            tx.onabort = () => {
                cleanup();
                reject(sessionSignal.reason ?? requestSignal?.reason ?? tx.error ?? new Error("Peer save transaction aborted."));
            };

            const file = store.get(key);
            file.onsuccess = () => {
                const previous = file.result;
                if (method === "GET" || (method === "DELETE" && !previous)) {
                    result = previous ? reply(200, previous) : reply(404, "Peer save not found.");
                    return;
                }

                // The byte count and save contents commit in the same transaction, including across tabs.
                const usage = store.get("totalBytes");
                usage.onsuccess = () => {
                    const size = (usage.result ?? 0) - (previous?.byteLength ?? 0) + body.byteLength;
                    if (size > maxTotalBytes) {
                        result = reply(507, "Peer save storage is full.");
                        return;
                    }
                    if (method === "PUT") store.put(body, key);
                    else store.delete(key);
                    store.put(size, "totalBytes");
                    result = reply(200);
                };
            };
        });
    }

    function reply(status, body = new Uint8Array()) {
        return { status, body: typeof body === "string" ? new TextEncoder().encode(body) : body };
    }

    function fileKey(method, path) {
        if (method === "GET" && path === "/isalive") return null;
        if (method !== "GET" && method !== "PUT" && method !== "DELETE")
            throw new TypeError("Unsupported peer request method.");
        const match = /^\/files\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:_Preview)?)$/.exec(path);
        if (!match) throw new TypeError("Peer requests are limited to /isalive and UUID save files.");
        return match[1];
    }

    function localRequest(session, method, path, body, signal) {
        const key = fileKey(method, path);
        if (key === null) return Promise.resolve(reply(200, '{"authVersion":0,"chatVersion":0}'));
        return accessFile(session, method, key, body, signal);
    }

    async function transmit(session, header, body) {
        const { channel, abort } = session;
        abort.signal.throwIfAborted();
        channel.send(JSON.stringify({ ...header, length: body.byteLength }));
        const chunkLimit = Math.min(chunkBytes, session.pc.sctp?.maxMessageSize || chunkBytes);
        for (let offset = 0; offset < body.byteLength; offset += chunkLimit) {
            if (channel.bufferedAmount > 256 * 1024)
                await waitFor(session, channel, "bufferedamountlow",
                    () => channel.bufferedAmount <= channel.bufferedAmountLowThreshold,
                    requestTimeout, "WebRTC send timed out.");
            abort.signal.throwIfAborted();
            channel.send(body.subarray(offset, offset + chunkLimit));
        }
    }

    async function receive(session, message) {
        const { role } = session;
        if (typeof message === "string") {
            if (session.incoming || message.length > 1024)
                throw connectionError("Invalid WebRTC message header.");
            const header = JSON.parse(message);
            if (!Number.isInteger(header.length) || header.length < 0 || header.length > maxFileBytes)
                throw connectionError("Invalid WebRTC message length.");
            if (role === "host") {
                if (session.serving || header.type !== "request" || typeof header.path !== "string")
                    throw connectionError("Unexpected WebRTC request.");
                fileKey(header.method, header.path);
                if (header.method !== "PUT" && header.length !== 0)
                    throw connectionError("Unexpected WebRTC request body.");
            } else if (!session.pending || header.type !== "response" ||
                !Number.isInteger(header.status) || header.status < 200 || header.status > 599 ||
                ([204, 205, 304].includes(header.status) && header.length !== 0)) {
                throw connectionError("Unexpected WebRTC response.");
            }
            const timer = setTimeout(() => close(session, "WebRTC message timed out."), requestTimeout);
            session.incoming = { header, body: new Uint8Array(header.length), offset: 0, timer };
        } else {
            const incoming = session.incoming;
            if (!incoming || !(message instanceof ArrayBuffer) || message.byteLength === 0 ||
                message.byteLength > chunkBytes || incoming.offset + message.byteLength > incoming.body.byteLength)
                throw connectionError("Invalid WebRTC data chunk.");
            incoming.body.set(new Uint8Array(message), incoming.offset);
            incoming.offset += message.byteLength;
        }

        const { header, body, offset, timer } = session.incoming;
        if (offset !== body.byteLength) return;
        clearTimeout(timer);
        session.incoming = null;

        if (role === "guest") {
            session.pending.resolve({ status: header.status, body });
            return;
        }

        session.serving = true;
        try {
            let response;
            try {
                response = await localRequest(session, header.method, header.path, body);
            } catch (_) {
                response = reply(500, "The host could not access its peer save storage.");
            }
            await transmit(session, { type: "response", status: response.status }, response.body);
        } finally {
            session.serving = false;
        }
    }

    async function remoteRequest(session, method, path, body, signal) {
        signal.throwIfAborted();
        const response = new Promise(resolve => { session.pending = { resolve }; });
        const connectionSignal = session.abort.signal;
        let rejectDisconnect;
        function lostConnection() { rejectDisconnect(connectionSignal.reason); }
        function abort() { close(session, "WebRTC request was cancelled."); }
        const disconnected = new Promise((_, reject) => { rejectDisconnect = reject; });
        connectionSignal.addEventListener("abort", lostConnection, { once: true });
        signal.addEventListener("abort", abort, { once: true });
        const timer = setTimeout(() => close(session, "WebRTC request timed out."), requestTimeout);
        try {
            const sent = transmit(session, { type: "request", method, path }, body);
            const [, result] = await Promise.race([Promise.all([sent, response]), disconnected]);
            return result;
        } catch (error) {
            close(session, error.message);
            throw error;
        } finally {
            session.pending = null;
            clearTimeout(timer);
            connectionSignal.removeEventListener("abort", lostConnection);
            signal.removeEventListener("abort", abort);
        }
    }

    async function request(input, init) {
        const http = new Request(input, init);
        const url = new URL(http.url);
        if (url.origin !== virtualOrigin || url.search || url.hash)
            throw new TypeError("Invalid peer request URL.");
        fileKey(http.method, url.pathname);
        const session = current;
        if (!isConnected(session)) throw connectionError("Connect to a WebRTC peer first.");
        if (session.queued >= 8) throw connectionError("Too many queued WebRTC requests.");

        session.queued++;
        const result = session.queue.then(async () => {
            session.abort.signal.throwIfAborted();
            http.signal.throwIfAborted();
            const body = http.body ? new Uint8Array(await http.arrayBuffer()) : new Uint8Array();
            if (body.byteLength > maxFileBytes) throw new TypeError("Peer file exceeds the 32 MiB limit.");
            session.abort.signal.throwIfAborted();
            const { status, body: bytes } = session.role === "host"
                ? await localRequest(session, http.method, url.pathname, body, http.signal)
                : await remoteRequest(session, http.method, url.pathname, body, http.signal);
            const contentType = url.pathname === "/isalive" ? "application/json" : "text/plain; charset=utf-8";
            return new Response([204, 205, 304].includes(status) ? null : bytes,
                { status, headers: { "content-type": contentType } });
        }).finally(() => { session.queued--; });
        session.queue = result.catch(() => {});
        return result;
    }

    function createSetup() {
        const dialog = document.createElement("dialog");
        dialog.className = "unciv-peer";
        dialog.setAttribute("aria-labelledby", "unciv-peer-title");
        dialog.innerHTML = [
            "<style>",
            ".unciv-peer{background:#202933;color:#f4f7fa;border:1px solid #677b90;border-radius:8px;",
            "font:16px system-ui;padding:20px;width:min(36rem,calc(100vw - 56px));max-height:calc(100vh - 56px)}",
            ".unciv-peer::backdrop{background:#000a}.unciv-peer h2{margin-top:0}",
            ".unciv-peer label{display:block;margin-top:12px}.unciv-peer textarea,.unciv-peer input",
            "{box-sizing:border-box;width:100%;padding:6px;margin:4px 0;font:14px monospace}",
            ".unciv-peer button{margin:5px 6px 5px 0;padding:6px 10px}.unciv-peer details{margin:10px 0}",
            "</style>",
            '<h2 id="unciv-peer-title">WebRTC multiplayer</h2>',
            "<p>Play with one trusted peer. Share the offer and answer privately. Keep the host tab open during play.</p>",
            '<label>Your player ID <input data-player readonly></label>',
            '<details><summary>ICE servers (optional)</summary>',
            '<label>ICE server JSON <textarea data-ice placeholder="Leave blank to use public STUN"></textarea></label></details>',
            '<label>Offer or answer from your peer <textarea data-input spellcheck="false"></textarea></label>',
            '<button type="button" data-host>Create host offer</button>',
            '<button type="button" data-join>Create join answer</button>',
            '<button type="button" data-accept>Accept peer answer</button>',
            '<label>Offer or answer to send <textarea data-output readonly spellcheck="false"></textarea></label>',
            '<button type="button" data-copy>Copy offer or answer</button>',
            '<p data-status role="status"></p>',
            '<button type="button" data-continue>Continue to multiplayer</button>',
            '<button type="button" data-disconnect>Disconnect</button>',
            '<form method="dialog" style="display:inline"><button>Close</button></form>'
        ].join("");

        const element = name => dialog.querySelector("[data-" + name + "]");
        const input = element("input");
        const output = element("output");
        const status = element("status");
        const panel = { dialog, player: element("player"), onReady: null };
        let busy = false;
        let previous = null;

        function render(message) {
            if (current !== previous) {
                previous = current;
                output.value = "";
            }
            const connected = isConnected();
            element("host").disabled = busy || connected;
            element("join").disabled = busy || connected;
            element("accept").disabled = busy || connected || current?.phase !== "offer";
            element("copy").disabled = !output.value;
            element("continue").disabled = !connected;
            element("disconnect").disabled = !current;
            status.textContent = message ?? (connected ? "Connected as " + current.role + "." :
                current?.phase === "offer" ? "Send the offer to your peer, then paste and accept their answer." :
                current?.phase === "answer" ? "Send the answer to the host." :
                current ? "Connecting…" : "Disconnected");
        }

        function iceConfig() {
            const value = element("ice").value.trim();
            if (!value) return undefined;
            const iceServers = JSON.parse(value);
            if (!Array.isArray(iceServers)) throw new TypeError("ICE server JSON must be an array.");
            return iceServers;
        }

        function action(name, handler) {
            element(name).onclick = async () => {
                busy = true;
                render();
                let message;
                try { await handler(); } catch (error) { message = error.message; }
                finally { busy = false; render(message); }
            };
        }

        action("host", async () => { output.value = await createHostOffer({ iceServers: iceConfig() }); });
        action("join", async () => { output.value = await createJoinAnswer(input.value, { iceServers: iceConfig() }); });
        action("accept", () => acceptAnswer(input.value));
        element("copy").onclick = async () => {
            try {
                await navigator.clipboard.writeText(output.value);
                render("Copied.");
            } catch (_) {
                output.select();
                render("Select and copy the signaling text.");
            }
        };
        element("continue").onclick = () => {
            if (!isConnected()) return;
            dialog.close();
            panel.onReady();
        };
        element("disconnect").onclick = disconnect;
        changes.addEventListener("change", () => render());
        document.body.append(dialog);
        render();
        return panel;
    }

    function showSetup(playerId, onReady) {
        setup ??= createSetup();
        setup.onReady = onReady;
        setup.player.value = playerId;
        if (!setup.dialog.open) setup.dialog.showModal();
    }

    window.addEventListener("pagehide", disconnect);
    window.uncivPeer = {
        virtualOrigin,
        get isConnected() { return isConnected(); },
        createHostOffer, createJoinAnswer, acceptAnswer, disconnect, request, showSetup,
        limits: Object.freeze({ maxFileBytes })
    };
})();
