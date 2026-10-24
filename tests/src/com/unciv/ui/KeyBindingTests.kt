package com.unciv.ui

import com.unciv.testing.BaseTestRunner
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.screens.worldscreen.unit.actions.UiUnitActionType
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BaseTestRunner::class)
class KeyBindingTests {
    @Test
    fun allUnitactionBindingsMatch() {
        val actionBindings = UiUnitActionType.entries.map { it.binding }.filterNot { it == KeyboardBinding.None }.toSet()
        val bindingsForActions = KeyboardBinding.entries.filter { it.category == KeyboardBinding.Category.UnitActions }.toSet()
        val actionBindingsOutOfRange = actionBindings - bindingsForActions
        val rangeBindingsWithoutAction = bindingsForActions - actionBindings
        Assert.assertEquals("There are UiUnitActionType entries with a KeyboardBinding not in the UnitActions category:", emptySet<KeyboardBinding>(), actionBindingsOutOfRange)
        Assert.assertEquals("There are KeyboardBinding entries in the UnitActions category without a matching UiUnitActionType:", emptySet<KeyboardBinding>(), rangeBindingsWithoutAction)
    }
}
