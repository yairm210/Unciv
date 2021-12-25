package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.utils.*
import java.io.PrintWriter
import java.io.StringWriter

/** Screen to crash to when an otherwise unhandled exception or error is thrown. */
class CrashScreen(val exception: Throwable): BaseScreen() {

    private companion object {
        fun Throwable.stringify(): String {
            val out = StringWriter()
            this.printStackTrace(PrintWriter(out))
            return out.toString()
        }
    }

    val text = generateReportHeader() + exception.stringify()
    var copied = false
        private set

    fun generateReportHeader(): String {
        return """
            Platform: ${Gdx.app.type}
            Version: ${UncivGame.Current.version}
            Rulesets: ${RulesetCache.keys}
            
            
            """.trimIndent()
    }

    init {
        println(text) // Also print to system terminal.
        stage.addActor(makeLayoutTable())
    }

    /** @return A Table containing the layout of the whole screen. */
    private fun makeLayoutTable(): Table {
        val layoutTable = Table().also {
            it.width = stage.width
            it.height = stage.height
        }
        layoutTable.add(makeTitleLabel())
            .padBottom(15f)
            .width(stage.width)
            .row()
        layoutTable.add(makeErrorScroll())
            .maxWidth(stage.width * 0.7f)
            .maxHeight(stage.height * 0.5f)
            .minHeight(stage.height * 0.2f)
            .row()
        layoutTable.add(makeInstructionLabel())
            .padTop(15f)
            .width(stage.width)
            .row()
        layoutTable.add(makeActionButtonsTable())
            .padTop(10f)
        return layoutTable
    }

    /** @return Label for title at top of screen. */
    private fun makeTitleLabel()
        = "An unrecoverable error has occurred in Unciv:".toLabel(fontSize = 24)
        .apply {
            wrap = true
            setAlignment(Align.center)
        }

    /** @return Actor that displays a scrollable view of the error report text. */
    private fun makeErrorScroll(): Actor {
        val errorLabel = Label(text, skin).apply {
            setFontSize(15)
        }
        val errorTable = Table()
        errorTable.add(errorLabel)
            .pad(10f)
        return AutoScrollPane(errorTable)
            .addBorder(4f, Color.DARK_GRAY)
    }

    /** @return Label to give the user more information and context below the error report. */
    private fun makeInstructionLabel()
        = "{If this keeps happening, you can try disabling mods.}\n{You can also report this on the issue tracker.}".toLabel()
        .apply {
            wrap = true
            setAlignment(Align.center)
        }

    /** @return Table that displays decision buttons for the bottom of the screen. */
    private fun makeActionButtonsTable(): Table {
        val copyButton = "Copy".toButton()
            .onClick {
                Gdx.app.clipboard.contents = text
                copied = true
                ToastPopup(
                    "Error report copied.",
                    this@CrashScreen
                )
            }
        val reportButton = "Open Issue Tracker".toButton(icon = "OtherIcons/Link")
            .onClick {
                if (copied) {
                    Gdx.net.openURI("https://github.com/yairm210/Unciv/issues")
                } else {
                    ToastPopup(
                        "Please copy the error report first.",
                        this@CrashScreen
                    )
                }
            }
        val closeButton = "Close Unciv".toButton()
            .onClick {
                throw exception  // throw the original exception to allow crash recording on GP
            }

        val buttonsTable = Table()
        buttonsTable.add(copyButton)
            .pad(10f)
        buttonsTable.add(reportButton)
            .pad(10f)
            .also {
                if (isCrampedPortrait()) {
                    it.row()
                    buttonsTable.add()
                }
            }
        buttonsTable.add(closeButton)
            .pad(10f)

        return buttonsTable
    }
}
