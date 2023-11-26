package com.unciv.ui.images

import com.unciv.logic.civilization.Civilization
import com.unciv.ui.components.tilegroups.TileSetStrings

@Suppress("MemberVisibilityCanBePrivate") // no problem for clients to see scope, imageFound, unused/internally used API

/**
 * Metaprogrammy class for short-circuitingly finding the first existing of multiple image options according to [ImageGetter.imageExists].
 *
 * Has a [tryImage] method that can be chain-called with functions which return candidate image paths.
 * The first function to return a valid image path stops subsequently chained calls from having any effect,
 * and its result is saved to be retrieved by the [getPath] and [getImage] methods at the end of the candidate chain.
 *
 * (So it is similar to Sequence in that intermediate "transforms" are only evaluated when necessary,
 * but it is also different as resolution happens early, while chaining, not triggered by a terminating transform.)
 *
 * Binds candidate functions to a [scope] instance of type [T] provided to primary constructor, for syntactic convenience. Bind to [Unit] when not needed.
 *
 * Non-reusable.
 *
 * @param T type of [scope]
 * @property scope Instance to which to bind the candidate-returning functions. For syntactic terseness when making lots of calls to, e.g., [TileSetStrings][com.unciv.ui.components.tilegroups.TileSetStrings].
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
     * @return Chainable `this` [ImageAttempter] extended by one check for [fileName]
     */
    fun tryImage(fileName: T.() -> String?): ImageAttempter<T> {
        if (!imageFound) {
            val imagePath = fileName.invoke(scope)
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
     * @return Chainable `this` [ImageAttempter] extended by zero or more checks for [fileNames]
     */
    fun tryImages(fileNames: Sequence<T.() -> String?>): ImageAttempter<T> {
        for (fileName in fileNames) {
            tryImage(fileName)
        } // *Could* skip calls/break loop if already imageFound. But that means needing an internal guarantee/spec of tryImage being same as no-op when imageFound.
        return this
    }

    /** Try to load era-specific image variants
     *
     *  Tries eras from the civ's current one down to the first era defined, by json order of eras.
     *  Result looks like "Plains-Rome-Ancient era": [style] goes before era if supplied.
     *
     * @param civInfo the civ who owns the tile or unit, used for getEraNumber and ruleset (but not for nation.getStyleOrCivName)
     * @param locationToCheck the beginning of the filename to check
     * @param style an optional string to load a civ- or style-specific sprite
     * @return Chainable `this` [ImageAttempter] extended by one or more checks for era-specific images
     * */
     fun tryEraImage(civInfo: Civilization, locationToCheck: String, style: String?, tileSetStrings: TileSetStrings): ImageAttempter<T> {
        return tryImages(
            (civInfo.getEraNumber() downTo 0).asSequence().map {
                {
                    val era = civInfo.gameInfo.ruleset.eras.keys.elementAt(it)
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
