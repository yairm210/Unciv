package com.unciv

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.utils.*
import java.io.PrintWriter
import java.io.StringWriter

/** Screen to crash to when an otherwise unhandled exception or error is thrown. */
class CrashScreen(message: String): BaseScreen() {

    private companion object {
        fun Throwable.stringify(): String {
            val out = StringWriter()
            this.printStackTrace(PrintWriter(out))
            return out.toString()
        }
    }

    constructor(exception: Throwable): this(exception.stringify())

    val text = generateReportHeader() + message

    var copied = false

    fun generateReportHeader(): String {
        return """
            Platform: ${Gdx.app.type}
            Version: ${UncivGame.Current.version}
            Rulesets: ${RulesetCache.keys}
            
            
            """.trimIndent()
    }

    init {
        println(text) // Also print to system terminal.
        stage.addActor(Table().also { layoutTable ->
        layoutTable.width = stage.width
            layoutTable.height = stage.height
            layoutTable.add(
                "An unrecoverable error has occurred in Unciv:".toLabel(fontSize = 24).also {
                    it.wrap = true
                    it.setAlignment(Align.center)
                }
            ).padBottom(15f)
                .width(stage.width)
                .row()
            layoutTable.add(
                AutoScrollPane(Table().also {
                    it.add(Label(text, skin).apply {
                        setFontSize(15)
                    }).pad(10f)
                        .row()
                }).addBorder(4f, Color.DARK_GRAY)
            ).maxWidth(stage.width * 0.7f)
                .maxHeight(stage.height * 0.5f)
                .minHeight(stage.height * 0.2f)
                .row()
            layoutTable.add(
                "{If this keeps happening, you can try disabling mods.}\n{You can also report this on the issue tracker.}".toLabel().also {
                    it.wrap = true
                    it.setAlignment(Align.center)
                }
            ).padTop(15f)
                .width(stage.width)
                .row()
            layoutTable.add(Table().also { table ->
                table.add(
                    "Copy".toButton()
                        .onClick {
                            Gdx.app.clipboard.contents = text
                            copied = true
                            ToastPopup(
                                "Error report copied.",
                                this@CrashScreen
                            )
                        }
                ).pad(10f)
                table.add(
                    "Open Issue Tracker".toButton(icon = "OtherIcons/Link")
                        .onClick {
                            if (copied) {
                                Gdx.net.openURI("https://github.com/yairm210/Unciv/issues")
                            } else {
                                ToastPopup(
                                    "Please copy the error message first.",
                                    this@CrashScreen
                                )
                            }
                        }
                ).pad(10f).also {
                    if (isCrampedPortrait()) {
                        it.row()
                        table.add()
                    }
                }
                table.add(
                    "Close Unciv".toButton()
                        .onClick {
                            Gdx.app.exit()
                        }
                ).pad(10f)
            }).padTop(10f)
        })

    }
}
