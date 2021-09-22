package com.unciv.ui.audio

enum class MusicTrackChooserFlags {
    /** Makes prefix parameter a mandatory match */
    PrefixMustMatch,
    /** Makes suffix parameter a mandatory match */
    SuffixMustMatch,
    /** Extends fade duration by factor 5 */
    SlowFade,
    /** Lets music controller shut down after track ends instead of choosing a random next track */
    PlaySingle,
    /** directly choose the 'fallback' file for playback */
    PlayDefaultFile,
}
