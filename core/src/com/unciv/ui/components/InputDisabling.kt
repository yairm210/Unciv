package com.unciv.ui.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputProcessor
import com.unciv.utils.Concurrency


/** On Android if you don't handle inputs within 500ms you get an Application Not Responding
 * To deal with this there are 2 main methods:
 * 1. Quick and easy: disable other inputs while you handle this one - this does not always avoid all ANRs
 * 2. Hard but correct: 
 *   - Disable inputs, run heavy calculations on another thread (no UI work) - the event "finishes" quickly
 *   - When done, run the update on the GL thread for UI work
 *     - If we don't create a new screen we'll need to reenable inputs
 *     
 *  This frequently takes the form of:
 *  
 *         val inputProcessor = InputDisabling.disableInput() // optional
 *         Concurrency.run {
 *             // state change / calculation
 *             Concurrency.runOnGLThread {
 *                 InputDisabling.setInputProcessor(inputProcessor)  // required if previously disabled
 *                 // update UI
 *             }
 *         }
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
     * If using when not about to replace the screen, use with caution - ensure that input is set afterwards
     */
    fun disableInput(): InputProcessor? {
        val oldInputProcessor = Gdx.input.inputProcessor
        Gdx.input.inputProcessor = null
        return oldInputProcessor
    }
    
    fun setInputProcessor(processor: InputProcessor?) {
        Gdx.input.inputProcessor = processor
    }
}
