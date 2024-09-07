package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.HasMultiplayerGameName
import com.unciv.logic.multiplayer.MultiplayerGameNameChanged
import com.unciv.logic.multiplayer.MultiplayerGameUpdateEnded
import com.unciv.logic.multiplayer.MultiplayerGameUpdateStarted
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.MultiplayerGame
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.LoadingImage
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

class MultiplayerStatusButton(
    screen: BaseScreen,
    curGame: MultiplayerGame?
) : Button(BaseScreen.skin), Disposable {
    private var curGameName = curGame?.name
    private val loadingImage = LoadingImage(style = LoadingImage.Style(
        idleImageName = "OtherIcons/Multiplayer",
        idleIconColor = Color.WHITE,
        minShowTime = 500
    ))
    private val turnIndicator = TurnIndicator()
    private val turnIndicatorCell: Cell<Actor>
    private val gameNamesWithCurrentTurn = getInitialGamesWithCurrentTurn()

    private val events = EventBus.EventReceiver()

    init {
        turnIndicatorCell = add().padTop(10f).padBottom(10f)
        add(loadingImage).pad(5f)

        updateTurnIndicator(flash = false) // no flash since this is just the initial construction
        events.receive(MultiplayerGameUpdated::class) {
            val shouldUpdate = if (it.preview.isUsersTurn()) {
                gameNamesWithCurrentTurn.add(it.name)
            } else {
                gameNamesWithCurrentTurn.remove(it.name)
            }
            if (shouldUpdate) Concurrency.runOnGLThread {
                updateTurnIndicator()
            }
        }

        val curGameFilter: (HasMultiplayerGameName) -> Boolean = { it.name == curGameName }

        events.receive(MultiplayerGameNameChanged::class, curGameFilter) {
            curGameName = it.newName
        }

        events.receive(MultiplayerGameUpdateStarted::class, curGameFilter) { startLoading() }
        events.receive(MultiplayerGameUpdateEnded::class, curGameFilter) { stopLoading() }

        onClick {
            MultiplayerStatusPopup(screen).open()
        }
    }

    private fun startLoading() {
        loadingImage.show()
    }

    private fun stopLoading() {
        loadingImage.hide()
    }

    private fun getInitialGamesWithCurrentTurn(): MutableSet<String> {
        return findGamesToBeNotifiedAbout(UncivGame.Current.onlineMultiplayer.games)
    }

    /** @return set of gameIds */
    private fun findGamesToBeNotifiedAbout(games: Iterable<MultiplayerGame>): MutableSet<String> {
        return games
            .filter { it.name != curGameName }
            .filter { it.preview?.isUsersTurn() == true }
            .map { it.name }
            .toMutableSet()
    }

    private fun updateTurnIndicator(flash: Boolean = true) {
        if (gameNamesWithCurrentTurn.size == 0) {
            turnIndicatorCell.clearActor()
        } else {
            turnIndicatorCell.setActor(turnIndicator)
            turnIndicator.update(gameNamesWithCurrentTurn.size)
        }

        // flash so the user sees an better update
        if (flash) {
            turnIndicator.flash()
        }
    }

    override fun dispose() {
        events.stopReceiving()
        turnIndicator.dispose()
        loadingImage.dispose()
    }
}

private class TurnIndicator : HorizontalGroup(), Disposable {
    val gameAmount = Label("2", BaseScreen.skin)
    val image: Image
    private var job: Job? = null
    init {
        image = ImageGetter.getImage("OtherIcons/ExclamationMark")
        image.setSize(30f)
        addActor(image)
    }

    fun update(gamesWithUpdates: Int) {
        if (gamesWithUpdates < 2) {
            gameAmount.remove()
        } else {
            gameAmount.setText(gamesWithUpdates.tr())
            addActorAt(0, gameAmount)
        }
    }

    fun flash() {
        // using a gdx Action would be nicer, but we don't necessarily have continuousRendering on and we still want to flash
        flash(6, Color.WHITE, Color.ORANGE)
    }
    private fun flash(alternations: Int, curColor: Color, nextColor: Color) {
        if (alternations == 0) return
        gameAmount.color = nextColor
        image.color = nextColor
        job = Concurrency.run("StatusButton color flash") {
            delay(500)
            launchOnGLThread {
                flash(alternations - 1, nextColor, curColor)
            }
        }
    }

    override fun dispose() {
        job?.cancel()
    }
}
