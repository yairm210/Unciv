package com.unciv.platform

object PlatformCapabilities {
    enum class CapabilityStage {
        DISABLED,
        ALPHA,
        BETA,
        ENABLED,
    }

    data class Features(
        val onlineMultiplayer: Boolean = true,
        val customFileChooser: Boolean = true,
        val onlineModDownloads: Boolean = true,
        val systemFontEnumeration: Boolean = true,
        val backgroundThreadPools: Boolean = true,
    )

    data class Staging(
        val onlineMultiplayer: CapabilityStage = CapabilityStage.ENABLED,
        val customFileChooser: CapabilityStage = CapabilityStage.ENABLED,
        val onlineModDownloads: CapabilityStage = CapabilityStage.ENABLED,
        val systemFontEnumeration: CapabilityStage = CapabilityStage.ENABLED,
    )

    @JvmField
    var current: Features = Features()

    @JvmField
    var currentStaging: Staging = Staging()

    @JvmStatic
    fun setCurrent(features: Features) {
        current = features
    }

    @JvmStatic
    fun setCurrentStaging(staging: Staging) {
        currentStaging = staging
    }

    @JvmStatic
    fun webPhase1(): Features = Features(
        onlineMultiplayer = false,
        customFileChooser = false,
        onlineModDownloads = false,
        systemFontEnumeration = false,
        backgroundThreadPools = false,
    )

    @JvmStatic
    fun webPhase3Staging(): Staging = Staging(
        onlineMultiplayer = CapabilityStage.ALPHA,
        customFileChooser = CapabilityStage.BETA,
        onlineModDownloads = CapabilityStage.ALPHA,
        systemFontEnumeration = CapabilityStage.DISABLED,
    )
}
