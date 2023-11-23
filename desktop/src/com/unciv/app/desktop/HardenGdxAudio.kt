package com.unciv.app.desktop

import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALMusic
import com.badlogic.gdx.utils.Array

/**
 *  Problem: Not all exceptions playing Music can be caught on the desktop platform using a try-catch around the play method.
 *  Unciv 3.17.13 to 4.0.5 all exacerbated the problem due to using Music from various threads - and Gdx documents it isn't threadsafe.
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

    fun installHooks(
        updateCallback: (()->Unit)?,
        exceptionHandler: ((Throwable, Music)->Unit)?
    ) {
        this.updateCallback = updateCallback
        this.exceptionHandler = exceptionHandler
    }

    override fun createAudio(config: Lwjgl3ApplicationConfiguration?) =
        HardenedGdxAudio()

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
