package com.unciv.ui.components

import com.badlogic.gdx.Gdx


/** On Android if you don't handle inputs within 500ms you get an Application Not Responding
 * To deal with this you can either run heavy calculations on another thread
 * This is not always feasible, and definitely the easiest way is to just disable inputs until you're done and then reenable.
 * 
 * */
object InputDisabling {
    /**
     * This is NOT what you want if you're creating a new screen, since this will make you set the input processing back 
     *    to the previous state, but when creating a new screen we set it to the new screen.
     */
    fun <T> withInputDisabled(block: () -> T): T {
        val inputProcessor = Gdx.input.inputProcessor
        Gdx.input.inputProcessor = null
        val result = block()
        Gdx.input.inputProcessor = inputProcessor
        return result
    }

    /** 
     * Avoids ANRs when we're about to replace the screen
     * If using when not about to replace the screem, use with caution - ensure that input is set afterwards
     */
    fun disableInput(){
        Gdx.input.inputProcessor = null
    }
}
