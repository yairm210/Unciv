package com.unciv.ui.saves

import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.worldscreen.WorldScreen
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread


//todo reduce code duplication

object QuickSave {
    fun save(gameInfo: GameInfo, screen: WorldScreen) {
        val gameSaver = UncivGame.Current.gameSaver
        val toast = ToastPopup("Quicksaving...", screen)
        Concurrency.runOnNonDaemonThreadPool("QuickSaveGame") {
            gameSaver.saveGame(gameInfo, "QuickSave") {
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
        val gameSaver = UncivGame.Current.gameSaver
        val toast = ToastPopup("Quickloading...", screen)
        Concurrency.run("QuickLoadGame") {
            try {
                val loadedGame = gameSaver.loadGameByName("QuickSave")
                launchOnGLThread {
                    toast.close()
                    UncivGame.Current.loadGame(loadedGame)
                    ToastPopup("Quickload successful.", screen)
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    toast.close()
                    ToastPopup("Could not load game!", screen)
                }
            }
        }
    }

    fun autoLoadGame(screen: MainMenuScreen) {
        val loadingPopup = Popup(screen)
        loadingPopup.addGoodSizedLabel(Constants.loading)
        loadingPopup.open()
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
                savedGame = screen.game.gameSaver.loadLatestAutosave()
            } catch (oom: OutOfMemoryError) {
                outOfMemory()
                return@run
            } catch (ex: Exception) {
                launchOnGLThread {
                    loadingPopup.close()
                    ToastPopup("Cannot resume game!", screen)
                }
                return@run
            }

            if (savedGame.gameParameters.isOnlineMultiplayer) {
                try {
                    screen.game.onlineMultiplayer.loadGame(savedGame)
                } catch (oom: OutOfMemoryError) {
                    outOfMemory()
                }
            } else {
                launchOnGLThread { /// ... and load it into the screen on main thread for GL context
                    try {
                        screen.game.loadGame(savedGame)
                    } catch (oom: OutOfMemoryError) {
                        outOfMemory()
                    }
                }
            }
        }
    }
}
