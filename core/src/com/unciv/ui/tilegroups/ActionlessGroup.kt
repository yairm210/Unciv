package com.unciv.ui.tilegroups

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable

/** A lot of the render time was spent on snapshot arrays of the TileGroupMap's groups, in the act() function.
 * These classes are to avoid the overhead of useless act() calls. */

/** A [Group] with [actions] effectively disabled. */
abstract class ActionlessGroupWithHit : Group() {
    override fun act(delta: Float) {}
}

/** A [Group] with [actions] and [hit] effectively disabled. */
abstract class ActionlessGroup : ActionlessGroupWithHit() {
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null
}

/** A [Group] with [actions], [hit] and scaling effectively disabled, pre-sized.
 *  @param groupSize [Sets size][setSize] initially */
open class ActionlessGroupSized(groupSize: Float) : ActionlessGroup() {
    init {
        isTransform = false
        @Suppress("LeakingThis")  // works by setting fields only
        setSize(groupSize, groupSize)
    }
}

/** A with [touchable] and scaling disabled, pre-sized.
 *  @param groupSize [Sets size][setSize] initially */
abstract class GroupSizedTouchableDisabled(groupSize: Float) : Group() {
    init {
        isTransform = false
        touchable = Touchable.disabled
        @Suppress("LeakingThis")  // works by setting fields only
        setSize(groupSize, groupSize)
    }
}
