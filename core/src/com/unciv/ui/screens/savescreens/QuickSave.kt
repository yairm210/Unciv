package com.unciv.ui.screens.savescreens

import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.UncivShowableException
import com.unciv.ui.popups.LoadingPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread


//todo reduce code duplication

object QuickSave {
    fun save(gameInfo: GameInfo, screen: WorldScreen) {
        // See #10353 - we don't support locally saving an online multiplayer game
        if (gameInfo.gameParameters.isOnlineMultiplayer) return

        val files = UncivGame.Current.files
        val toast = ToastPopup("Quicksaving...", screen)
        Concurrency.runOnNonDaemonThreadPool("QuickSaveGame") {
            files.saveGame(gameInfo, "QuickSave") {
                launchOnGLThread {
                    toast.close()
                    if (it != null)
                        ToastPopup("Could not save game!", screen)
                    else
                        ToastPopup("Quicksave successful.", screen)
                }
            }
        }
    }

    fun load(screen: WorldScreen) {
        screen.autoPlay.stopAutoPlay()
        val files = UncivGame.Current.files
        val toast = ToastPopup("Quickloading...", screen)
        Concurrency.run("QuickLoadGame") {
            try {
                val loadedGame = files.loadGameByName("QuickSave")
                launchOnGLThread {
                    toast.close()
                    UncivGame.Current.loadGame(loadedGame)
                    ToastPopup("Quickload successful.", screen)
                }
            } catch (ex: Exception) {
                Log.error("Exception while quickloading", ex)
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                launchOnGLThread {
                    toast.close()
                    ToastPopup(message, screen)
                }
            }
        }
    }

    fun autoLoadGame(screen: MainMenuScreen) {
        val loadingPopup = LoadingPopup(screen)
        Concurrency.run("autoLoadGame") {
            // Load game from file to class on separate thread to avoid ANR...
            fun outOfMemory() {
                launchOnGLThread {
                    loadingPopup.close()
                    ToastPopup("Not enough memory on phone to load game!", screen)
                }
            }

            val savedGame: GameInfo
            try {
                savedGame = screen.game.files.autosaves.loadLatestAutosave()
            } catch (_: OutOfMemoryError) {
                outOfMemory()
                return@run
            } catch (ex: Exception) {
                Log.error("Could not autoload game", ex)
                launchOnGLThread {
                    loadingPopup.close()
                    val (message) = LoadGameScreen.getLoadExceptionMessage(
                        ex,
                        "Cannot resume game!"
                    )
                    ToastPopup(message, screen)
                }
                return@run
            }

            if (savedGame.gameParameters.isOnlineMultiplayer) {
                try {
                    screen.game.onlineMultiplayer.downloadGame(savedGame)
                } catch (_: OutOfMemoryError) {
                    outOfMemory()
                } catch (notAPlayer: UncivShowableException) {
                    val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                    launchOnGLThread {
                        loadingPopup.close()
                        ToastPopup(message, screen)
                    }
                } catch (ex: Exception) {
                    Log.error("Could not autoload game", ex)
                    val (message) = LoadGameScreen.getLoadExceptionMessage(ex)
                    launchOnGLThread {
                        loadingPopup.close()
                        ToastPopup(message, screen)
                    }
                }
            } else {
                try {
                    screen.game.loadGame(savedGame)
                } catch (_: OutOfMemoryError) {
                    outOfMemory()
                } catch (ex: Exception) {
                    launchOnGLThread {
                        Log.error("Could not autoload game", ex)
                        loadingPopup.close()
                        ToastPopup("Cannot resume game!", screen)
                    }
                }
            }
        }
    }
}
