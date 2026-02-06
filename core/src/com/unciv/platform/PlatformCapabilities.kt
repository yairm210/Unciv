package com.unciv.platform

object PlatformCapabilities {
    data class Features(
        val onlineMultiplayer: Boolean = true,
        val customFileChooser: Boolean = true,
        val onlineModDownloads: Boolean = true,
        val systemFontEnumeration: Boolean = true,
        val backgroundThreadPools: Boolean = true,
    )

    @JvmField
    var current: Features = Features()

    @JvmStatic
    fun setCurrent(features: Features) {
        current = features
    }

    @JvmStatic
    fun webPhase1(): Features = Features(
        onlineMultiplayer = false,
        customFileChooser = false,
        onlineModDownloads = false,
        systemFontEnumeration = false,
        backgroundThreadPools = false,
    )
}
