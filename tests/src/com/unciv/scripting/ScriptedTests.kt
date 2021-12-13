package com.unciv.scripting

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.glutils.HdpiMode
import com.unciv.UncivGame
import com.unciv.UncivGameParameters
import com.unciv.app.desktop.NativeFontDesktop
import com.unciv.testing.GdxTestRunner
import com.unciv.ui.utils.Fonts
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// The scripting API, by definition and by nature, works with the entire application state.
// Most of the risk of instability comes from its dynamism. It's neither particularly difficult nor terribly useful to guarantee and test that any specific set of edge cases will work consistently, but it's much more concerning whether any practical usage patterns involving large amounts of code have broken. With all the non-statically-checked IPC protocols, tokenization logic, and Python/JS operator overloading, hitting as much of the API surface as possible in as realistic a setup as possible is probably the easiest and most useful way to catch breaking changes.

// So... The best way to have useful tests of the scripting API is going to be to launch an entire instance of the application, I think.
// There are/is seemingly (a) Github Action(s) to enable an OpenGL environment through software rendering, so that should hopefully be fine.

@RunWith(GdxTestRunner::class)
class ScriptedTests {

    // @return The ExecResult from running the command with the backendType in a new Unciv application, or null if something went wrong.
    private fun runScript(backendType: ScriptingBackendType, command: String): ExecResult? {
        var execResult: ExecResult? = null
        val uncivGame = UncivGame(UncivGameParameters(
            "ScriptedTests",
            fontImplementation = NativeFontDesktop(Fonts.ORIGINAL_FONT_SIZE.toInt()),
            runScriptAndExit = Triple(
                backendType,
                command,
                { execResult = it }
            )
        ))
        val application = Lwjgl3Application(
            uncivGame,
            Lwjgl3ApplicationConfiguration()
        )
        return execResult
    }

    private fun runScriptedTest(backendType: ScriptingBackendType, command: String) {
        val execResult = runScript(backendType, command)
        Assert.assertFalse(
            execResult?.resultPrint?.prependIndent("\t") ?: "No execResult.",
            execResult?.isException ?: true
        )
    }

    @Test
    fun scriptedPythonTests() {
        runScriptedTest(
            ScriptingBackendType.SystemPython,
            "from unciv_scripting_examples.Tests import *; TestRunner.run_tests(debugprint=False)"
        )
    }

    @Test
    fun scriptedReflectiveTests() {
        runScriptedTest(
            ScriptingBackendType.Reflective,
            "runtests"
        )
    }
}
