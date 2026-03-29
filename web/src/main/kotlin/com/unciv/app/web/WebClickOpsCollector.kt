package com.unciv.app.web

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.ServerFeatureSet
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object WebClickOpsCollector {
    private const val hostUserId = "00000000-0000-0000-0000-0000000000a1"
    private const val guestUserId = "00000000-0000-0000-0000-0000000000b2"
    private const val sharedPassword = "webtest-pass"
    private const val publishIntervalMs = 120L
    private val dismissLabels = setOf("close", "back", "ok", "yes", "done", "continue", "confirm", "let's begin!")

    private data class TargetSnapshot(
        val id: String,
        val screen: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val enabled: Boolean,
        val visible: Boolean,
        val text: String,
        val checked: Boolean?,
    )

    private var started = false
    private var settingsApplied = false
    private var lastPublishMs = 0L
    private var lastToast = ""

    fun maybeStart(game: WebGame): Boolean {
        if (started) return true
        if (!WebClickOpsInterop.isEnabled()) return false
        started = true
        applyMultiplayerSettings(game)
        captureFrame(game, force = true)
        return true
    }

    fun captureFrame(game: WebGame, force: Boolean = false) {
        if (!started) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastPublishMs) < publishIntervalMs) return
        lastPublishMs = now
        runCatching {
            applyMultiplayerSettings(game)
            val screen = game.screen
            val screenName = screen?.javaClass?.simpleName ?: "None"
            val stage = (screen as? BaseScreen)?.stage
            val targets = ArrayList<TargetSnapshot>(48)
            if (stage != null) {
                collectNamedTargets(stage.root, stage, screenName, targets)
                collectPopupDismissTarget(stage.root, stage, screenName, targets)
            }
            val stateJson = buildStateJson(game, stage, screenName)
            val targetsJson = buildTargetsJson(screenName, targets)
            WebClickOpsInterop.publishTargets(targetsJson)
            WebClickOpsInterop.publishState(stateJson)
        }.onFailure { throwable ->
            val type = throwable.javaClass.simpleName.ifBlank { "Exception" }
            WebClickOpsInterop.publishError("$type: ${throwable.message ?: "unknown error"}")
        }
    }

    private fun applyMultiplayerSettings(game: UncivGame) {
        if (settingsApplied) return
        val serverUrl = WebClickOpsInterop.getTestMultiplayerServerUrl()?.trim().orEmpty()
        val role = WebClickOpsInterop.getRole()?.trim()?.lowercase().orEmpty()
        if (serverUrl.isEmpty()) {
            settingsApplied = true
            return
        }
        val multiplayer = game.settings.multiplayer
        game.settings.autoAssignCityProduction = true
        game.settings.showTutorials = false
        multiplayer.setServer(serverUrl)
        multiplayer.hideDropboxWarning = true
        game.onlineMultiplayer.multiplayerServer.setFeatureSet(ServerFeatureSet(chatVersion = 1))
        when (role) {
            "host" -> {
                multiplayer.setUserId(hostUserId)
                multiplayer.setCurrentServerPassword(sharedPassword)
            }
            "guest" -> {
                multiplayer.setUserId(guestUserId)
                multiplayer.setCurrentServerPassword(sharedPassword)
            }
        }
        settingsApplied = true
    }

    private fun collectNamedTargets(actor: Actor, stage: Stage, screenName: String, out: MutableList<TargetSnapshot>) {
        val name = actor.name?.trim().orEmpty()
        if (name.isNotEmpty() && name.contains('.')) {
            val target = buildTarget(actor, stage, name, screenName)
            if (target != null) out += target
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                collectNamedTargets(children[index], stage, screenName, out)
            }
        }
    }

    private fun collectPopupDismissTarget(actor: Actor, stage: Stage, screenName: String, out: MutableList<TargetSnapshot>) {
        if (out.any { it.id == "popup.dismiss" }) return
        val popupButtons = mutableListOf<TextButton>()
        collectPopupButtons(actor, popupButtons)
        val dismissButton = popupButtons.firstOrNull {
            it.text?.toString()?.trim().orEmpty().lowercase() in dismissLabels
        } ?: popupButtons.singleOrNull()
        if (dismissButton != null) {
            val target = buildTarget(dismissButton, stage, "popup.dismiss", screenName)
            if (target != null) {
                out += target
                return
            }
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                collectPopupDismissTarget(children[index], stage, screenName, out)
                if (out.any { it.id == "popup.dismiss" }) return
            }
        }
    }

    private fun collectPopupButtons(actor: Actor, out: MutableList<TextButton>) {
        if (actor is TextButton && actor.isVisible && actor.touchable == Touchable.enabled && isInsidePopup(actor)) {
            out += actor
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                collectPopupButtons(children[index], out)
            }
        }
    }

    private fun isInsidePopup(actor: Actor): Boolean {
        var current: Actor? = actor
        while (current != null) {
            if (current is Popup) return true
            current = current.parent
        }
        return false
    }

    private fun buildTarget(actor: Actor, stage: Stage, id: String, screenName: String): TargetSnapshot? {
        val bounds = actorBoundsOnScreen(actor, stage) ?: return null
        val visible = actor.isVisible
        val enabled = isActorEnabled(actor) && isActorHittable(actor, stage)
        return TargetSnapshot(
            id = id,
            screen = screenName,
            x = bounds[0],
            y = bounds[1],
            width = bounds[2],
            height = bounds[3],
            enabled = enabled,
            visible = visible,
            text = actorText(actor),
            checked = (actor as? CheckBox)?.isChecked,
        )
    }

    private fun isActorHittable(actor: Actor, stage: Stage): Boolean {
        val probePoints = listOf(
            Vector2(actor.width / 2f, actor.height / 2f),
            Vector2(min(14f, actor.width / 3f), actor.height / 2f),
            Vector2(max(1f, actor.width - min(14f, actor.width / 3f)), actor.height / 2f),
        ).map { actor.localToStageCoordinates(it) }
        for (probe in probePoints) {
            var hit: Actor? = stage.hit(probe.x, probe.y, true)
            while (hit != null) {
                if (hit === actor) return true
                hit = hit.parent
            }
        }
        return false
    }

    private fun actorBoundsOnScreen(actor: Actor, stage: Stage): IntArray? {
        if (!actor.isVisible) return null
        val p1 = actor.localToStageCoordinates(Vector2(0f, 0f))
        val p2 = actor.localToStageCoordinates(Vector2(actor.width, actor.height))
        val s1 = stage.stageToScreenCoordinates(Vector2(p1.x, p1.y))
        val s2 = stage.stageToScreenCoordinates(Vector2(p2.x, p2.y))
        val x = min(s1.x, s2.x).toInt()
        val y = min(s1.y, s2.y).toInt()
        val width = max(1, abs(s2.x - s1.x).toInt())
        val height = max(1, abs(s2.y - s1.y).toInt())
        return intArrayOf(x, y, width, height)
    }

    private fun isActorEnabled(actor: Actor): Boolean {
        if (!actor.isVisible) return false
        if (actor.touchable != Touchable.enabled) return false
        return when (actor) {
            is Button -> !actor.isDisabled
            is TextField -> !actor.isDisabled
            else -> true
        }
    }

    private fun actorText(actor: Actor): String = when (actor) {
        is IconTextButton -> actor.label.text.toString()
        is TextButton -> actor.text.toString()
        is CheckBox -> actor.text.toString()
        is Label -> actor.text.toString()
        is TextField -> actor.text
        else -> ""
    }

    private fun buildTargetsJson(screenName: String, targets: List<TargetSnapshot>): String {
        val builder = StringBuilder(2048)
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"screen\":\"").append(escapeJson(screenName)).append("\",")
        builder.append("\"targets\":[")
        targets.forEachIndexed { index, target ->
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"id\":\"").append(escapeJson(target.id)).append("\",")
            builder.append("\"screen\":\"").append(escapeJson(target.screen)).append("\",")
            builder.append("\"x\":").append(target.x).append(',')
            builder.append("\"y\":").append(target.y).append(',')
            builder.append("\"width\":").append(target.width).append(',')
            builder.append("\"height\":").append(target.height).append(',')
            builder.append("\"enabled\":").append(target.enabled).append(',')
            builder.append("\"visible\":").append(target.visible).append(',')
            builder.append("\"text\":\"").append(escapeJson(target.text)).append('"')
            if (target.checked != null) {
                builder.append(",\"checked\":").append(target.checked)
            }
            builder.append('}')
        }
        builder.append(']')
        builder.append('}')
        return builder.toString()
    }

    private fun buildStateJson(game: WebGame, stage: Stage?, screenName: String): String {
        val worldScreen = game.screen as? WorldScreen
        val gameInfo = game.gameInfo
        val hasPopup = hasVisiblePopup(stage)
        val toastText = findVisibleToastText(stage?.root)
        if (!toastText.isNullOrBlank()) lastToast = toastText

        val chatMessages = ArrayList<String>(16)
        if (gameInfo != null) {
            val chat = ChatStore.getChatByGameId(gameInfo.gameId)
            chat.forEachMessage { civName, message ->
                chatMessages += "$civName: $message"
            }
        }
        while (chatMessages.size > 20) chatMessages.removeAt(0)

        val builder = StringBuilder(1024)
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"screen\":\"").append(escapeJson(screenName)).append("\",")
        builder.append("\"gameId\":\"").append(escapeJson(gameInfo?.gameId?.toString() ?: "")).append("\",")
        builder.append("\"turn\":").append(gameInfo?.turns ?: -1).append(',')
        builder.append("\"currentPlayer\":\"").append(escapeJson(gameInfo?.currentPlayer ?: "")).append("\",")
        builder.append("\"isPlayersTurn\":").append(worldScreen?.isPlayersTurn == true).append(',')
        builder.append("\"hasPopup\":").append(hasPopup).append(',')
        builder.append("\"lastToast\":\"").append(escapeJson(lastToast)).append("\",")
        builder.append("\"chatMessages\":[")
        chatMessages.forEachIndexed { index, message ->
            if (index > 0) builder.append(',')
            builder.append('"').append(escapeJson(message)).append('"')
        }
        builder.append(']')
        builder.append('}')
        return builder.toString()
    }

    private fun hasVisiblePopup(stage: Stage?): Boolean {
        val root = stage?.root ?: return false
        val children = root.children
        for (index in 0 until children.size) {
            val actor = children[index]
            if (actor is Popup && actor.isVisible) return true
        }
        return false
    }

    private fun findVisibleToastText(actor: Actor?): String? {
        if (actor == null || !actor.isVisible) return null
        if (actor.javaClass.simpleName.contains("ToastPopup")) {
            return firstLabelText(actor)
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                val found = findVisibleToastText(children[index])
                if (!found.isNullOrBlank()) return found
            }
        }
        return null
    }

    private fun firstLabelText(actor: Actor): String? {
        if (actor is Label) return actor.text.toString()
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                val value = firstLabelText(children[index])
                if (!value.isNullOrBlank()) return value
            }
        }
        return null
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
