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

    public LinqCollection<String> BuiltBuildings = new LinqCollection<String>();
    public HashMap<String, Integer> InProgressBuildings = new HashMap<String, Integer>();
    public String CurrentBuilding = Worker; // default starting building!

    public CityInfo GetCity(){return UnCivGame.Current.civInfo.tileMap.get(cityLocation).getCity(); }
    public boolean IsBuilt(String buildingName) { return BuiltBuildings.contains(buildingName); }
    public boolean IsBuilding(String buildingName) { return CurrentBuilding.equals(buildingName); }

    Building GetGameBuilding(String buildingName) { return GameBasics.Buildings.get(buildingName); }
    public LinqCollection<Building> GetBuiltBuildings(){ return  BuiltBuildings.select(new LinqCollection.Func<String, Building>() {
        @Override
        public Building GetBy(String arg0) {
            return GetGameBuilding(arg0);
        }
    }); }

    public void NextTurn(int ProductionProduced)
    {
        if (CurrentBuilding == null) return;
        if (!InProgressBuildings.containsKey(CurrentBuilding)) InProgressBuildings.put(CurrentBuilding, 0);
        InProgressBuildings.put(CurrentBuilding, InProgressBuildings.get(CurrentBuilding) + ProductionProduced);

        if (InProgressBuildings.get(CurrentBuilding) >= GetGameBuilding(CurrentBuilding).cost)
        {
            if (CurrentBuilding.equals(Worker) || CurrentBuilding.equals(Settler))
                UnCivGame.Current.civInfo.tileMap.get(cityLocation).unit = new Unit(CurrentBuilding,2);

            else
            {
                BuiltBuildings.add(CurrentBuilding);
                Building gameBuilding = GetGameBuilding(CurrentBuilding);
                if (gameBuilding.providesFreeBuilding != null && !BuiltBuildings.contains(gameBuilding.providesFreeBuilding))
                    BuiltBuildings.add(gameBuilding.providesFreeBuilding);
                if (gameBuilding.freeTechs != 0) UnCivGame.Current.civInfo.tech.FreeTechs += gameBuilding.freeTechs;
            }

            InProgressBuildings.remove(CurrentBuilding);

            // Choose next building to build
            CurrentBuilding = GetBuildableBuildings().first(new Predicate<String>() {
                @Override
                public boolean evaluate(String arg0) {
                    if(arg0.equals(Settler) || arg0.equals(Worker)) return false;
                    return !BuiltBuildings.contains(arg0);
                }
            });
            if (CurrentBuilding == null) CurrentBuilding = Worker;
        }

    }

    public boolean CanBuild(final Building building)
    {
        CivilizationInfo civInfo = UnCivGame.Current.civInfo;
        if(IsBuilt(building.name)) return false;
//        if (building.name.equals("Worker") || building.name.equals("Settler")) return false;
        if(building.resourceRequired) {
            boolean containsResourceWithImprovement = GetCity().getTilesInRange()
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

        if (building.requiredTech != null && !civInfo.tech.IsResearched(building.requiredTech)) return false;
        if (building.isWonder && civInfo.cities
                .any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        CityBuildings CB = arg0.cityBuildings;
                        return CB.IsBuilding(building.name) || CB.IsBuilt(building.name);
                    }
                }) ) return false;
        if (building.requiredBuilding != null && !IsBuilt(building.requiredBuilding)) return false;
        if (building.requiredBuildingInAllCities != null ||
                civInfo.cities.any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        return arg0.cityBuildings.IsBuilt(building.requiredBuildingInAllCities);
                    }
                }) ) return false;

        return true;
    }

    public com.unciv.models.LinqCollection<String> GetBuildableBuildings()
    {
        return new com.unciv.models.LinqCollection<Building>(GameBasics.Buildings.values())
                .where(new Predicate<Building>() {
            @Override
            public boolean evaluate(Building arg0) { return CanBuild(arg0); }
        })
                .select(new com.unciv.models.LinqCollection.Func<Building, String>() {
                    @Override
                    public String GetBy(Building arg0) {
                        return arg0.name;
                    }
                });
    }

    public FullStats GetStats()
    {
        FullStats stats = new FullStats();
        for (String building : BuiltBuildings)
        {
            Building gameBuilding = GetGameBuilding(building);
            stats.add(gameBuilding);
            //if (gameBuilding.GetFlatBonusStats != null) stats.add(gameBuilding.GetFlatBonusStats(cityInfo));
            stats.gold -= gameBuilding.maintainance;
        }
        return stats;
    }

    public int TurnsToBuilding(String buildingName)
    {
        int workDone = 0;
        if (InProgressBuildings.containsKey(buildingName)) workDone = InProgressBuildings.get(buildingName);
        float workLeft = GetGameBuilding(buildingName).cost - workDone; // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = GetCity().getCityStats();
        int production = cityStats.production;
        if (buildingName.equals(Settler)) production += cityStats.food;

        return (int) Math.ceil(workLeft / production);
    }
}