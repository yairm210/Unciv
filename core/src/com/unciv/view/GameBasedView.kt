package com.unciv.view

import com.unciv.logic.civilization.Civilization

/** Base class for [View]s that always know their [viewer] civilization. */
open class GameBasedView<T>(wrapped: T, override val viewer: Civilization, spectatorMode: Boolean = false) : View<T>(wrapped, viewer, spectatorMode)
