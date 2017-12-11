package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.game.VictoryScreen;
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
    public Building getCurrentBuilding(){return getGameBuilding(currentBuilding);}

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


        Building gameBuilding = getGameBuilding(currentBuilding);

        // Let's try to remove the building from the city, and seee if we can still build it (weneed to remove because of wonders etc.
        String saveCurrentBuilding = currentBuilding;
        currentBuilding = null;
        if(!canBuild(gameBuilding)){
            // We can't build this building anymore! (Wonder has been built / resource is gone / etc.)
            CivilizationInfo.current().notifications.add("Cannot continue work on "+saveCurrentBuilding);
            chooseNextBuilding();
            gameBuilding = getGameBuilding(currentBuilding);
        }
        else currentBuilding = saveCurrentBuilding;

        if (!inProgressBuildings.containsKey(currentBuilding)) inProgressBuildings.put(currentBuilding, 0);
        inProgressBuildings.put(currentBuilding, inProgressBuildings.get(currentBuilding) + Math.round(cityStats.production));

        if (inProgressBuildings.get(currentBuilding) >= gameBuilding.cost)
        {
            if (currentBuilding.equals(Worker) || currentBuilding.equals(Settler))
                UnCivGame.Current.civInfo.tileMap.get(cityLocation).unit = new Unit(currentBuilding,2);
            else if("SpaceshipPart".equals(gameBuilding.unique)) {
                CivilizationInfo.current().scienceVictory.currentParts.add(currentBuilding, 1);
                if(CivilizationInfo.current().scienceVictory.unconstructedParts().isEmpty())
                    UnCivGame.Current.setScreen(new VictoryScreen(UnCivGame.Current));
            }

            else
            {
                builtBuildings.add(currentBuilding);
                if (gameBuilding.providesFreeBuilding != null && !builtBuildings.contains(gameBuilding.providesFreeBuilding))
                    builtBuildings.add(gameBuilding.providesFreeBuilding);
                if (gameBuilding.freeTechs != 0) UnCivGame.Current.civInfo.tech.freeTechs += gameBuilding.freeTechs;
            }

            inProgressBuildings.remove(currentBuilding);

            CivilizationInfo.current().notifications.add(currentBuilding+" has been built in "+getCity().name);

            chooseNextBuilding();
        }

    }

    private void chooseNextBuilding() {
        currentBuilding = getBuildableBuildings().first(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                if(arg0.equals(Settler) || arg0.equals(Worker) || getGameBuilding(arg0).isWonder) return false;
                return !builtBuildings.contains(arg0);
            }
        });
        if (currentBuilding == null) currentBuilding = Worker;

        CivilizationInfo.current().notifications.add("Work has started on "+currentBuilding);
    }

    public boolean canBuild(final Building building)
    {
        CivilizationInfo civInfo = UnCivGame.Current.civInfo;
        if(isBuilt(building.name)) return false;
        if(building.resourceBoostingBuilding) {
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
        if(building.cannotBeBuiltWith != null && isBuilt(building.cannotBeBuiltWith)) return false;
        if("MustBeNextToDesert".equals(building.unique) &&
                !civInfo.tileMap.getTilesInDistance(cityLocation,1).any(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.baseTerrain.equals("Desert");
                    }
                }))
            return false;
        if(building.requiredResource!=null &&
                !civInfo.getCivResources().keySet().contains(GameBasics.TileResources.get(building.requiredResource)))
            return false; // Only checks if exists, doesn't check amount - todo
        
        if(building.unique.equals("SpaceshipPart")){
            if(!civInfo.getBuildingUniques().contains("ApolloProgram")) return false;
            if(civInfo.scienceVictory.requiredParts.get(building.name)==0) return false; // Don't need to build any more of these!
        }

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
        stats.science += getCity().getBuildingUniques().count(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return "SciencePer2Pop".equals(arg0);
            }
        }) * getCity().population/2; // Library and public school unique (not actualy unique, though...hmm)
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

    public int workDone(String buildingName) {
        if (inProgressBuildings.containsKey(buildingName))
            return inProgressBuildings.get(buildingName);
        return 0;
    }

    public int turnsToBuilding(String buildingName)
    {
        float workLeft = getGameBuilding(buildingName).cost - workDone(buildingName); // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = getCity().cityStats;
        int production = Math.round(cityStats.production);
        if (buildingName.equals(Settler)) production += cityStats.food;

        return (int) Math.ceil(workLeft / production);
    }

    public void purchaseBuilding(String buildingName) {
        CivilizationInfo.current().civStats.gold -= getGameBuilding(buildingName).getGoldCost();
        builtBuildings.add(buildingName);
        if(currentBuilding.equals(buildingName)) chooseNextBuilding();
    }

    public String getCityProductionText(){
        String result = currentBuilding;
        if(!result.equals("Science") && !result.equals("Gold"))
            result+="\r\n"+turnsToBuilding(currentBuilding)+" turns";
        return result;
    }
}