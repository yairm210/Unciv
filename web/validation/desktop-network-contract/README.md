# Desktop Ktor networking contract

This JVM fixture compiles the production `RawWebSocketSession.kt` helper with Ktor 3.5.2. It checks that `HttpClient()` discovers CIO on the desktop dependency graph, then uses the actual helper against a bounded loopback WebSocket peer. The peer sends a Ping; the client must expose that raw Ping frame and explicitly send a distinct Pong payload back. This protects the manual control-frame behavior used by API v2.

Run from the Unciv root:

```powershell
./gradlew.bat -p web/validation/desktop-network-contract run --console=plain --no-daemon
```

The fixture uses a 5-second timeout for the handshake/frame exchange and a local-only ephemeral port; it does not contact a remote service.
