package com.unciv.ui.images

import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.tilegroups.TileSetStrings

/**
 * Metaprogrammy class for short-circuitingly finding the first existing of multiple image options according to [ImageGetter.imageExists].
 *
 * Has a [tryImage] method that can be chain-called with functions which return candidate image paths. The first function to return a valid image path stops subsequently chained calls from having any effect, and its result is saved to be retrieved by the [getPath] and [getImage] methods at the end of the candidate chain.
 *
 * Binds candidate functions to a [scope] instance provided to primary constructor, for syntactic convenience. Bind to [Unit] when not needed.
 *
 * Non-reusable.
 *
 * @property scope Instance to which to bind the candidate-returning functions. For syntactic terseness when making lots of calls to, E.G., [com.unciv.ui.tilegroups.TileSetStrings].
 */
class ImageAttempter<out T: Any>(val scope: T) {
    /** The first valid filename tried if any, or the last filename tried if none have succeeded. */
    private var lastTriedFileName: String? = null
    /** Whether an valid image path has already been tried. Once this is true, no further calls to [tryImage] have any effect. */
    var imageFound = false
        private set

    /**
     * Chainable method that uses [ImageGetter.imageExists] to check whether an image exists. [getPath] and [getImage] will return either the first valid image passed here, or the last invalid image if none were valid. Calls after the first valid one are short-circuited.
     *
     * @see ImageAttempter
     * @param fileName Function that returns the filename of the image to check. Bound to [scope]. Will not be run if a valid image has already been found. May return `null` to skip this candidate entirely.
     * @return This [ImageAttempter], for chaining.
     */
    fun tryImage(fileName: T.() -> String?): ImageAttempter<T> {
        if (!imageFound) {
            val imagePath = scope.run(fileName)
            lastTriedFileName = imagePath ?: lastTriedFileName
            if (imagePath != null && ImageGetter.imageExists(imagePath))
                imageFound = true
        }
        return this
    }
    /**
     * Chainable method that makes multiple invocations to [tryImage].
     *
     * @see tryImage
     * @param fileNames Any number of image candidate returning functions to pass to [tryImage].
     * @return This [ImageAttempter], for chaining.
     */
    fun tryImages(fileNames: Sequence<T.() -> String?>): ImageAttempter<T> {
        for (fileName in fileNames) {
            tryImage(fileName)
        } // *Could* skip calls/break loop if already imageFound. But that means needing an internal guarantee/spec of tryImage being same as no-op when imageFound.
        return this
    }

    /** Try to load era-specific image variants
     * [civInfo]: the civ who owns the tile or unit
     * [locationToCheck]: the beginning of the filename to check
     * [style]: an optional string to load a civ- or style-specific sprite
     * */
     fun tryEraImage(civInfo: CivilizationInfo, locationToCheck: String, style: String?, tileSetStrings:TileSetStrings): ImageAttempter<T> {
        return tryImages(
            (civInfo.getEraNumber() downTo 0).asSequence().map {
                {
                    val era = civInfo.gameInfo.ruleSet.eras.keys.elementAt(it)
                    if (style != null)
                        tileSetStrings.getString(locationToCheck, tileSetStrings.tag, style, tileSetStrings.tag, era)
                    else
                        tileSetStrings.getString(locationToCheck, tileSetStrings.tag, era)
                }
            }
        )
    }

    /** @return The first valid image filename given to [tryImage] if any, or the last tried image filename otherwise. */
    fun getPath() = lastTriedFileName
    /** @return The first valid image filename given to [tryImage] if any, or `null` if no valid image was tried. */
    fun getPathOrNull() = if (imageFound) lastTriedFileName else null
    /** @return The first valid image specified to [tryImage] if any, or the last tried image otherwise. */
    fun getImage() = ImageGetter.getImage(lastTriedFileName)
}
