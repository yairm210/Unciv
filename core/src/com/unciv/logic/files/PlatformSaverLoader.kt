package com.unciv.logic.files

import java.io.InputStream
import java.io.OutputStream

/**
 * Contract for platform-specific helper classes to provide streams to arbitrary external
 * locations, chosen interactively by the user.
 *
 * Implementation note: If a location is obtained via [requestLoadLocation] and the same file is
 * later saved with [requestSaveLocation]/[saveGame], the suggestedLocation passed should be the
 * location returned then.
 */
interface PlatformSaverLoader {

    /** Ask the platform for a place to write to.
     *  @param mimeType Advertised to the platform's file picker (e.g. to suggest a default
     *      extension/icon on Android). Not all platforms use this; pass the real type of what
     *      you're about to write regardless, since callers always know it upfront.
     *  @param onLocationChosen Receives an open, writable stream and the resulting location.
     *      The caller owns the stream from this point — writing to it, handling I/O failures, and
     *      closing it are all the caller's responsibility, not the implementation's.
     *  @param onError Called only for failures *obtaining* a location (permission denied,
     *      provider returned null, or user cancellation as [Cancelled]). Never called after
     *      [onLocationChosen] has already fired.
     */
    fun requestSaveLocation(
        suggestedLocation: String,
        mimeType: String,
        onLocationChosen: (stream: OutputStream, location: String) -> Unit,
        onError: (ex: Exception) -> Unit = {}
    )

    /** Ask the platform for a place to read from.
     *  @param onLocationChosen Receives an open, readable stream and the resulting location.
     *      The caller owns the stream from this point onward, same as in [requestSaveLocation].
     *  @param onError Called only for failures obtaining a location, see [requestSaveLocation].
     */
    fun requestLoadLocation(
        onLocationChosen: (stream: InputStream, location: String) -> Unit,
        onError: (ex: Exception) -> Unit = {}
    )

    /** Convenience wrapper around [requestSaveLocation] for saving a game already encoded as String.
     *  See [requestSaveLocation] for the save-location/exception-handling contract.
     *  [isZipped] only affects the advertised mimeType. */
    fun saveGame(
        data: String,
        isZipped: Boolean = false,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit = {},
        onError: (ex: Exception) -> Unit = {}
    ) {
        requestSaveLocation(
            suggestedLocation,
            if (isZipped) ZIP_SAVE_MIME_TYPE else PLAIN_SAVE_MIME_TYPE,
            onLocationChosen = { stream, location ->
                try {
                    stream.writer().use { it.write(data) }
                    onSaved(location)
                } catch (ex: Exception) {
                    onError(ex)
                }
            },
            onError = onError
        )
    }

    /** Convenience wrapper around [requestLoadLocation] for loading a String.
     *  See [requestLoadLocation] for the load-location/exception-handling contract. */
    fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        requestLoadLocation(
            onLocationChosen = { stream, location ->
                try {
                    val data = stream.reader().use { it.readText() }
                    onLoaded(data, location)
                } catch (ex: Exception) {
                    onError(ex)
                }
            },
            onError = onError
        )
    }

    /** Invisible Exception can be used with onError callbacks to indicate the User cancelled the operation and needs no message */
    class Cancelled : Exception()

    companion object {
        const val PLAIN_SAVE_MIME_TYPE = "application/json"
        const val ZIP_SAVE_MIME_TYPE = "application/gzip"

        val None = object : PlatformSaverLoader {
            override fun requestSaveLocation(
                suggestedLocation: String,
                mimeType: String,
                onLocationChosen: (OutputStream, String) -> Unit,
                onError: (Exception) -> Unit
            ) {}

            override fun requestLoadLocation(
                onLocationChosen: (InputStream, String) -> Unit,
                onError: (Exception) -> Unit
            ) {}
        }
    }
}
