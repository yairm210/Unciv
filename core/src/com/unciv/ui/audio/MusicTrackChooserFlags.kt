package com.unciv.ui.audio

import java.util.EnumSet

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
    ;

    companion object {
        // EnumSet factories
        val default: EnumSet<MusicTrackChooserFlags> = EnumSet.of(SuffixMustMatch)
        /** EnumSet.of([PlayDefaultFile], [PlaySingle]) */
        val setPlayDefault: EnumSet<MusicTrackChooserFlags> = EnumSet.of(PlayDefaultFile, PlaySingle)
        /** EnumSet.of([PrefixMustMatch], [PlaySingle]) */
        val setSelectNation: EnumSet<MusicTrackChooserFlags> = EnumSet.of(PrefixMustMatch)
        /** EnumSet.of([PrefixMustMatch], [SuffixMustMatch]) */
        val setSpecific: EnumSet<MusicTrackChooserFlags> = EnumSet.of(PrefixMustMatch, SuffixMustMatch)
        /** EnumSet.of([PrefixMustMatch], [SlowFade]) */
        val setNextTurn: EnumSet<MusicTrackChooserFlags> = EnumSet.of(PrefixMustMatch, SlowFade)
        /** EnumSet.noneOf() */
        val none: EnumSet<MusicTrackChooserFlags> = EnumSet.noneOf(MusicTrackChooserFlags::class.java)
    }
}
