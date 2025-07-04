package com.unciv.app.desktop

import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.Clipboard
import com.unciv.ui.audio.MusicController

/**
 *  ### Notes
 *  - Since this is the one replacement wrapping Lwjgl3Application that will be running Unciv-desktop, and we want to replace Gdx.app.clipboard,
 *    the hook for [AwtClipboard] is implemented here as [getClipboard] override.
 *  - ***This class is never properly initialized before use!*** (because the super constructor runs until the game is quit.)
 *    Therefore, fields must be initialized on-demand or from upstream UncivGame
 *
 *  ### Problem
 *  Not all exceptions playing Music can be caught on the desktop platform using a try-catch around the play method.
 *  Unciv 3.17.13 to 4.0.5 all exacerbated the problem due to using Music from various threads - and Gdx documents it isn't threadsafe.
 *  But even with that fixed, music streams can have codec failures _after_ the first buffer's worth of data, so the problem is only mitigated.
 *
 *  Sooner or later some Exception will be thrown from the code under `Lwjgl3Application.loop` -> `OpenALLwjgl3Audio.update` ->
 *  `OpenALMusic.update` -> `OpenALMusic.fill`, where a Gdx app _cannot_ catch them and has no chance to recover gracefully.
 *
 *  This catches those Exceptions and reports them through a callback mechanism, and also provides a callback from the app loop
 *  that allows MusicController to make its Music calls on a thread guaranteed to be safe for OpenALMusic.
 *
 *  ### Approach:
 *  - Subclass [OpenALLwjgl3Audio] overriding [update][OpenALLwjgl3Audio.update] with equivalent code catching any Exceptions and Errors
 *  - Get the framework to use the subclassed Audio by overriding Lwjgl3ApplicationBase.createAudio
 *
 *  ### Some exceptions so far seen:
 *  * Cannot store to object array because "this.mode_param" is null
 *  * BufferOverflowException from java.nio.DirectByteBuffer.put(DirectByteBuffer.java:409)
 *  * GdxRuntimeException: Error reading audio data from Mp3$Music.read(Mp3.java:90)
 *  * Unable to allocate audio buffers. AL Error: 40961 from OpenALMusic.play(OpenALMusic.java:83)
 *  * Unable to allocate audio buffers. AL Error: 40963 from OpenALMusic.play(OpenALMusic.java:83)
 *  * Unable to allocate audio buffers. AL Error: 40964 from OpenALMusic.play(OpenALMusic.java:83)
 *  * ArrayIndexOutOfBoundsException: arraycopy: last destination index 1733 out of bounds for byte[1732] from PushbackInputStream.unread(PushbackInputStream.java:232)
 *  * ArrayIndexOutOfBoundsException: arraycopy: length -109 is negative from OggInputStream.readPCM(OggInputStream.java:319)
 *  * IllegalArgumentException: newPosition > limit: (29308 > 4608) from java.nio.Buffer.position(Buffer.java:316)
 *  * IllegalArgumentException: newPosition < 0: (11520 < 0)
 *  * java.nio.BufferOverflowException at java.base/java.nio.ByteBuffer.put(ByteBuffer.java:1179)
 *  * [gdx-audio] Error reading OGG: Corrupt or missing data in bitstream.
 *  * ArithmeticException in LayerIIIDecoder:904
 *  * javazoom.jl.decoder.BitstreamException: Bitstream errorcode 102
 *  * NullPointerException: Cannot invoke "javazoom.jl.decoder.Bitstream.closeFrame()" because "this.bitstream" is null
 */
class HardenGdxAudio(
    game: DesktopGame,
    config: Lwjgl3ApplicationConfiguration
) : Lwjgl3Application(game, config) {
    private var updateCallback: (()->Unit)? = null
    private var exceptionHandler: ((Throwable, Music)->Unit)? = null
    private lateinit var awtClipboard: AwtClipboard  // normal initialization won't run, including initializing lazy delegates!

    /** Hooks part 1
     *
     *  This installs our extended version of OpenALLwjgl3Audio into Gdx from within the Lwjgl3Application constructor
     *  Afterwards, Music/Sound decoder crashes are handled silently - nothing, not even a console log entry.
     */
    override fun createAudio(config: Lwjgl3ApplicationConfiguration?) =
        HardenedGdxAudio()

    /** Hooks part 2
     *
     *  This installs callbacks into [MusicController] that
     *  - allow handling the exceptions - see [MusicController.getAudioExceptionHandler].
     *  - and also allow MusicController to use the loop as timing source instead of a [Timer][java.util.Timer] - see [MusicController.getAudioLoopCallback].
     */
    fun installHooks(
        updateCallback: (()->Unit)?,
        exceptionHandler: ((Throwable, Music)->Unit)?
    ) {
        this.updateCallback = updateCallback
        this.exceptionHandler = exceptionHandler
    }

    /** This redirects `Gdx.app.clipboard` on desktop to our [AwtClipboard] replacement */
    override fun getClipboard(): Clipboard {
        if (!::awtClipboard.isInitialized) awtClipboard = AwtClipboard()
        return awtClipboard
    }

    /**
     *  Desktop implementation of the [Audio][com.badlogic.gdx.Audio] interface that
     *  unlike its superclass catches exceptions and allows passing them into application code for handling.
     *
     *  Notes:
     *  - Uses reflection to avoid reimplementing the entire thing.
     *  - Accesses the super private field noDevice - once. Yes their constructor runs before ours.
     *  - Accesses super.music and copies a reference - thus size and element access are "live".
     *  - An alternative might be to try-catch in an OpenALMusic wrapper, that would be reflection-free
     *    but quite a few lines more (override OpenALLwjgl3Audio.newMusic).
     */
    inner class HardenedGdxAudio : OpenALLwjgl3Audio() {
        private val noDevice: Boolean
        private val music: Array<OpenALMusic>

        init {
            val myClass = this::class.java
            val noDeviceField = myClass.superclass.declaredFields.first { it.name == "noDevice" }
            noDeviceField.isAccessible = true
            noDevice = noDeviceField.getBoolean(this)
            val musicField = myClass.superclass.declaredFields.first { it.name == "music" }
            musicField.isAccessible = true
            @Suppress("UNCHECKED_CAST") // See OpenALLwjgl3Audio line 86 - it's a Gdx Array of OpenALMusic alright.
            music = musicField.get(this) as Array<OpenALMusic>
        }

        /**
         *  This is just a kotlin translation of the original [update][OpenALLwjgl3Audio.update] with added try-catch and cleanup
         *
         *  ## Important: This _must_ stay in sync if ever upstream Gdx changes that function.
         *  This current version corresponds to this upstream source:
         *  ```java
         *  	public void update () {
         * 		    if (noDevice) return;
         * 		    for (int i = 0; i < music.size; i++)
         * 			    music.items[i].update();
         *      }
         *  ```
         *  Note you ***cannot*** use Studio's automatic java-to-kotlin translation: In this case, it goes awry.
         *
         *  "```for (i in 0 until music.size) music.items[i].update()```" is ***not*** equivalent!
         *  It tests the end each loop iteration against the size at the moment the loop was entered, not the current size.
         */
        override fun update() {
            if (noDevice) return
            var i = 0  // No for loop as the array might be changed
            while (i < music.size) {
                val item = music[i]
                try {
                    item.update()
                } catch (ex: Throwable) {
                    item.dispose() // this will call stop which will do audio.music.removeValue
                    exceptionHandler?.invoke(ex, item)
                }
                i++
            }
            updateCallback?.invoke()
        }
    }
}

/*
    Getting Gdx to play other music formats might work along these lines:
    (Note - this is Lwjgl3 only, one would have to repeat per platform with quite different actual
     implementations, though DefaultAndroidAudio just calls the SDK's MediaPlayer so it likely
     already supports m4a, flac, opus and others...)

    class AacMusic(audio: OpenALLwjgl3Audio, file: FileHandle) : OpenALMusic(audio, file) {
        override fun read(buffer: ByteArray?): Int {
            //...
        }
        override fun reset() {
            //...
        }
    }
    fun registerCodecs(audio: OpenALLwjgl3Audio) {
        audio.registerMusic("m4a") { a, f -> AacMusic(a, f) }
    }
*/
