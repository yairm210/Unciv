package com.unciv.platform

object PlatformCapabilities {
    enum class WebProfile {
        PHASE1,
        PHASE3_ALPHA,
        PHASE3_BETA,
        PHASE3_FULL,
        PHASE4_FULL,
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
    fun webDefaultsProfile(): WebProfile = WebProfile.PHASE4_FULL

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
    fun webPhase4Full(): Features = webPhase3Full()

    @JvmStatic
    fun webPhase4Staging(): Staging = Staging(
        onlineMultiplayer = CapabilityStage.ENABLED,
        customFileChooser = CapabilityStage.ENABLED,
        onlineModDownloads = CapabilityStage.ENABLED,
        systemFontEnumeration = CapabilityStage.DISABLED,
    )

    @JvmStatic
    fun webProfileFeatures(profile: WebProfile): Features = when (profile) {
        WebProfile.PHASE1 -> webPhase1()
        WebProfile.PHASE3_ALPHA -> webPhase3Alpha()
        WebProfile.PHASE3_BETA -> webPhase3Beta()
        WebProfile.PHASE3_FULL -> webPhase3Full()
        WebProfile.PHASE4_FULL -> webPhase4Full()
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
        WebProfile.PHASE3_FULL -> webPhase4Staging()
        WebProfile.PHASE4_FULL -> webPhase4Staging()
    }

    @JvmStatic
    fun applyWebFeatureRollbacks(
        base: Features,
        disableAll: Boolean,
        disableMultiplayer: Boolean,
        disableFileChooser: Boolean,
        disableModDownloads: Boolean,
    ): Features {
        if (disableAll) {
            return base.copy(
                onlineMultiplayer = false,
                customFileChooser = false,
                onlineModDownloads = false,
                systemFontEnumeration = false,
            )
        }
        return base.copy(
            onlineMultiplayer = if (disableMultiplayer) false else base.onlineMultiplayer,
            customFileChooser = if (disableFileChooser) false else base.customFileChooser,
            onlineModDownloads = if (disableModDownloads) false else base.onlineModDownloads,
        )
    }

    @JvmStatic
    fun applyWebStagingRollbacks(
        base: Staging,
        disableAll: Boolean,
        disableMultiplayer: Boolean,
        disableFileChooser: Boolean,
        disableModDownloads: Boolean,
    ): Staging {
        if (disableAll) {
            return base.copy(
                onlineMultiplayer = CapabilityStage.DISABLED,
                customFileChooser = CapabilityStage.DISABLED,
                onlineModDownloads = CapabilityStage.DISABLED,
                systemFontEnumeration = CapabilityStage.DISABLED,
            )
        }
        return base.copy(
            onlineMultiplayer = if (disableMultiplayer) CapabilityStage.DISABLED else base.onlineMultiplayer,
            customFileChooser = if (disableFileChooser) CapabilityStage.DISABLED else base.customFileChooser,
            onlineModDownloads = if (disableModDownloads) CapabilityStage.DISABLED else base.onlineModDownloads,
        )
    }

    @JvmStatic
    fun hasWebRollbacksApplied(
        disableAll: Boolean,
        disableMultiplayer: Boolean,
        disableFileChooser: Boolean,
        disableModDownloads: Boolean,
    ): Boolean = disableAll || disableMultiplayer || disableFileChooser || disableModDownloads

    @JvmStatic
    fun describeWebRollbacks(
        disableAll: Boolean,
        disableMultiplayer: Boolean,
        disableFileChooser: Boolean,
        disableModDownloads: Boolean,
    ): String {
        val tokens = mutableListOf<String>()
        if (disableAll) tokens += "all"
        if (disableMultiplayer) tokens += "multiplayer"
        if (disableFileChooser) tokens += "fileChooser"
        if (disableModDownloads) tokens += "mods"
        return if (tokens.isEmpty()) "none" else tokens.joinToString(",")
    }

    @JvmStatic
    fun profileFromLabel(rawLabel: String?): WebProfile? {
        val label = rawLabel?.trim()?.lowercase() ?: return null
        return when (label) {
            "phase1" -> WebProfile.PHASE1
            "phase3-alpha", "phase3_alpha", "phase4-alpha", "phase4_alpha", "alpha" -> WebProfile.PHASE3_ALPHA
            "phase3-beta", "phase3_beta", "phase4-beta", "phase4_beta", "beta" -> WebProfile.PHASE3_BETA
            "phase3-full", "phase3_full", "full" -> WebProfile.PHASE3_FULL
            "phase4-full", "phase4_full", "phase4", "prod", "production" -> WebProfile.PHASE4_FULL
            else -> null
        }
    }

    @JvmStatic
    fun profileLabel(profile: WebProfile): String = when (profile) {
        WebProfile.PHASE1 -> "phase1"
        WebProfile.PHASE3_ALPHA -> "phase3-alpha"
        WebProfile.PHASE3_BETA -> "phase3-beta"
        WebProfile.PHASE3_FULL -> "phase3-full"
        WebProfile.PHASE4_FULL -> "phase4-full"
    }
}
