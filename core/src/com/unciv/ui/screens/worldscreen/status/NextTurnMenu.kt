package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.popups.ContextMenus
import com.unciv.ui.screens.worldscreen.WorldScreen

object NextTurnMenuDescriptor : ContextMenus.IDescriptor<NextTurnMenuDescriptor.NextTurnMenu, NextTurnMenuDescriptor.Context> {
    class Context(val worldScreen: WorldScreen) : ContextMenus.IContext

    override fun isAvailable(context: Context): Boolean = !context.worldScreen.nextTurnButton.isNextTurnAction()

    override fun createMenu(context: Context) = NextTurnMenu(context)

    class NextTurnMenu(
        context: Context,
        private val worldScreen: WorldScreen = context.worldScreen
    ) : ContextMenus.Menu(worldScreen.stage, worldScreen.nextTurnButton) {
        init {
            // We need to activate the end turn button again after the menu closes
            afterCloseCallback = { worldScreen.shouldUpdate = true }
        }

        override fun createContentTable(): Table {
            val table = super.createContentTable()!!
            table.add(getButton("Next Turn", KeyboardBinding.NextTurnMenuNextTurn) {
                worldScreen.nextTurn()
            }).row()
            if (NextTurnAction.MoveAutomatedUnits.isChoice(worldScreen))
                table.add(getButton("Move automated units", KeyboardBinding.NextTurnMenuMoveAutomatedUnits) {
                    NextTurnAction.MoveAutomatedUnits.action(worldScreen)
                }).row()
            return table
        }
    }
}
