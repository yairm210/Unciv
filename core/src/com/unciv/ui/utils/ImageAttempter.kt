package com.unciv.ui.utils

/**
 * Metaprogrammy class for short-circuitingly finding the first existing of multiple image options according to [ImageGetter.imageExists].
 *
 * Has a [tryImage] method that can be chain-called with functions which return candidate image paths. The first function to return a valid image path stops subsequently chained calls from having any effect, and its result is saved to be retrieved by the [getPath] and [getImage] methods at the end of the candidate chain. Has a [forceImage] method that can be used in place of [tryImage] to force a candidate image path to be treated as valid.
 *
 * Binds candidate functions to a [scope] instance provided to primary constructor, for syntactic convenience. Bind to [Unit] when not needed.
 *
 * Non-reusable.
 *
 * @property scope Instance to which to bind the candidate-returning functions. For syntactic terseness when making lots of calls to, E.G., [com.unciv.ui.tilegroups.TileSetStrings].
 */
class ImageAttempter<out T: Any>(val scope: T) {
    /** The first valid filename tried if any, or the last filename tried if none have succeeded. */
    var lastTriedFileName: String? = null
        private set
    /** Whether an valid image path has already been tried. Once this is true, no further calls to [tryImage] have any effect. */
    var imageFound = false
        private set

    /**
     * Chainable method that uses [ImageGetter.imageExists] to check whether an image exists. [getPath] and [getImage] will return either the first valid image passed here, or the last invalid image if none were valid. Calls after the first valid one are short-circuited.
     *
     * @see ImageAttempter
     * @see forceImage
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
    /**
     * Chainable method that forces an image candidate chain to be terminated. Passing a function here is exactly like passing a function to [tryImage] and pretending [ImageGetter.imageExists] always returns `true`.
     *
     * @see tryImage
     * @param fileName Function that returns the filename of the image to check. Bound to [scope]. Will not be run if a valid image has already been found. When returning a string, causes the candidate chain to be terminated, all subsequent calls to [tryImage] to be short-circuited, and [getImage] and [getPath] to return the resulting image. May return `null` to skip this candidate entirely.
     * @return This [ImageAttempter], for chaining.
     */
    fun forceImage(fileName: T.() -> String?): ImageAttempter<T> {
        if (!imageFound) {
            val imagePath = scope.run(fileName)
            if (imagePath != null) {
                lastTriedFileName = imagePath
                imageFound = true
            }
        }
        return this
    }
    /** @return The first valid image filename given to [tryImage] if any, or the last tried image filename otherwise. */
    fun getPath() = lastTriedFileName
    /** @return The first valid image filename given to [tryImage] if any, or `null` if no valid image was tried. */
    fun getPathOrNull() = if (imageFound) lastTriedFileName else null
    /** @return The first valid image specified to [tryImage] if any, or the last tried image otherwise. */
    fun getImage() = ImageGetter.getImage(lastTriedFileName)
}
