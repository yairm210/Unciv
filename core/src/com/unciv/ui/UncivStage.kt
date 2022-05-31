package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import com.unciv.ui.utils.wrapCrashHandling
import com.unciv.ui.utils.wrapCrashHandlingUnit


/** Main stage for the game. Safely brings the game to a [CrashScreen] if any event handlers throw an exception or an error that doesn't get otherwise handled. */
class UncivStage(viewport: Viewport, batch: Batch) : Stage(viewport, batch) {

    /**
     * Enables/disables sending pointer enter/exit events to actors on this stage.
     * Checking for the enter/exit bounds is a relatively expensive operation and may thus be disabled temporarily.
     */
    var performPointerEnterExitEvents: Boolean = false

    override fun draw() =
        { super.draw() }.wrapCrashHandlingUnit()()

    /** libGDX has no built-in way to disable/enable pointer enter/exit events. It is simply being done in [Stage.act]. So to disable this, we have
     * to replicate the [Stage.act] method without the code for pointer enter/exit events. This is of course inherently brittle, but the only way. */
    override fun act() = {
        /** We're replicating [Stage.act], so this value is simply taken from there */
        val delta = Gdx.graphics.deltaTime.coerceAtMost(1 / 30f)

        if (performPointerEnterExitEvents) {
            super.act(delta)
        } else {
            root.act(delta)
        }
    }.wrapCrashHandlingUnit()()

    override fun act(delta: Float) =
        { super.act(delta) }.wrapCrashHandlingUnit()()

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int) =
        { super.touchDown(screenX, screenY, pointer, button) }.wrapCrashHandling()() ?: true

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int) =
        { super.touchDragged(screenX, screenY, pointer) }.wrapCrashHandling()() ?: true

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int) =
        { super.touchUp(screenX, screenY, pointer, button) }.wrapCrashHandling()() ?: true

    override fun mouseMoved(screenX: Int, screenY: Int) =
        { super.mouseMoved(screenX, screenY) }.wrapCrashHandling()() ?: true

    override fun scrolled(amountX: Float, amountY: Float) =
        { super.scrolled(amountX, amountY) }.wrapCrashHandling()() ?: true

    override fun keyDown(keyCode: Int) =
        { super.keyDown(keyCode) }.wrapCrashHandling()() ?: true

    override fun keyUp(keyCode: Int) =
        { super.keyUp(keyCode) }.wrapCrashHandling()() ?: true

    override fun keyTyped(character: Char) =
        { super.keyTyped(character) }.wrapCrashHandling()() ?: true

}

// Example Stack traces from unhandled exceptions after a button click on Desktop and on Android are below.

// Another stack trace from an exception after setting TileInfo.naturalWonder to an invalid value is below that.

// Below that are another two exceptions, from a lambda given to thread{} and another given to Gdx.app.postRunnable{}.

// Stage()'s event handlers seem to be the most universal place to intercept exceptions from events.

// Events and the render loop are the main ways that code gets run with GDX, right? So if we wrap both of those in exception handling, it should hopefully gracefully catch most unhandled exceptions… Threads may be the exception, hence why I put the wrapping as extension functions that can be invoked on the lambdas passed to threads, as in crashHandlingThread and postCrashHandlingRunnable.


// Button click (event):

/*
Exception in thread "main" com.badlogic.gdx.utils.GdxRuntimeException: java.lang.Exception
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.<init>(Lwjgl3Application.java:122)
        at com.unciv.app.desktop.DesktopLauncher.main(DesktopLauncher.kt:61)
Caused by: java.lang.Exception
        at com.unciv.MainMenuScreen$newGameButton$1.invoke(MainMenuScreen.kt:107)
        at com.unciv.MainMenuScreen$newGameButton$1.invoke(MainMenuScreen.kt:106)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClick$1.invoke(ExtensionFunctions.kt:64)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClick$1.invoke(ExtensionFunctions.kt:64)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClickEvent$1.clicked(ExtensionFunctions.kt:57)
        at com.badlogic.gdx.scenes.scene2d.utils.ClickListener.touchUp(ClickListener.java:88)
        at com.badlogic.gdx.scenes.scene2d.InputListener.handle(InputListener.java:71)
        at com.badlogic.gdx.scenes.scene2d.Stage.touchUp(Stage.java:355)
        at com.badlogic.gdx.InputEventQueue.drain(InputEventQueue.java:70)
        at com.badlogic.gdx.backends.lwjgl3.DefaultLwjgl3Input.update(DefaultLwjgl3Input.java:189)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window.update(Lwjgl3Window.java:394)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.loop(Lwjgl3Application.java:143)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.<init>(Lwjgl3Application.java:116)
        ... 1 more

E/AndroidRuntime: FATAL EXCEPTION: GLThread 299
    Process: com.unciv.app, PID: 5910
    java.lang.Exception
        at com.unciv.MainMenuScreen$newGameButton$1.invoke(MainMenuScreen.kt:107)
        at com.unciv.MainMenuScreen$newGameButton$1.invoke(MainMenuScreen.kt:106)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClick$1.invoke(ExtensionFunctions.kt:64)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClick$1.invoke(ExtensionFunctions.kt:64)
        at com.unciv.ui.utils.ExtensionFunctionsKt$onClickEvent$1.clicked(ExtensionFunctions.kt:57)
        at com.badlogic.gdx.scenes.scene2d.utils.ClickListener.touchUp(ClickListener.java:88)
        at com.badlogic.gdx.scenes.scene2d.InputListener.handle(InputListener.java:71)
        at com.badlogic.gdx.scenes.scene2d.Stage.touchUp(Stage.java:355)
        at com.badlogic.gdx.backends.android.DefaultAndroidInput.processEvents(DefaultAndroidInput.java:425)
        at com.badlogic.gdx.backends.android.AndroidGraphics.onDrawFrame(AndroidGraphics.java:469)
        at android.opengl.GLSurfaceView$GLThread.guardedRun(GLSurfaceView.java:1522)
        at android.opengl.GLSurfaceView$GLThread.run(GLSurfaceView.java:1239)
 */

// Invalid Natural Wonder (rendering):

/*
Exception in thread "main" java.lang.NullPointerException
        at com.unciv.logic.map.TileInfo.getNaturalWonder(TileInfo.kt:149)
        at com.unciv.logic.map.TileInfo.getTileStats(TileInfo.kt:255)
        at com.unciv.logic.map.TileInfo.getTileStats(TileInfo.kt:240)
        at com.unciv.ui.worldscreen.bottombar.TileInfoTable.getStatsTable(TileInfoTable.kt:43)
        at com.unciv.ui.worldscreen.bottombar.TileInfoTable.updateTileTable$core(TileInfoTable.kt:25)
        at com.unciv.ui.worldscreen.WorldScreen.update(WorldScreen.kt:383)
        at com.unciv.ui.worldscreen.WorldScreen.render(WorldScreen.kt:828)
        at com.badlogic.gdx.Game.render(Game.java:46)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window.update(Lwjgl3Window.java:403)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.loop(Lwjgl3Application.java:143)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.<init>(Lwjgl3Application.java:116)
        at com.unciv.app.desktop.DesktopLauncher.main(DesktopLauncher.kt:61)
 */

// Thread:

/*
Exception in thread "Thread-5" java.lang.Exception
        at com.unciv.MainMenuScreen$newGameButton$1$1.invoke(MainMenuScreen.kt:107)
        at com.unciv.MainMenuScreen$newGameButton$1$1.invoke(MainMenuScreen.kt:107)
        at kotlin.concurrent.ThreadsKt$thread$thread$1.run(Thread.kt:30)
 */

// Gdx.app.postRunnable:

/*
Exception in thread "main" com.badlogic.gdx.utils.GdxRuntimeException: java.lang.Exception
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.<init>(Lwjgl3Application.java:122)
        at com.unciv.app.desktop.DesktopLauncher.main(DesktopLauncher.kt:61)
Caused by: java.lang.Exception
        at com.unciv.MainMenuScreen$loadGameTable$1.invoke$lambda-0(MainMenuScreen.kt:112)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.loop(Lwjgl3Application.java:159)
        at com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application.<init>(Lwjgl3Application.java:116)
        ... 1 more
 */
