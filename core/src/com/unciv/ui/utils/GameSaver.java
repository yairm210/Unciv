package com.unciv.ui.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.ui.UnCivGame;

public class GameSaver {
    public static final String saveFilesFolder = "SaveFiles";

    public static FileHandle GetSave(String GameName) {
        return Gdx.files.local(saveFilesFolder + "/" + GameName);
    }

    public static void SaveGame(UnCivGame game, String GameName) {
        GetSave(GameName).writeString(new Json().toJson(game.civInfo), false);
    }

    public static void LoadGame(UnCivGame game, String GameName) {
        game.civInfo = new Json().fromJson(CivilizationInfo.class, GetSave(GameName).readString());
        for (CityInfo cityInfo : game.civInfo.cities){
            cityInfo.population.cityInfo = cityInfo;
            cityInfo.expansion.cityInfo = cityInfo;
            cityInfo.cityStats.cityInfo = cityInfo;
        }
    }
}
