package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic
import com.badlogic.gdx.backends.lwjgl3.audio.mock.MockAudio
import com.badlogic.gdx.utils.Array
import com.unciv.ui.utils.AudioExceptionHelper

/**
 *  Problem: Not all exceptions playing Music can be caught on the desktop platform using a try-catch around the play method.
 *  Unciv 3.17.13 to 4.0.5 all exacerbated the problem due to using Music from various threads - my current interpretation
 *  is that OpenALMusic isn't thread-safe on play, load, dispose or any other methods touching any AL10.al*Buffers* call.
 *  But even with that fixed, music streams can have codec failures _after_ the first buffer's worth of data, so the problem is only mitigated.
 *
 *  Sooner or later some Exception will be thrown from the code under `Lwjgl3Application.loop` -> `OpenALLwjgl3Audio.update` ->
 *  `OpenALMusic.update` -> `OpenALMusic.fill`, where a Gdx app _cannot_ catch them and has no chance to recover gracefully.
 *
 *  This catches those Exceptions and reports them through a callback mechanism, and also provides a callback from the app loop
 *  that allows MusicController to make its Music calls on a thread guaranteed to be safe for OpenALMusic.
 * #
 *  ### Approach:
 *  - Subclass [OpenALLwjgl3Audio] overriding [update][OpenALLwjgl3Audio.update] with equivalent code catching any Exceptions and Errors
 *  - Replicate original update (a 2-liner) accessing underlying fields through reflection to avoid rewriting the _whole_ thing
 *  - Not super.update so failed music can be immediately stopped, disposed and removed
 *  - Replace the object running inside Gdx - Gdx.app.audio - through reflection
 *  - Replace the object Unciv talks to - overwriting Gdx.audio is surprisingly allowed
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
class HardenGdxAudio : AudioExceptionHelper {

    override fun installHooks(
        updateCallback: (()->Unit)?,
        exceptionHandler: ((Throwable, Music)->Unit)?
    ) {
        // Get already instantiated Audio implementation for cleanup
        // (may be OpenALLwjgl3Audio or MockAudio at this point)
        val badAudio = Gdx.audio

        val noAudio = MockAudio()

        Gdx.audio = noAudio  // It's a miracle this is allowed.
        // If it breaks in a Gdx update, go reflection instead as done below for Gdx.app.audio:

        // Access the reference stored in Gdx.app.audio via reflection
        val appClass = Gdx.app::class.java
        val audioField = appClass.declaredFields.firstOrNull { it.name == "audio" }
        if (audioField != null) {
            audioField.isAccessible = true
            audioField.set(Gdx.app, noAudio)  // kill it for a microsecond safely
        }

        // Clean up allocated resources
        (badAudio as? OpenALLwjgl3Audio)?.dispose()

        // Create replacement
        val newAudio = HardenedGdxAudio(updateCallback, exceptionHandler)

        // Store in Gdx fields used throughout the app (Gdx.app.audio by Gdx internally, Gdx.audio by us)
        audioField?.set(Gdx.app, newAudio)
        Gdx.audio = newAudio
    }

    class HardenedGdxAudio(
        private val updateCallback: (()->Unit)?,
        private val exceptionHandler: ((Throwable, Music)->Unit)?
    ) : OpenALLwjgl3Audio() {
        private val noDevice: Boolean
        private val music: Array<OpenALMusic>

        init {
            val myClass = this::class.java
            val noDeviceField = myClass.superclass.declaredFields.first { it.name == "noDevice" }
            noDeviceField.isAccessible = true
            noDevice = noDeviceField.getBoolean(this)
            val musicField = myClass.superclass.declaredFields.first { it.name == "music" }
            musicField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            music = musicField.get(this) as Array<OpenALMusic>
        }

        // This is just a kotlin translation of the original `update` with added try-catch and cleanup
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

    class AacMusic(audio: OpenALLwjgl3Audio?, file: FileHandle?) : OpenALMusic(audio, file) {
        override fun read(buffer: ByteArray?): Int {
            //...
        }
        override fun reset() {
            //...
        }
    }
    fun registerCodecs(audio: OpenALLwjgl3Audio) {
        audio.registerMusic("m4a", AacMusic::class.java)
    }
*/
