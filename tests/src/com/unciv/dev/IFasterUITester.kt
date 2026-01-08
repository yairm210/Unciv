package com.unciv.dev

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 *  To support [FasterUIDevelopment] from your UI element class, implement this interface and list your class in `FasterUIDevelopment.candidates`.
 *  @see FasterUIDevTesters
 */
interface IFasterUITester {
    fun testGetLabel(): String?
    fun testCreateExample(screen: BaseScreen): Actor
    fun testAfterAdd() {}
}
