package com.unciv.game;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Json;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.civinfo.Unit;
import com.unciv.game.pickerscreens.GameSaver;
import com.unciv.models.gamebasics.BasicHelp;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Technology;
import com.unciv.models.LinqHashMap;
import com.unciv.models.gamebasics.TechColumn;
import com.unciv.models.gamebasics.TileImprovement;

public class UnCivGame extends Game {

    public static UnCivGame Current;
    public CivilizationInfo civInfo;
    public GameSettings settings = new GameSettings();


    public WorldScreen worldScreen;
    public void create() {
        SetupGameBasics();
        Current = this;
        if(GameSaver.GetSave("Autosave").exists()) GameSaver.LoadGame(this,"Autosave");
        else startNewGame();

        worldScreen = new WorldScreen(this);
        setWorldScreen();
    }

    public void startNewGame(){
        civInfo = new CivilizationInfo();
        civInfo.tileMap.get(Vector2.Zero).Unit = new Unit("Settler",2);

        worldScreen = new WorldScreen(this);
        setWorldScreen();
    }

    public void setWorldScreen(){
        setScreen(worldScreen);
        worldScreen.update();
        Gdx.input.setInputProcessor(worldScreen.stage);
    }

    private <T> T GetFromJson(Class<T> tClass, String name){
        String jsonText = Gdx.files.internal("jsons/"+name+".json").readString();
        return new Json().fromJson(tClass,jsonText);
    }

    private <T extends com.unciv.models.stats.NamedStats> LinqHashMap<String,T> CreateHashmap(Class<T> tClass, T[] items){
        LinqHashMap<String,T> hashMap = new LinqHashMap<String, T>();
        for(T item:items) hashMap.put(item.GetName(),item);
        return hashMap;
    }

    private void SetupGameBasics() {
        GameBasics.Buildings = CreateHashmap(com.unciv.models.gamebasics.Building.class,GetFromJson(com.unciv.models.gamebasics.Building[].class,"Buildings"));
        GameBasics.Terrains =  CreateHashmap(com.unciv.models.gamebasics.Terrain.class,GetFromJson(com.unciv.models.gamebasics.Terrain[].class,"Terrains"));
        GameBasics.TileResources =  CreateHashmap(com.unciv.models.gamebasics.TileResource.class,GetFromJson(com.unciv.models.gamebasics.TileResource[].class,"TileResources"));
        GameBasics.TileImprovements =  CreateHashmap(TileImprovement.class,GetFromJson(TileImprovement[].class,"TileImprovements"));
        GameBasics.Helps  = CreateHashmap(BasicHelp.class,GetFromJson(BasicHelp[].class,"BasicHelp"));

        TechColumn[] TechColumns = GetFromJson(TechColumn[].class, "Techs");
        GameBasics.Technologies = new LinqHashMap<String, Technology>();
        for(TechColumn techColumn : TechColumns){
            for(com.unciv.models.gamebasics.Technology tech : techColumn.Techs){
                tech.Cost = techColumn.TechCost;
                tech.Column = techColumn;
                GameBasics.Technologies.put(tech.Name,tech);
            }
        }
        for(com.unciv.models.gamebasics.Building building : GameBasics.Buildings.values()){
            if(building.RequiredTech == null) continue;
            TechColumn column = building.GetRequiredTech().Column;
            building.Cost = building.IsWonder ? column.WonderCost : column.BuildingCost;
        }
    }

}
