package com.unciv.logic.civilization;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.sun.jmx.remote.security.NotificationAccessController;
import com.unciv.logic.city.CityInfo;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.logic.map.TileMap;
import com.unciv.ui.UnCivGame;
import com.unciv.ui.pickerscreens.PolicyPickerScreen;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqCounter;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.CivStats;
import com.unciv.models.stats.FullStats;

import java.util.Collection;



public class CivilizationInfo {
    public static CivilizationInfo current(){ return UnCivGame.Current.civInfo; }

    //public CivStats civStats = new CivStats();
    public int gold = 0;
    public int baseHappiness = 15;
    public String civName = "Babylon";


    public TechManager tech = new TechManager();
    public PolicyManager policies = new PolicyManager();
    public GoldenAgeManager goldenAges = new GoldenAgeManager();
    public GreatPersonManager greatPeople = new GreatPersonManager();
    public int turns = 1;


    public Linq<Notification> notifications = new Linq<Notification>();
    public void addNotification(String text, Vector2 location){
        notifications.add(new Notification(text,location));
    }
    public Linq<String> tutorial = new Linq<String>();

    public Linq<CityInfo> cities = new Linq<CityInfo>();

    public TileMap tileMap = new TileMap(20);
    public ScienceVictoryManager scienceVictory = new ScienceVictoryManager();

    public int currentCity =0; //index!

    public CivilizationInfo(){}

    public CityInfo getCurrentCity() { return cities.get(currentCity); }

    public int turnsToTech(String TechName) {
        return (int) Math.ceil((float)(GameBasics.Technologies.get(TechName).cost - tech.researchOfTech(TechName))
                / getStatsForNextTurn().science);
    }

    public void addCity(Vector2 location){
        CityInfo newCity = new CityInfo(this,location);
        newCity.cityConstructions.chooseNextConstruction();
    }

    public CityInfo getCapital(){
        return cities.first(new Predicate<CityInfo>() {
            @Override
            public boolean evaluate(CityInfo arg0) {
                return arg0.cityConstructions.isBuilt("Palace");
            }
        });
    }

    public void nextTurn()
    {
        notifications.clear();
        CivStats nextTurnStats = getStatsForNextTurn();
        policies.nextTurn(nextTurnStats.culture);

        int happiness = getHappinessForNextTurn();

        if(cities.size() > 0) tech.nextTurn((int)nextTurnStats.science);

        for (CityInfo city : cities) city.nextTurn();

        greatPeople.greatPersonPointsForTurn();

        // We need to update the stats after ALL the cities are done updating because
        // maybe one of them has a wonder that affects the stats of all the rest of the cities

        // Here we need to filter out the tiles that don''t have units, because what happens if a unit in in tile 1,
        // gets activated, and then moves to tile 2, which is activated later? Problem!
        for(TileInfo tile : tileMap.values().where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.unit!=null;
            }
        })) tile.nextTurn();

        goldenAges.nextTurn(happiness);

        for (CityInfo city : cities) city.cityStats.update();
        turns++;
    }

    public CivStats getStatsForNextTurn() {
        CivStats statsForTurn = new CivStats();
        for (CityInfo city : cities) {
            statsForTurn.add(city.cityStats.currentCityStats);
        }
        statsForTurn.happiness=0;

        int transportationUpkeep = 0;
        for(TileInfo tile : tileMap.values()) {
            if(tile.isCityCenter()) continue;
            if (tile.roadStatus == RoadStatus.Road) transportationUpkeep+=1;
            else if(tile.roadStatus == RoadStatus.Railroad) transportationUpkeep+=2;
        }
        if(policies.isAdopted("Trade Unions")) transportationUpkeep *= 2/3f;
        statsForTurn.gold -=transportationUpkeep;

        if(policies.isAdopted("Mandate Of Heaven"))
            statsForTurn.culture+=getHappinessForNextTurn()/2;
        return statsForTurn;
    }

    public int getHappinessForNextTurn(){
        int happiness = baseHappiness;
        int happinessPerUniqueLuxury = 5;
        if(policies.isAdopted("Protectionism")) happinessPerUniqueLuxury+=1;
        happiness += new Linq<TileResource>(getCivResources().keySet()).count(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.resourceType == ResourceType.Luxury;
            }
        }) * happinessPerUniqueLuxury;
        for (CityInfo city : cities) {
            happiness += city.cityStats.getCityHappiness();
        }
        if(getBuildingUniques().contains("HappinessPerSocialPolicy"))
            happiness+=policies.getAdoptedPolicies().count(new Predicate<String>() {
                @Override
                public boolean evaluate(String arg0) {
                    return !arg0.endsWith("Complete");
                }
            });
        return happiness;
    }

    public LinqCounter<TileResource> getCivResources(){
        LinqCounter<TileResource> civResources = new LinqCounter<TileResource>();
        for (CityInfo city : cities) civResources.add(city.getCityResources());

        return civResources;
    }

    public Linq<String> getBuildingUniques(){
        return cities.selectMany(new Linq.Func<CityInfo, Collection<? extends String>>() {
            @Override
            public Collection<? extends String> GetBy(CityInfo arg0) {
                return arg0.cityConstructions.getBuiltBuildings().select(new Linq.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.unique;
                    }
                });
            }
        }).unique();
    }
}

