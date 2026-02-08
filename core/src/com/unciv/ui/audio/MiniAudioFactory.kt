package com.unciv.ui.audio

import com.unciv.logic.files.UncivFiles
import com.unciv.utils.Log
import com.unciv.utils.Tag
import games.rednblack.miniaudio.MADeviceInfo
import games.rednblack.miniaudio.MALogCallback
import games.rednblack.miniaudio.MALogLevel
import games.rednblack.miniaudio.MiniAudio
import games.rednblack.miniaudio.config.MAContextConfiguration
import games.rednblack.miniaudio.config.MAEngineConfiguration
import java.util.EnumSet

/* TODO:
       - Use MASound.fadeIn and fadeOut - do they have an end callback?
       - MA has a separate master volume - no need?
       - Use MASound's own end callback - where is ma_sound_set_end_callback? (https://github.com/rednblackgames/gdx-miniaudio/issues/25)
       - Use 4 MAGroups for Music, Voices, CityAmbient and Sounds - use the group volume control
       - Use MA_SOUND_FLAG_NO_PITCH and MA_SOUND_FLAG_NO_SPATIALIZATION (In MASound.Flags, passed to createSound)
       - Choose good engine frame rate? Perf gains if same as input files and on roid same as device native
       - Use MASoundPool instead of MASound for concurrency (short sounds)? (that's a pool for one single file)
 */

/**
 * Example:

```json
    // As top level field within GameSettings.json:
    "audioConfig": {
        "device": "HDMI",                       // Depends on user's box. Just needs to be part of the actual name. Invalid strings will silently default to the primary device.
        "logLevels": ["ERROR", "INFO"],         // Filters by MALogLevel, or "DEVICES" to log all devices when starting
        "context": {
            "iOSSessionCategory": "PLAYBACK",   // Continue while ringing or locked
            "iOSSessionCategoryOptions": 4,     // ALLOW_BLUETOOTH
            "androidUseAAudio": true            // turn AAudio back on
        },
        "engine": {
            "channels": 2,                      // defaults to native channel count of the device
            "bufferPeriodMillis": 5000,         // default 2000
            "sampleRate": 48000,                // defaults to preferrec sample rate of the device
            "formatType": "S16",                // U8, S16, S24, S32, or F32 (default)
            "lowLatency": false
        }
    }
```
    Other fields are not useful for Unciv use?
 */


/** @see [create] */
object MiniAudioFactory {
    /**
     *  Factory for the single [MiniAudio] instance used in Unciv, called from [UncivGame.create][com.unciv.UncivGame.create]
     *
     *  Allows tailoring the configuration by manually adding a [MiniAudioConfig] as "audioConfig" to the settings json.
     */
    fun create(files: UncivFiles): MiniAudio {
        val config = files.getGeneralSettings().audioConfig ?: MiniAudioConfig()
        config.context.logCallback = UncivMALogCallback(config.logLevels)
        val miniaudio = MiniAudio(config.context, null) // no engine config yet, maybe enumerate devices first
        if (config.device != null) {
            // ma_context_get_devices
            config.engine.playbackId =
                miniaudio.enumerateDevices()
                .firstOrNull { config.device in it.name }
                ?.idAddress ?: -1
        }
        miniaudio.initEngine(config.engine)
        return miniaudio
    }

    class MiniAudioConfig {
        val device: String? = null
        val logLevels = arrayListOf(MALogLevel.ERROR.name, MALogLevel.WARNING.name)
        val context = MAContextConfiguration().apply {
            androidUseAAudio = false
        }
        val engine = MAEngineConfiguration()
    }

    private class UncivMALogCallback(logLevelsAsString: ArrayList<String>) : MALogCallback {
        val logLevels: EnumSet<MALogLevel> =
            logLevelsAsString
            .mapNotNull { name -> MALogLevel.entries.firstOrNull { it.name == name } }
            .toCollection(EnumSet.noneOf(MALogLevel::class.java))
        private val logTag = Tag("GdxMiniAudio")

        private fun MADeviceInfo.MADeviceNativeDataFormat.format() = "$format ${channels}ch ${sampleRate}Hz flags=$flags"
        init {
            if ("DEVICES" in logLevelsAsString) {
                for (device in MiniAudio().enumerateDevices())
                    Log.debug(logTag, "Device = \"%s\" default=%s, capture=%s, formats=%s",
                        device.name, device.isDefault, device.isCapture, device.nativeDataFormats.map { it.format() })
            }
        }

        override fun onLog(level: MALogLevel, message: String) {
            if (level !in logLevels) return
            if (level == MALogLevel.ERROR)
                Log.error(logTag, "%s", message)
            else
                Log.debug(logTag, "[%s] %s", level, message)
        }
    }
}
