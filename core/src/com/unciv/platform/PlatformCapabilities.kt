package com.unciv.platform

object PlatformCapabilities {
    enum class WebProfile {
        PHASE1,
        PHASE3_ALPHA,
        PHASE3_BETA,
        PHASE3_FULL,
    }

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

    @JvmStatic
    fun webPhase3Alpha(): Features = Features(
        onlineMultiplayer = false,
        customFileChooser = true,
        onlineModDownloads = false,
        systemFontEnumeration = false,
        backgroundThreadPools = false,
    )

    @JvmStatic
    fun webPhase3Beta(): Features = Features(
        onlineMultiplayer = false,
        customFileChooser = true,
        onlineModDownloads = true,
        systemFontEnumeration = false,
        backgroundThreadPools = false,
    )

    @JvmStatic
    fun webPhase3Full(): Features = Features(
        onlineMultiplayer = true,
        customFileChooser = true,
        onlineModDownloads = true,
        systemFontEnumeration = false,
        backgroundThreadPools = false,
    )

    @JvmStatic
    fun webProfileFeatures(profile: WebProfile): Features = when (profile) {
        WebProfile.PHASE1 -> webPhase1()
        WebProfile.PHASE3_ALPHA -> webPhase3Alpha()
        WebProfile.PHASE3_BETA -> webPhase3Beta()
        WebProfile.PHASE3_FULL -> webPhase3Full()
    }

    @JvmStatic
    fun webProfileStaging(profile: WebProfile): Staging = when (profile) {
        WebProfile.PHASE1 -> Staging(
            onlineMultiplayer = CapabilityStage.DISABLED,
            customFileChooser = CapabilityStage.DISABLED,
            onlineModDownloads = CapabilityStage.DISABLED,
            systemFontEnumeration = CapabilityStage.DISABLED,
        )
        WebProfile.PHASE3_ALPHA -> webPhase3Staging()
        WebProfile.PHASE3_BETA -> Staging(
            onlineMultiplayer = CapabilityStage.BETA,
            customFileChooser = CapabilityStage.BETA,
            onlineModDownloads = CapabilityStage.BETA,
            systemFontEnumeration = CapabilityStage.DISABLED,
        )
        WebProfile.PHASE3_FULL -> Staging(
            onlineMultiplayer = CapabilityStage.ENABLED,
            customFileChooser = CapabilityStage.ENABLED,
            onlineModDownloads = CapabilityStage.ENABLED,
            systemFontEnumeration = CapabilityStage.DISABLED,
        )
    }
}
