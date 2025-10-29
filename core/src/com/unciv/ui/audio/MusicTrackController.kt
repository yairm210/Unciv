package com.unciv.ui.audio

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import com.unciv.utils.Log
import com.unciv.utils.debug
import games.rednblack.miniaudio.MASound
import games.rednblack.miniaudio.MiniAudio

/** Wraps one Gdx Music instance and manages loading, playback, fading and cleanup */
internal class MusicTrackController(
    private val miniAudio: MiniAudio,
    private var volume: Float,
    initialFadeVolume: Float = 1f
) : Disposable {

    /** Internal state of this Music track */
    enum class State(val canPlay: Boolean) {
        None(false),
        Loading(false),
        Idle(true),
        FadeIn(true),
        Playing(true),
        FadeOut(true),
        Error(false)
    }

    var state = State.None
        private set
    var music: MASound? = null
        private set
    private var fadeStep = MusicController.defaultFadingStep
    private var fadeVolume: Float = initialFadeVolume

    //region Functions for MusicController

    /** Clean up and dispose resources */
    override fun dispose() {
        state = State.None
        if (music == null) return
        music!!.dispose()
        music = null
    }

    /** Loads [file] into this controller's [music] and optionally calls [onSuccess] when done.
     *  Failures are silently logged to console, and [onError] is called.
     *  Callbacks run on the background thread.
     *  @throws IllegalStateException if called in the wrong state (fresh or cleared instance only)
     */
    fun load(
        file: FileHandle,
        onError: ((MusicTrackController)->Unit)? = null,
        onSuccess: ((MusicTrackController)->Unit)? = null
    ) {
        check(state == State.None && music == null) {
            "MusicTrackController.load should only be called once"
        }

        state = State.Loading
        try {
            music = miniAudio.createSound(file.path(), MASound.Flags.MA_SOUND_FLAG_STREAM, null)
            if (state != State.Loading) {  // in case dispose was called in the meantime
                dispose()
            } else {
                state = State.Idle
                debug("Music loaded %s", file)
                onSuccess?.invoke(this)
            }
        } catch (ex: Exception) {
            audioExceptionHandler(ex)
            state = State.Error
            onError?.invoke(this)
        }
    }

    /** Called by the [MusicController] in its timer "tick" event handler, implements fading */
    fun timerTick(): State {
        if (state == State.FadeIn) fadeInStep()
        if (state == State.FadeOut) fadeOutStep()
        return state
    }

    /** Starts fadeIn or fadeOut.
     *
     *  Note this does _not_ set the current fade "percentage" to allow smoothly
     *  changing direction mid-fade
     *  @param step Overrides current fade step only if >0
     */
    fun startFade(fade: State, step: Float = 0f) {
        if (!state.canPlay) return
        if (step > 0f) fadeStep = step
        state = fade
    }

    /** Graceful shutdown tick event - fade out then report Idle
     *  @return `true` shutdown can proceed, `false` still fading out
     */
    fun shutdownTick(): Boolean {
        if (!state.canPlay) state = State.Idle
        if (state == State.Idle) return true
        if (state != State.FadeOut) {
            state = State.FadeOut
            fadeStep = MusicController.defaultFadingStep
        }
        return timerTick() == State.Idle
    }

    /** @return [MASound.isPlaying] (miniAudio music stream is playing)
     *      unless [state] says it won't make sense */
    fun isPlaying() = state.canPlay && music?.isPlaying == true

    /** Calls play() on the wrapped Gdx Music, catching exceptions to console.
     *  @return success
     *  @throws IllegalStateException if called on uninitialized instance
     */
    fun play(): Boolean {
        if (!state.canPlay || music == null) {
            dispose() // reset to correct state
            return false
        }

        // Unexplained observed exception: Gdx.Music.play fails with
        // "Unable to allocate audio buffers. AL Error: 40964" (AL_INVALID_OPERATION)
        // Approach: This track dies, parent controller will enter state Silence thus
        // retry after a while.
        if (tryPlay(music!!)) return true
        state = State.Error
        return false
    }

    /** Adjust master volume without affecting a fade-in/out */
    fun setVolume(newVolume: Float) {
        volume = newVolume
        music?.setVolume(volume * fadeVolume)
    }

    //endregion
    //region Helpers

    private fun fadeInStep() {
        // fade-in: linearly ramp fadeVolume to 1.0, then continue playing
        fadeVolume += fadeStep
        if (fadeVolume < 1f  && music != null && music!!.isPlaying) {
            music!!.setVolume(volume * fadeVolume)
            return
        }
        music!!.setVolume(volume)
        fadeVolume = 1f
        state = State.Playing
    }
    private fun fadeOutStep() {
        // fade-out: linearly ramp fadeVolume to 0.0, then act according to Status
        //   (Playing->Silence/Pause/Shutdown)
        // This needs to guard against the music backend breaking mid-fade away during game shutdown
        fadeVolume -= fadeStep
        try {
            if (fadeVolume >= 0.001f && music != null && music!!.isPlaying) {
                music!!.setVolume(volume * fadeVolume)
                return
            }
            fadeVolume = 0f
            music!!.setVolume(0f)
            music!!.pause()
        } catch (_: Throwable) {}
        state = State.Idle
    }

    private fun tryPlay(music: MASound): Boolean {
        return try {
            music.setVolume(volume * fadeVolume)
            // for fade-over this could be called by the end of the previous track:
            if (!music.isPlaying) music.play()
            true
        } catch (ex: Throwable) {
            audioExceptionHandler(ex)
            false
        }
    }

    private fun audioExceptionHandler(ex: Throwable) {
        dispose()
        Log.error("Error playing music", ex)
    }

    //endregion
}
