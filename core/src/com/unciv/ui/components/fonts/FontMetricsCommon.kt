package com.unciv.ui.components.fonts

/** Implementations of FontImplementation will use different FontMetrics - AWT or Android.Paint,
 *  both have a class of that name, no other common point: thus we create an abstraction.
 *
 *  This is used by [Fonts.getPixmapFromActor] for vertical positioning.
 */
class FontMetricsCommon(
    /** (positive) distance from the baseline up to the recommended top of normal text */
    val ascent: Float,
    /** (positive) distance from the baseline down to the recommended bottom of normal text */
    val descent: Float,
    /** (positive) maximum distance from top to bottom of any text,
     *  including potentially empty space above ascent or below descent */
    val height: Float,
    /** (positive) distance from the bounding box top (as defined by [height])
     *  to the highest possible top of any text */

    // Note: This is NOT what typographical leading actually is, but redefined as extra empty space
    // on top, to make it easier to sync desktop and android. AWT has some leading but no measures
    // outside ascent+descent+leading, while Android has its leading always 0 but typically top
    // above ascent and bottom below descent.
    // I chose to map AWT's spacing to the top as I found the calculations easier to visualize.
    /** Space from the bounding box top to the top of the ascenders - includes line spacing and
     *  room for unusually high ascenders, as [ascent] is only a recommendation. */
    val leading: Float
)
