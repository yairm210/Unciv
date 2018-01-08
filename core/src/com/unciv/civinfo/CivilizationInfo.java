package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.game.pickerscreens.PolicyPickerScreen;
import com.unciv.models.LinqCollection;
import com.unciv.models.LinqCounter;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.CivStats;
import com.unciv.models.stats.FullStats;

import java.util.Collection;
import java.util.Vector;

/**
 * Created by LENOVO on 10/18/2017.
 */



public class CivilizationInfo {
    public static CivilizationInfo current(){ return UnCivGame.Current.civInfo; }

    public CivStats civStats = new CivStats();
    public int baseHappiness = 15;
    public int numberOfGoldenAges=0;
    public int turnsLeftForCurrentGoldenAge=0;
    public int pointsForNextGreatPerson=100;
    public String civName = "Babylon";

    public FullStats greatPersonPoints = new FullStats();

    public CivilizationTech tech = new CivilizationTech();
    public LinqCollection<String> policies = new LinqCollection<String>();
    public int freePolicies=0;
    public int turns = 1;

    public class Notification{
        public final String text;
        public final Vector2 location;

        Notification(String text, Vector2 location) {
            this.text = text;
            this.location = location;
        }
    }
    public LinqCollection<Notification> notifications = new LinqCollection<Notification>();
    public void addNotification(String text, Vector2 location){
        notifications.add(new Notification(text,location));
    }
    public LinqCollection<String> tutorial = new LinqCollection<String>();

    public LinqCollection<CityInfo> cities = new LinqCollection<CityInfo>();

    public TileMap tileMap = new TileMap(20);
    public ScienceVictory scienceVictory = new ScienceVictory();

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

    public boolean isGoldenAge(){return turnsLeftForCurrentGoldenAge>0;}
    public int happinessRequiredForNextGoldenAge(){
        return (int) ((500+numberOfGoldenAges*250)*(1+cities.size()/100.0)); //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    public void nextTurn()
    {
        notifications.clear();
        CivStats nextTurnStats = getStatsForNextTurn();
        boolean couldAdoptPolicyBefore = canAdoptPolicy();
        civStats.add(nextTurnStats);
        if(!couldAdoptPolicyBefore && canAdoptPolicy())
            UnCivGame.Current.setScreen(new PolicyPickerScreen());

        int happiness = getHappinessForNextTurn();
        if(!isGoldenAge() && happiness>0)
            civStats.happiness += happiness;

        if(cities.size() > 0) tech.nextTurn((int)nextTurnStats.science);

        for (CityInfo city : cities) city.nextTurn();
        greatPersonPointsForTurn();

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

        if(isGoldenAge()) turnsLeftForCurrentGoldenAge--;

        if(civStats.happiness > happinessRequiredForNextGoldenAge()){
            civStats.happiness-=happinessRequiredForNextGoldenAge();
            enterGoldenAge();
            numberOfGoldenAges++;
        }

        for (CityInfo city : cities) city.updateCityStats();
        turns++;
    }

    public void addGreatPerson(String unitName){ // This is also done by some wonders and social policies, remember
        tileMap.placeUnitNearTile(cities.get(0).cityLocation,unitName);
        addNotification("A "+unitName+" has been born!",cities.get(0).cityLocation);
    }

    public void greatPersonPointsForTurn(){
        for(CityInfo city : cities)
            greatPersonPoints.add(city.getGreatPersonPoints());

        if(greatPersonPoints.science>pointsForNextGreatPerson){
            greatPersonPoints.science-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Scientist");
        }
        if(greatPersonPoints.production>pointsForNextGreatPerson){
            greatPersonPoints.production-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Engineer");
        }
        if(greatPersonPoints.culture>pointsForNextGreatPerson){
            greatPersonPoints.culture-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Artist");
        }
        if(greatPersonPoints.gold>pointsForNextGreatPerson){
            greatPersonPoints.gold-=pointsForNextGreatPerson;
            pointsForNextGreatPerson*=2;
            addGreatPerson("Great Merchant");
        }
    }

    public void enterGoldenAge(){
        int turnsToGoldenAge = 10;
        if(getBuildingUniques().contains("GoldenAgeLengthIncrease")) turnsToGoldenAge*=1.5;
        if(policies.contains("Freedom Complete")) turnsToGoldenAge*=1.5;
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge;
        addNotification("You have entered a golden age!",null);
    }

    public CivStats getStatsForNextTurn() {
        CivStats statsForTurn = new CivStats();
        for (CityInfo city : cities) {
            statsForTurn.add(city.cityStats);
        }
        statsForTurn.happiness=0;

        int transportationUpkeep = 0;
        for(TileInfo tile : tileMap.values()) {
            if(tile.isCityCenter()) continue;
            if (tile.roadStatus == RoadStatus.Road) transportationUpkeep+=1;
            else if(tile.roadStatus == RoadStatus.Railroad) transportationUpkeep+=2;
        }
        if(policies.contains("Trade Unions")) transportationUpkeep *= 2/3f;
        statsForTurn.gold -=transportationUpkeep;

        if(policies.contains("Mandate Of Heaven"))
            statsForTurn.culture+=getHappinessForNextTurn()/2;
        return statsForTurn;
    }

    public int getHappinessForNextTurn(){
        int happiness = baseHappiness;
        int happinessPerUniqueLuxury = 5;
        if(policies.contains("Protectionism")) happinessPerUniqueLuxury+=1;
        happiness += new LinqCollection<TileResource>(getCivResources().keySet()).count(new Predicate<TileResource>() {
            @Override
            public boolean evaluate(TileResource arg0) {
                return arg0.resourceType == ResourceType.Luxury;
            }
        }) * happinessPerUniqueLuxury;
        for (CityInfo city : cities) {
            happiness += city.getCityHappiness();
        }
        if(getBuildingUniques().contains("HappinessPerSocialPolicy"))
            happiness+=policies.count(new Predicate<String>() {
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

    public int getCultureNeededForNextPolicy(){
        // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
        int basicPolicies = policies.count(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return !arg0.endsWith("Complete");
            }
        });
        double baseCost = 25+ Math.pow(basicPolicies*6,1.7);
        double cityModifier = 0.3*(cities.size()-1);
        if(policies.contains("Representation")) cityModifier *= 2/3f;
        int cost = (int) Math.round(baseCost*(1+cityModifier));
        if(policies.contains("Piety Complete")) cost*=0.9;
        if(getBuildingUniques().contains("PolicyCostReduction")) cost*=0.9;
        return cost-cost%5; // round down to nearest 5
    }

    public boolean canAdoptPolicy(){
        return civStats.culture >= getCultureNeededForNextPolicy();
    }
}

