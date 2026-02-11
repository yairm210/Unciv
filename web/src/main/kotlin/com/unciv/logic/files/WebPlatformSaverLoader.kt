package com.unciv.logic.files

import com.unciv.logic.UncivShowableException

class WebPlatformSaverLoader : PlatformSaverLoader {
    override fun saveGame(
        data: String,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        val suggestedName = sanitizeName(suggestedLocation, "unciv-save.json")
        if (WebFileInterop.isTestFileStoreEnabled()) {
            WebFileInterop.writeTestStoreText(suggestedName, data)
            onSaved(suggestedName)
            return
        }
        WebFileInterop.saveText(
            data,
            suggestedName,
            arrayOf("json", "txt", "unciv"),
            object : WebFileInterop.SaveCallback {
                override fun handle(location: String) {
                    onSaved(location)
                }
            },
            object : WebFileInterop.ErrorCallback {
                override fun handle(message: String) {
                    onError(mapError(message))
                }
            }
        )
    }

    override fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (WebFileInterop.isTestFileStoreEnabled()) {
            val name = WebFileInterop.getTestStoreLastName()
            if (name.isNullOrBlank()) {
                onError(mapError("EMPTY"))
                return
            }
            val data = WebFileInterop.readTestStoreText(name)
            if (data == null) {
                onError(mapError("EMPTY"))
                return
            }
            onLoaded(data, name)
            return
        }
        WebFileInterop.loadText(
            arrayOf("json", "txt", "unciv"),
            object : WebFileInterop.LoadTextCallback {
                override fun handle(data: String, name: String) {
                    onLoaded(data, name)
                }
            },
            object : WebFileInterop.ErrorCallback {
                override fun handle(message: String) {
                    onError(mapError(message))
                }
            }
        )
    }

    private fun sanitizeName(raw: String, fallback: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return fallback
        return trimmed.substringAfterLast('/').substringAfterLast('\\')
    }

    private fun mapError(message: String?): Exception {
        val cleaned = message?.trim().orEmpty()
        if (cleaned.equals("CANCELLED", ignoreCase = true)) {
            return PlatformSaverLoader.Cancelled()
        }
        return UncivShowableException(if (cleaned.isEmpty()) "File operation failed" else cleaned)
    }
}
