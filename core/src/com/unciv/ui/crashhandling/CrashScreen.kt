package com.unciv.ui.crashhandling

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.files.UncivFiles
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.components.extensions.addBorder
import com.unciv.ui.components.extensions.setFontSize
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import java.io.PrintWriter
import java.io.StringWriter

/** Screen to crash to when an otherwise unhandled exception or error is thrown. */
//todo We may be in a critical low-memory situation. Using a lot ot String concatenation and trimIndent
//     could make the display fail when a more efficient StringBuilder approach might still succeed.
class CrashScreen(val exception: Throwable) : BaseScreen() {

    private companion object {
        fun Throwable.stringify(): String {
            val out = StringWriter()
            this.printStackTrace(PrintWriter(out))
            return out.toString()
        }
    }

    /** Qualified class name of the game screen that was active at the construction of this instance, or an error note. */
    private val lastScreenType = try {
        UncivGame.Current.screen!!::class.qualifiedName.toString()
    } catch (e: Throwable) {
        "Could not get screen type: $e"
    }

    val text = formatReport(exception.stringify())
    var copied = false
        private set

    /** @return The last active save game serialized as a compressed string if any, or an informational note otherwise. */
    private fun tryGetSaveGame(): String {
        val gameInfo = UncivGame.getGameInfoOrNull() ?: return ""
        return "\n**Save Data:**\n<details><summary>Show Saved Game</summary>\n\n```\n" +
            try {
                UncivFiles.gameInfoToString(gameInfo, forceZip = true)
            } catch (e: Throwable) {
                "No save data: $e" // In theory .toString() could still error here.
            } + "\n```\n</details>\n"
    }

    /** @return Mods from the last active save game if any, or an informational note otherwise. */
    private fun tryGetSaveMods(): String {
        val sb = StringBuilder(160)  // capacity: Just some guess
        val gameInfo = UncivGame.getGameInfoOrNull()
        if (gameInfo != null) {
            sb.append("\n**Save Mods:**\n```\n")
            try { // Also from old CrashController().buildReport(), also could still error at .toString().
                sb.append(gameInfo.gameParameters.getModsAndBaseRuleset().toString())
            } catch (e: Throwable) {
                sb.append("No mod data: $e")
            }
            sb.append("\n```\n")
        }
        val visualMods = UncivGame.Current.settings.visualMods
        if (visualMods.isEmpty())
            return sb.toString()
        sb.append("**Permanent audiovisual Mods**:\n```\n")
        sb.append(visualMods.toString())
        sb.append("\n```\n")
        return sb.toString()
    }


    /**
     * @param message Error text. Probably exception traceback.
     * @return Message with application, platform, and game state metadata.
     * */
    private fun formatReport(message: String): String {
        val indent = " ".repeat(4)
        val baseIndent = indent.repeat(3) // To be even with the template string.
        val subIndent = baseIndent + indent // TO be one level more than the template string.
        /** We only need the indent after any new lines in each substitution itself. So this prepends to all lines, and then removes from the start. */
        fun String.prependIndentToOnlyNewLines(indent: String) = this.prependIndent(indent).removePrefix(indent)
        /// The $lastScreenType substitution is the only one completely under the control of this class— Everything else can, in theory, have new lines in it due to containing strings or custom .toString behaviour with new lines (which… I think Table.toString or something actually does). So normalize indentation for basically everything.
        return """
            **Platform:** ${Gdx.app.type.toString().prependIndentToOnlyNewLines(subIndent)}
            **Version:** ${UncivGame.VERSION.toNiceString().prependIndentToOnlyNewLines(subIndent)}
            **Rulesets:** ${RulesetCache.keys.toString().prependIndentToOnlyNewLines(subIndent)}
            **Last Screen:** `$lastScreenType`

            --------------------------------

            ${Log.getSystemInfo().prependIndentToOnlyNewLines(baseIndent)}

            --------------------------------


            **Message:**
            ```
            ${message.prependIndentToOnlyNewLines(baseIndent)}
            ```
            """.trimIndent() + tryGetSaveMods() + tryGetSaveGame()
    }

    init {
        System.gc() // If we previously died to an OOM error, this decreases the chances that the *crash screen* will OOM as well
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
    private fun makeTitleLabel() =
        "An unrecoverable error has occurred in Unciv:".toLabel(fontSize = Constants.headingFontSize)
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
    private fun makeInstructionLabel() =
        "{If this keeps happening, you can try disabling mods.}\n{You can also report this on the issue tracker.}".toLabel()
            .apply {
                wrap = true
                setAlignment(Align.center)
            }

    /** @return Table that displays decision buttons for the bottom of the screen. */
    private fun makeActionButtonsTable(): Table {
        val copyButton = IconTextButton("Copy", fontSize = Constants.headingFontSize)
            .onClick {
                try {
                    Gdx.app.clipboard.contents = text
                    copied = true
                    ToastPopup(
                        "Error report copied.",
                        this@CrashScreen
                    )
                } catch(ex:Exception) {
                    Log.debug("Could not copy to clipboard", ex)
                    ToastPopup(
                        "Could not copy to clipboard!",
                        this@CrashScreen
                    )
                }
            }
        val reportButton = IconTextButton("Open Issue Tracker", ImageGetter.getImage("OtherIcons/Link"),
            Constants.headingFontSize
        )
            .onClick {
                if (copied) {
                    Gdx.net.openURI("${Constants.uncivRepoURL}issues")
                } else {
                    ToastPopup(
                        "Please copy the error report first.",
                        this@CrashScreen
                    )
                }
            }
        val closeButton = IconTextButton("Close Unciv", fontSize = Constants.headingFontSize)
            .onClick { Gdx.app.exit() }

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
