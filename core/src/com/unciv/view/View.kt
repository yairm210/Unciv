package com.unciv.view

import com.unciv.logic.civilization.Civilization

/** A View is the API a player has, to the game; It defines allowed behavior for a player.
 * It allows players to access data and functions they have permission to access.
 * All UI code should use ONLY views, and all "automate as civ" code should do the same, 
 * if we want to retain "legal access to known data" for AI automation (this may be a non-goal. TBD) */
open class View(protected open val viewer: Civilization?, protected val spectatorMode: Boolean = false)
