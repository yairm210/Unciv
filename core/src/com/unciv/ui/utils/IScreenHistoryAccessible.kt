package com.unciv.ui.utils


/**
 * Mutable list for stack of functions to close historically opened screens.
 */
private val screenClosersStack = arrayListOf<() -> Unit>()
// Could also make each screen keep track of its own close action, and thus avoid all issues with desync. That's more like what I'm presently doing with IConsoleScreenAccessible and ConsoleScreen. Actually, I'm no longer sure why I thought a global stack would be a good idea.
// Hm. Can't have var in interface to hold closer function? Doesn't seem use extension property would help either.

/**
 * Mutable set of screens currently in history stack.
 */
private val openScreens = hashSetOf<BaseScreen>()


/**
 * Interface for screens that return to the previous screen by keeping and popping from a global history stack.
 *
 * To use:
 *  Both the original screen to be returned to and the new screen it opens must inherit from this interface.
 *  The original screen must open the new screen using its own openReturnableScreen extension method.
 *  When the new screen is done, it must close itself or be closed using its closeToPreviousScreen extension method.
 *
 * Screens that inherit from this method can still be opened and closed the usual way when it is not necessary to return to the previous screen via the history stack. However, if they are opened with the history stack, then they must be closed with it too.
 */
interface IScreenHistoryAccessible {

    /**
     * Open a new screen that also uses the global screen history stack in order to return to this screen on being closed.
     *
     * The new screen that is opened by this method *must* also inherit from IScreenHistoryAccessible.
     * the new screen *must* call its closeToPreviousScreen method when it closes itself, or else the global history stack will become desynchronized.
     *
     * @param screen New screen to open. Must also be IScreenHistoryAccessible, and must call its closeToPreviousScreen method.
     * @param closeAction Function to call in order to return to the current screen. Default will be a lambda to return to the current screen. May want to pass special value, E.G., { game.setWorldScreen() }, in some cases.
     */
    fun BaseScreen.openReturnableScreen(screen: BaseScreen, closeAction: (() -> Unit)? = null) {
        if (screen is IScreenHistoryAccessible) {
            screenClosersStack.add(closeAction ?: { game.setScreen(this) })
            openScreens.add(screen)
            this.game.setScreen(screen)
            return
        }
        throw IllegalArgumentException("Trying to use screen history stack with ${screen}, which doesn't inherit from IScreenHistoryAccessible.")
    }

    /**
     * Return to the previous screen.
     *
     * This function must not be called unless this instance of the screen was opened using another IScreenHistoryAccessible()'s openReturnableScreenMethod.
     *
     * @throws IllegalStateException if there are no previous screens to return to.
     */
    fun BaseScreen.closeToPreviousScreen() {
        if (!(this in openScreens)) {
            throw IllegalStateException("${this} was not opened using the screen history stack!")
        }
        val gotoprevious = screenClosersStack.removeLast()
        // Since we already check for desync above, removeLast() should never fail.
//        if (gotoprevious == null) {
//            throw IllegalStateException("No previous screen to return to from ${this}!")
//        }
        openScreens.remove(this)
        gotoprevious()
    }
}
