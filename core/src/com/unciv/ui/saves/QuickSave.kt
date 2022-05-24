package com.unciv.ui.saves

import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.worldscreen.WorldScreen


//todo reduce code duplication

object QuickSave {
    fun save(gameInfo: GameInfo, screen: WorldScreen) {
        val gameSaver = UncivGame.Current.gameSaver
        val toast = ToastPopup("Quicksaving...", screen)
        launchCrashHandling("SaveGame", runAsDaemon = false) {
            gameSaver.saveGame(gameInfo, "QuickSave") {
                postCrashHandlingRunnable {
                    toast.close()
                    if (it != null)
                        ToastPopup("Could not save game!", screen)
                    else {
                        ToastPopup("Quicksave successful.", screen)
                    }
                }
            }
        }
    }

    fun load(screen: WorldScreen) {
        val gameSaver = UncivGame.Current.gameSaver
        val toast = ToastPopup("Quickloading...", screen)
        launchCrashHandling("LoadGame") {
            try {
                val loadedGame = gameSaver.loadGameByName("QuickSave")
                postCrashHandlingRunnable {
                    toast.close()
                    UncivGame.Current.loadGame(loadedGame)
                    ToastPopup("Quickload successful.", screen)
                }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    ToastPopup("Could not load game!", screen)
                }
            }
        }
    }

    fun autoLoadGame(screen: MainMenuScreen) {
        val loadingPopup = Popup(screen)
        loadingPopup.addGoodSizedLabel(Constants.loading)
        loadingPopup.open()
        launchCrashHandling("autoLoadGame") {
            // Load game from file to class on separate thread to avoid ANR...
            fun outOfMemory() {
                postCrashHandlingRunnable {
                    loadingPopup.close()
                    ToastPopup("Not enough memory on phone to load game!", screen)
                }
            }

            val savedGame: GameInfo
            try {
                savedGame = screen.game.gameSaver.loadLatestAutosave()
            } catch (oom: OutOfMemoryError) {
                outOfMemory()
                return@launchCrashHandling
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    loadingPopup.close()
                    ToastPopup("Cannot resume game!", screen)
                }
                return@launchCrashHandling
            }

            postCrashHandlingRunnable { /// ... and load it into the screen on main thread for GL context
                try {
                    screen.game.loadGame(savedGame)
                    screen.dispose()
                } catch (oom: OutOfMemoryError) {
                    outOfMemory()
                }
            }
        }
    }
}
