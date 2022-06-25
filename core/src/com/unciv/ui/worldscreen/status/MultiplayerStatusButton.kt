package com.unciv.ui.worldscreen.status

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.HasMultiplayerGameName
import com.unciv.logic.multiplayer.MultiplayerGameNameChanged
import com.unciv.logic.multiplayer.MultiplayerGameUpdateEnded
import com.unciv.logic.multiplayer.MultiplayerGameUpdateStarted
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.OnlineMultiplayerGame
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setSize
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

class MultiplayerStatusButton(
    screen: BaseScreen,
    curGame: OnlineMultiplayerGame?
) : Button(BaseScreen.skin), Disposable {
    private var curGameName = curGame?.name
    private val multiplayerImage = createMultiplayerImage()
    private val loadingImage = createLoadingImage()
    private val turnIndicator = TurnIndicator()
    private val turnIndicatorCell: Cell<Actor>
    private val gameNamesWithCurrentTurn = getInitialGamesWithCurrentTurn()
    private var loadingStarted: Instant? = null

    private val events = EventBus.EventReceiver()
    private var loadStopJob: Job? = null

    init {
        turnIndicatorCell = add().padTop(10f).padBottom(10f)
        add(Stack(multiplayerImage, loadingImage)).pad(5f)

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
        loadingStarted = Instant.now()

        if (UncivGame.Current.settings.continuousRendering) {
            loadingImage.clearActions()
            loadingImage.addAction(Actions.repeat(RepeatAction.FOREVER,Actions.rotateBy(-90f, 1f)))
        }

        loadingImage.isVisible = true

        multiplayerImage.color.a = 0.4f
    }

    private fun stopLoading() {
        val loadingTime = Duration.between(loadingStarted ?: Instant.now(), Instant.now())
        val waitFor = if (loadingTime.toMillis() < 500) {
            // Some servers might reply almost instantly. That's nice and all, but the user will just see a blinking icon in that case
            // and won't be able to make out what it was. So we just show the loading indicator a little longer even though it's already done.
            Duration.ofMillis(500 - loadingTime.toMillis())
        } else {
            Duration.ZERO
        }
        loadStopJob = Concurrency.run("Hide loading indicator") {
            delay(waitFor.toMillis())
            launchOnGLThread {
                loadingImage.clearActions()
                loadingImage.isVisible = false
                multiplayerImage.color.a = 1f
            }
        }
    }

    private fun getInitialGamesWithCurrentTurn(): MutableSet<String> {
        return findGamesToBeNotifiedAbout(UncivGame.Current.onlineMultiplayer.games)
    }

    /** @return set of gameIds */
    private fun findGamesToBeNotifiedAbout(games: Iterable<OnlineMultiplayerGame>): MutableSet<String> {
        return games
            .filter { it.name != curGameName }
            .filter { it.preview?.isUsersTurn() == true }
            .map { it.name }
            .toMutableSet()
    }


    private fun createMultiplayerImage(): Image {
        val img = ImageGetter.getImage("OtherIcons/Multiplayer")
        img.setSize(40f)
        return img
    }

    private fun createLoadingImage(): Image {
        val img = ImageGetter.getImage("OtherIcons/Loading")
        img.setSize(40f)
        img.isVisible = false
        img.setOrigin(Align.center)
        return img
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
        loadStopJob?.cancel()
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
            gameAmount.setText(gamesWithUpdates)
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
