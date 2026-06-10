package com.unciv.dev

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 *  To support [FasterUIDevelopment] from your UI element class, implement this interface and list your class in `FasterUIDevelopment.candidates`.
 *  @see FasterUIDevTesters
 */
interface IFasterUITester {
    /** [FasterUIDevelopment] will offer your tester with this label. [FasterUIDevTesters] defaults it to the enum name.  */
    fun testGetLabel(): String?

    /** Create your testing container and content. Will be added to the stage centered, and if it does not set its own size, sized to half the stage per direction. */
    fun testCreateExample(screen: BaseScreen): Actor

    /** Optional code to execute after your testing container is on the stage */
    fun testAfterAdd() {}
}
