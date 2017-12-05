package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.FullStats;

import java.util.HashMap;


public class CityBuildings
{
    static final String Worker="Worker";
    static final String Settler="Settler";

    public Vector2 cityLocation;

    public CityBuildings(){} // for json parsing, we need to have a default constructor

    public CityBuildings(CityInfo cityInfo)
    {
        cityLocation = cityInfo.cityLocation;
    }

    public LinqCollection<String> builtBuildings = new LinqCollection<String>();
    public HashMap<String, Integer> inProgressBuildings = new HashMap<String, Integer>();
    public String currentBuilding = Worker; // default starting building!

    public CityInfo getCity(){return UnCivGame.Current.civInfo.tileMap.get(cityLocation).getCity(); }
    public boolean isBuilt(String buildingName) { return builtBuildings.contains(buildingName); }
    public boolean isBuilding(String buildingName) { return currentBuilding.equals(buildingName); }

    Building getGameBuilding(String buildingName) { return GameBasics.Buildings.get(buildingName); }
    public LinqCollection<Building> getBuiltBuildings(){ return  builtBuildings.select(new LinqCollection.Func<String, Building>() {
        @Override
        public Building GetBy(String arg0) {
            return getGameBuilding(arg0);
        }
    }); }

    public void nextTurn(FullStats cityStats)
    {
        if (currentBuilding == null) return;
        if(currentBuilding.equals("Gold")) {cityStats.gold+=cityStats.production/3; return;}
        if(currentBuilding.equals("Science")) {cityStats.science+=cityStats.production/3; return;}
        if (!inProgressBuildings.containsKey(currentBuilding)) inProgressBuildings.put(currentBuilding, 0);
        inProgressBuildings.put(currentBuilding, inProgressBuildings.get(currentBuilding) + Math.round(cityStats.production));

        if (inProgressBuildings.get(currentBuilding) >= getGameBuilding(currentBuilding).cost)
        {
            if (currentBuilding.equals(Worker) || currentBuilding.equals(Settler))
                UnCivGame.Current.civInfo.tileMap.get(cityLocation).unit = new Unit(currentBuilding,2);

            else
            {
                builtBuildings.add(currentBuilding);
                Building gameBuilding = getGameBuilding(currentBuilding);
                if (gameBuilding.providesFreeBuilding != null && !builtBuildings.contains(gameBuilding.providesFreeBuilding))
                    builtBuildings.add(gameBuilding.providesFreeBuilding);
                if (gameBuilding.freeTechs != 0) UnCivGame.Current.civInfo.tech.freeTechs += gameBuilding.freeTechs;
            }

            inProgressBuildings.remove(currentBuilding);

            CivilizationInfo.current().notifications.add(currentBuilding+" has been built in "+getCity().name);

            // Choose next building to build
            currentBuilding = getBuildableBuildings().first(new Predicate<String>() {
                @Override
                public boolean evaluate(String arg0) {
                    if(arg0.equals(Settler) || arg0.equals(Worker)) return false;
                    return !builtBuildings.contains(arg0);
                }
            });
            if (currentBuilding == null) currentBuilding = Worker;

            CivilizationInfo.current().notifications.add("Work has started on "+currentBuilding);
        }

    }

    public boolean canBuild(final Building building)
    {
        CivilizationInfo civInfo = UnCivGame.Current.civInfo;
        if(isBuilt(building.name)) return false;
//        if (building.name.equals("Worker") || building.name.equals("Settler")) return false;
        if(building.resourceRequired) {
            boolean containsResourceWithImprovement = getCity().getTilesInRange()
                    .any(new Predicate<TileInfo>() {
                @Override
                public boolean evaluate(TileInfo tile) {
                    return tile.resource != null
                        && building.name.equals(tile.getTileResource().building)
                        && tile.getTileResource().improvement.equals(tile.improvement);
                }
            });
            if(!containsResourceWithImprovement) return false;
        }

        if (building.requiredTech != null && !civInfo.tech.isResearched(building.requiredTech)) return false;
        if (building.isWonder && civInfo.cities
                .any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        CityBuildings CB = arg0.cityBuildings;
                        return CB.isBuilding(building.name) || CB.isBuilt(building.name);
                    }
                }) ) return false;
        if (building.requiredBuilding != null && !isBuilt(building.requiredBuilding)) return false;
        if (building.requiredBuildingInAllCities != null ||
                civInfo.cities.any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        return arg0.cityBuildings.isBuilt(building.requiredBuildingInAllCities);
                    }
                }) ) return false;

        return true;
    }

    public LinqCollection<String> getBuildableBuildings()
    {
        return new LinqCollection<Building>(GameBasics.Buildings.values())
                .where(new Predicate<Building>() {
            @Override
            public boolean evaluate(Building arg0) { return canBuild(arg0); }
        })
                .select(new com.unciv.models.LinqCollection.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.name;
                    }
                });
    }

    public FullStats getStats()
    {
        FullStats stats = new FullStats();
        for(Building building : getBuiltBuildings()) stats.add(building);
        if(getCity().getBuildingUniques().contains("SciencePer2Pop")) stats.science+=getCity().population/2; // Library unique
        return stats;
    }

    public int getMaintainanceCosts(){
        int maintainanceTotal = 0;
        for( Building building : getBuiltBuildings()) maintainanceTotal+=building.maintainance;
        return maintainanceTotal;
    }

    public FullStats getStatPercentBonuses(){

        FullStats stats = new FullStats();
        for(Building building : getBuiltBuildings())
            if(building.percentStatBonus != null)
                stats.add(building.percentStatBonus);

        return stats;
    }

    public int turnsToBuilding(String buildingName)
    {
        int workDone = 0;
        if (inProgressBuildings.containsKey(buildingName)) workDone = inProgressBuildings.get(buildingName);
        float workLeft = getGameBuilding(buildingName).cost - workDone; // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = getCity().getCityStats();
        int production = Math.round(cityStats.production);
        if (buildingName.equals(Settler)) production += cityStats.food;

        return (int) Math.ceil(workLeft / production);
    }

    public String getCityProductionText(){
        String result = currentBuilding;
        if(!result.equals("Science") && !result.equals("Gold"))
            result+="\r\n"+turnsToBuilding(currentBuilding)+" turns";
        return result;
    }
}