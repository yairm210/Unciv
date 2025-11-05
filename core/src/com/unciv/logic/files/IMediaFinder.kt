package com.unciv.logic.files

import com.badlogic.gdx.Application
import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.audio.MusicController
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.options.SettingsSelect
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.full.declaredMemberProperties

/**
 *  Encapsulate how media files are found and enumerated.
 *
 *  ## API requirements
 *  - Map a name to a FileHandle
 *  - Enumerate folders over all active mods
 *  - Enumerate files over all active mods
 *  - Allow fine-tuning where the list of mods comes from
 *  - By default, "active mods" means selected in a running game, or selected as permanent audiovisual mod, and including builtin, in that order.
 *
 *  ## Instructions
 *  - Inherit the interface or one of the specializations [Sounds], [Music], [Images], [Voices]
 *  - Simply call [findMedia], [listMediaFolders] or [listMediaFiles]
 *  - If you need a specialization but your host already has a superclass - use delegation:
 *    `class SoundPlayer : Popup(), IMediaFinder by IMediaFinder.Sounds() { ... }`
 *  - Direct instantiation is fine too when the call is simple.
 *
 *  ## Usages
 *  - OptionsPopup multiplayer notification sounds: [multiplayerTab][com.unciv.ui.popups.options.MultiplayerTab]
 *  - todo, prepared: [SoundPlayer.getFolders]
 *  - todo, prepared: [MusicController.getAllMusicFiles]
 *  - todo, prepared: Leader voices: [MusicController.playVoice]
 *  - todo, prepared: ExtraImages: [ImageGetter.findExternalImage], [FormattedLine.extraImage]
 *  - todo: Particle effect definitions: Open PR
 *
 *  ## Caveats
 *  - FileHandle.list() won't work for internal folders when running a desktop jar (unless the assets in extracted form are actually available)
 *  - FileHandle.exists() - doc claims it won't work on Android for internal _folders_. Cannot repro on Android S, but respected here nonetheless.
 *  - FileHandle.exists() - doc quote: "Note that this can be very slow for internal files on Android!" (meaning files).
 *      I disagree - that's very true if there's an obb, so the zip directory has to be scanned.
 *      But otherwise, asking the Android asset manager should not be too bad - better than file access since SAF anyway.
 *  - FileHandle.isDirectory - won't work for internal folders when running a desktop jar
 *    Doc: "On Android, an Files.FileType.Internal handle to an empty directory will return false. On the desktop, an Files.FileType.Internal handle to a directory on the classpath will return false."
 */
interface IMediaFinder {
    //////////////////////////////////////////// Control

    /** Set of supported extensions **including the leading dot**.
     *  - use `setOf("")` if the [findMedia] API should not guess extensions but require the name parameter to only match full names.
     *  - supplying "" ***and*** a list of extensions will make [findMedia] accept both an explicit full name and have it guess extensions.
     *  - Don't use emptyset() - that will cause [findMedia] to always return `null`.
     */
    val supportedMediaExtensions: Set<String>

    /** Name of assets subfolder.
     *  - Will be interpreted as a direct child of a Mod folder or the Unciv internal assets folder.
     */
    val mediaSubFolderName: String

    /** Access the current Ruleset.
     *  - Defaults to [UncivGame.getGameInfoOrNull]`()?.ruleset`.
     *  - Override to provide a direct source to enumerate game mods.
     *  @return `null` if no game is loaded - only permanent audiovisual mods and builtin are valid sources
     */
    fun getRuleset(): Ruleset? = UncivGame.getGameInfoOrNull()?.ruleset

    /** Supply a list of possible file names for the builtin folder.
     *  - The children of builtin folders cannot be enumerated.
     *  - Thus [listMediaFiles] will throw unless this is overridden.
     */
    fun getInternalMediaNames(folder: FileHandle): Sequence<String> =
        throw UnsupportedOperationException("Using IMediaFinder.listMediaFiles from a jar requires overriding getInternalMediaNames")

    //////////////////////////////////////////// API

    /** Find a specific asset by name.
     *  - Calls [listMediaFolders] and looks in each candidate folder.
     *  - [supportedMediaExtensions] are the extensions searched.
     */
    fun findMedia(name: String): FileHandle? = listMediaFolders()
        .flatMap { folder -> supportedMediaExtensions.map { folder.child(name + it) } }
        .firstOrNull { it.exists() }

    /** Enumerate all candidate media folders according to current Ruleset mod choice, Permanent audiovisual mods, and builtin sources.
     *  - Remember builtin are under Gdx.internal and will not support listing children on most build types.
     */
    fun listMediaFolders(): Sequence<FileHandle> =
        (listModFolders() + Gdx.files.internal(mediaSubFolderName))
        .filter { it.directoryExists() }

    /** Enumerate all media files.
     *  - Existence is ensured.
     */
    fun listMediaFiles(): Sequence<FileHandle> = listMediaFolders().flatMap { folder ->
        if (folder.type() == Files.FileType.Internal && isRunFromJar())
            getInternalMediaNames(folder).flatMap { name ->
                supportedMediaExtensions.map { ext ->
                    folder.child(name + ext)
                }
            }.filter { it.exists() }
        else folder.list().asSequence()
    }

    //////////////////////////////////////////// Internal helpers

    fun getModMediaFolder(modName: String): FileHandle =
        UncivGame.Current.files.getModFolder(modName).child(mediaSubFolderName)

    private fun FileHandle.directoryExists() = when {
        type() != Files.FileType.Internal -> exists() && isDirectory
        Gdx.app.type == Application.ApplicationType.Android -> isDirectory  // We accept that an empty folder is no folder in this case
        isRunFromJar() -> exists()
        else -> exists() && isDirectory
    }

    private fun listModFolders() = sequence {
        // Order determines winner if several sources contain the same asset!
        // todo: Can there be a deterministic priority/ordering within game mods or visualMods?

        // Mods chosen in the running game go first
        // - including BaseRuleset (which can be Vanilla/G&K but those don't have folders under local/mods), which always is first in Ruleset.mods
        getRuleset()?.run { yieldAll(mods) }
        // Permanent audiovisual mods next
        if (UncivGame.isCurrentInitialized())
            yieldAll(UncivGame.Current.settings.visualMods)
        // Our caller will append the one builtin folder candidate (not here, as it's internal instead of local)
    }.map { getModMediaFolder(it) }

    //////////////////////////////////////////// Specializations

    companion object {
        private fun supportedAudioExtensions() = setOf(".mp3", ".ogg", ".wav")   // Per Gdx docs, no aac/m4a
        private fun supportedImageExtensions() = setOf(".png", ".jpg", ".jpeg")
        private fun isRunFromJar(): Boolean =
            Gdx.app.type == Application.ApplicationType.Desktop &&
            this::class.java.`package`.specificationVersion != null
    }

    open class Sounds : IMediaFinder {
        override val mediaSubFolderName = "sounds"
        override val supportedMediaExtensions = supportedAudioExtensions()

        private val uncivSoundNames by lazy { uncivSoundNames().toList() }
        private val unitAttackSounds by lazy { unitAttackSounds().map { it.second }.toList() }

        override fun getInternalMediaNames(folder: FileHandle) = uncivSoundNames.asSequence() + unitAttackSounds

        protected companion object {
            // Warning: reflection monster to enumerate a non-enum.
            fun uncivSoundNames() = UncivSound.Companion::class.declaredMemberProperties.asSequence()
                .map { (it.get(UncivSound.Companion) as UncivSound).fileName }

            // Extract Unit attack sounds from the larger vanilla ruleset
            // Remember this replaces enumeration over *bundled* assets - not necessary for mods
            // Keeps unit around not for this class but for the labeled version
            fun unitAttackSounds(): Sequence<Pair<BaseUnit, String>> {
                val ruleset = RulesetCache[BaseRuleset.Civ_V_GnK.fullName] ?: return emptySequence()
                return ruleset.units.values.asSequence()
                    .filter { it.attackSound != null }
                    .distinctBy { it.attackSound }
                    .map { it to it.attackSound!! }
            }
        }
    }

    open class Music : IMediaFinder {
        override val mediaSubFolderName = "music"
        override val supportedMediaExtensions = supportedAudioExtensions()
        val names = sequenceOf("Thatched Villagers - Ambient")
        override fun getInternalMediaNames(folder: FileHandle) = names
    }

    open class Voices : IMediaFinder {
        override val mediaSubFolderName = "voices"
        override val supportedMediaExtensions = supportedAudioExtensions()
    }

    open class Images : IMediaFinder {
        override val mediaSubFolderName = "ExtraImages"
        override val supportedMediaExtensions = supportedImageExtensions()
        // no getInternalMediaNames - no listMediaFiles() for internal assets needed
    }

    /** Specialized subclass to provide all accessible sounds with a human-readable label.
     *  - API: Use [getLabeledSounds] only.
     *  - Note: Redesign if UncivSound should ever be made into or use an Enum, to store the label there.
     */
    open class LabeledSounds : Sounds() {
        /** Holds an UncivSound with a prettified UI label for use in a SelectBox */
        class UncivSoundLabeled(label: String, value: UncivSound) : SettingsSelect.SelectItem<UncivSound>(label, value) {
            constructor(entry: Map.Entry<String, String>) : this(entry.value, UncivSound(entry.key))
        }
        /** Translate a reference for an UncivSound (stored in settings) -> UncivSoundLabeled field that can be used as callable reference for addSelectBox */
        class UncivSoundProxy(private val property: KMutableProperty0<UncivSound>) {
            var value: UncivSoundLabeled
                get() = UncivSoundLabeled("", property.get()) // can do without pretty label as it's used only for selection
                set(value) { property.set(value.value) }
        }

        private companion object {
            // Also determines display order
            val prettifyUncivSoundNames = mapOf(
                "" to "None",
                "notification1" to "Notification [1]",
                "notification2" to "Notification [2]",
                "coin" to "Buy",
                "construction" to "Create",
                "paper" to "Pick a tech",
                "policy" to "Adopt policy",
                "setup" to "Set up",
                "swap" to "Swap units",
            )
            // Previous code in createNotificationSoundOptions also excluded UncivSound.Whoosh - now included
            val exclusions = setOf("nuke", "fire", "slider")
        }

        private val cache = mutableMapOf<String, String>()

        fun getLabeledSounds(): Iterable<UncivSoundLabeled> {
            fillCache()
            return cache.asSequence()
                .map { UncivSoundLabeled(it) }
                .asIterable()
        }

        override fun listMediaFolders(): Sequence<FileHandle> =
            throw UnsupportedOperationException("LabeledSounds does not support the normal IMediaFinder API")

        private fun fillCache() {
            if (cache.isNotEmpty()) return
            cacheBuiltins()
            cacheMods()
            for (sound in exclusions) cache.remove(sound)
        }

        private fun cacheBuiltins() {
            cache.putAll(prettifyUncivSoundNames)
            for (sound in uncivSoundNames())
                if (sound !in cache)
                    cache[sound] = sound.replaceFirstChar(Char::titlecase)
            for ((unit, sound) in unitAttackSounds())
                cache[sound] = "[${unit.name}] Attack Sound"
        }

        private fun cacheMods() {
            if (!UncivGame.isCurrentInitialized()) return
            for (folder in super.listMediaFolders()) {
                if (folder.type() == Files.FileType.Internal) continue
                val mod = folder.parent().name()
                val ruleset = RulesetCache[mod]
                if (ruleset != null) {
                    for (unit in ruleset.units.values) {
                        val sound = unit.attackSound ?: continue
                        if (sound in cache) continue
                        cache[sound] = "${mod.take(32)}: {[${unit.name}] Attack Sound}"
                    }
                }
                for (file in folder.list()) {
                    val sound = file.nameWithoutExtension()
                    if (sound in cache) continue
                    cache[sound] = "${mod.take(32)}: ${sound.replaceFirstChar(Char::titlecase)} {}"
                }
            }
        }
    }
}
