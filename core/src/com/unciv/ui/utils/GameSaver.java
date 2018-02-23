package com.unciv.ui.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.unciv.ui.GameInfo;
import com.unciv.ui.UnCivGame;

public class GameSaver {
    public static final String saveFilesFolder = "SaveFiles";

    public static FileHandle GetSave(String GameName) {
        return Gdx.files.local(saveFilesFolder + "/" + GameName);
    }

    public static void SaveGame(UnCivGame game, String GameName) {
        GetSave(GameName).writeString(new Json().toJson(game.gameInfo), false);
    }

    public static void LoadGame(UnCivGame game, String GameName) {
        game.gameInfo = new Json().fromJson(GameInfo.class, GetSave(GameName).readString());
        game.gameInfo.setTransients();
    }
}
