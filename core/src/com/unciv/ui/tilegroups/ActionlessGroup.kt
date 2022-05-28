package com.unciv.ui.tilegroups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group

/** A lot of the render time was spent on snapshot arrays of the TileGroupMap's groups, in the act() function.
 * These classes are to avoid the overhead of useless act() calls. */

/** A [Group] with [actions] effectively disabled. */
abstract class ActionlessGroupWithHit : Group() {
    override fun act(delta: Float) {}
}

/** A [Group] with [actions] and [hit] effectively disabled. */
open class ActionlessGroup() : ActionlessGroupWithHit() {
    /** A [Group] with [actions], [hit] and scaling effectively disabled, pre-sized.
     *  @param groupSize [Sets size][setSize] initially */
    constructor(groupSize: Float) : this() {
        isTransform = false
        @Suppress("LeakingThis")  // works by setting fields only
        setSize(groupSize, groupSize)
    }
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
}
