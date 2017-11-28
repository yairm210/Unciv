package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.CivStats;

import java.util.HashSet;

/**
 * Created by LENOVO on 10/18/2017.
 */
public class CivilizationInfo {
    public static CivilizationInfo current(){return UnCivGame.Current.civInfo; }

    public CivStats civStats = new CivStats();
    public int baseHappiness = 15;
    public String civName = "Babylon";

    public CivilizationTech Tech = new CivilizationTech();
    public int turns = 1;

    public LinqCollection<CityInfo> Cities = new LinqCollection<CityInfo>();

    public TileMap tileMap = new TileMap(20);

    public int CurrentCity=0; //index!

    public CivilizationInfo(){
    }


    public CityInfo GetCurrentCity() { return Cities.get(CurrentCity); }

    public int TurnsToTech(String TechName) {
        return (int) Math.ceil((float)(GameBasics.Technologies.get(TechName).Cost - Tech.ResearchOfTech(TechName))
                / GetStatsForNextTurn().Science);
    }

    public void addCity(Vector2 location){
        CityInfo city = new CityInfo(this,location);
        if(Cities.size()==1) city.cityBuildings.BuiltBuildings.add("Palace");
    }

    public void NextTurn()//out boolean displayTech)
    {
        CivStats nextTurnStats = GetStatsForNextTurn();
        civStats.add(nextTurnStats);
        if(Cities.size() > 0) Tech.NextTurn(nextTurnStats.Science);

        for (CityInfo city : Cities.as(CityInfo.class)) city.nextTurn();

        for(TileInfo tile : tileMap.values()) if(tile.Unit!=null) tile.Unit.CurrentMovement = tile.Unit.MaxMovement;

        turns += 1;
    }

    public CivStats GetStatsForNextTurn() {
        CivStats statsForTurn = new CivStats() {{
            Happiness = baseHappiness;
        }};
        HashSet<String> LuxuryResources = new HashSet<String>();
        for (CityInfo city : Cities) {
            statsForTurn.add(city.getCityStats());
            LuxuryResources.addAll(city.getLuxuryResources());
        }
        statsForTurn.Happiness += LuxuryResources.size() * 5; // 5 happiness for each unique luxury in civ

        return statsForTurn;
    }
}

