package com.unciv.ui.screens.newgamescreen

/**
 * Interface to implement for all screens using [MapOptionsTable] for universal usage
 * @see IPreviousScreen
 */
interface MapOptionsInterface: IPreviousScreen {
    fun isNarrowerThan4to3(): Boolean
    fun lockTables()
    fun unlockTables()
    fun updateTables()
    fun updateRuleset()
    fun tryUpdateRuleset(): Boolean
    fun getColumnWidth(): Float
}
