package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqCounter;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.CivStats;
import com.unciv.models.stats.FullStats;

import java.util.Collection;

/**
 * Created by LENOVO on 10/18/2017.
 */
public class CivilizationInfo {
    public static CivilizationInfo current(){ return UnCivGame.Current.civInfo; }

    public CivStats civStats = new CivStats();
    public int baseHappiness = 15;
    public int numberOfGoldenAges=0;
    public int turnsLeftForCurrentGoldenAge=0;
    public String civName = "Babylon";

    public FullStats greatPersonPoints = new FullStats();

    public CivilizationTech tech = new CivilizationTech();
    public int turns = 1;
    public LinqCollection<String> notifications = new LinqCollection<String>();

    public LinqCollection<CityInfo> cities = new LinqCollection<CityInfo>();

    public TileMap tileMap = new TileMap(20);
    public ScienceVictory scienceVictory = new ScienceVictory();

    public int currentCity =0; //index!

    public CivilizationInfo(){
    }

    public CityInfo getCurrentCity() { return cities.get(currentCity); }

    public int turnsToTech(String TechName) {
        return (int) Math.ceil((float)(GameBasics.Technologies.get(TechName).cost - tech.researchOfTech(TechName))
                / getStatsForNextTurn().science);
    }

    public void addCity(Vector2 location){
        CityInfo city = new CityInfo(this,location);
    }

    public CityInfo getCapital(){
        return cities.first(new Predicate<CityInfo>() {
            @Override
            public boolean evaluate(CityInfo arg0) {
                return arg0.cityConstructions.isBuilt("Palace");
            }
        });
    }

    public boolean isGoldenAge(){return turnsLeftForCurrentGoldenAge>0;}
    public int happinessRequiredForNextGoldenAge(){
        return (int) ((500+numberOfGoldenAges*250)*(1+cities.size()/100.0)); //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    public void nextTurn()
    {
        notifications.clear();
        CivStats nextTurnStats = getStatsForNextTurn();
        civStats.add(nextTurnStats);

        int happiness = getHappinessForNextTurn();
        if(!isGoldenAge() && happiness>0)
            civStats.happiness += happiness;

        if(cities.size() > 0) tech.nextTurn((int)nextTurnStats.science);

        for (CityInfo city : cities) city.nextTurn();

        for(TileInfo tile : tileMap.values()) tile.nextTurn();

        for (CityInfo city : cities) city.updateCityStats();
        turns++;
        if(isGoldenAge()) turnsLeftForCurrentGoldenAge--;

        if(civStats.happiness > happinessRequiredForNextGoldenAge()){
            enterGoldenAge();
            numberOfGoldenAges++;
        }
    }

    public void enterGoldenAge(){
        civStats.happiness-=happinessRequiredForNextGoldenAge();
        turnsLeftForCurrentGoldenAge = 10;
        if(getBuildingUniques().contains("GoldenAgeLengthIncrease")) turnsLeftForCurrentGoldenAge*=1.5;
    }

    public CivStats getStatsForNextTurn() {
        CivStats statsForTurn = new CivStats();
        for (CityInfo city : cities) {
            statsForTurn.add(city.cityStats);
        }
        statsForTurn.happiness=0;
        for(TileInfo tile : tileMap.values()) {
            if (tile.roadStatus == RoadStatus.Road) statsForTurn.gold += 1;
            else if(tile.roadStatus == RoadStatus.Railroad) statsForTurn.gold +=2;
        }
        return statsForTurn;
    }

    public int getHappinessForNextTurn(){
        int happiness = baseHappiness;
        happiness += new LinqCollection<TileResource>(getCivResources().keySet()).count(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.resourceType == ResourceType.Luxury;
            }
        }) * 5; // 5 happiness for each unique luxury in civ
        for (CityInfo city : cities) {
            happiness += city.getCityHappiness();
        }
        return happiness;
    }

    public LinqCounter<TileResource> getCivResources(){
        LinqCounter<TileResource> civResources = new LinqCounter<TileResource>();
        for (CityInfo city : cities) civResources.add(city.getCityResources());

        return civResources;
    }

    public LinqCollection<String> getBuildingUniques(){
        return cities.selectMany(new LinqCollection.Func<CityInfo, Collection<? extends String>>() {
            @Override
            public Collection<? extends String> GetBy(CityInfo arg0) {
                return arg0.cityConstructions.getBuiltBuildings().select(new LinqCollection.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.unique;
                    }
                });
            }
        }).unique();
    }
}

