package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;
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

    public ArrayList<String> BuiltBuildings = new ArrayList<String>();
    public HashMap<String, Integer> InProgressBuildings = new HashMap<String, Integer>();
    public String CurrentBuilding = Worker; // default starting building!

    public CityInfo GetCity(){return UnCivGame.Current.civInfo.tileMap.get(cityLocation).GetCity(); }
    public boolean IsBuilt(String buildingName) { return BuiltBuildings.contains(buildingName); }
    public boolean IsBuilding(String buildingName) { return CurrentBuilding.equals(buildingName); }

    Building GetGameBuilding(String buildingName) { return GameBasics.Buildings.get(buildingName); }

    public void NextTurn(int ProductionProduced)
    {
        if (CurrentBuilding == null) return;
        if (!InProgressBuildings.containsKey(CurrentBuilding)) InProgressBuildings.put(CurrentBuilding, 0);
        InProgressBuildings.put(CurrentBuilding, InProgressBuildings.get(CurrentBuilding) + ProductionProduced);

        if (InProgressBuildings.get(CurrentBuilding) >= GetGameBuilding(CurrentBuilding).Cost)
        {
            if (CurrentBuilding.equals(Worker) || CurrentBuilding.equals(Settler))
                UnCivGame.Current.civInfo.tileMap.get(cityLocation).Unit = new Unit(CurrentBuilding,2);

            else
            {
                BuiltBuildings.add(CurrentBuilding);
                Building gameBuilding = GetGameBuilding(CurrentBuilding);
                if (gameBuilding.ProvidesFreeBuilding != null && !BuiltBuildings.contains(gameBuilding.ProvidesFreeBuilding))
                    BuiltBuildings.add(gameBuilding.ProvidesFreeBuilding);
                if (gameBuilding.FreeTechs != 0) UnCivGame.Current.civInfo.Tech.FreeTechs += gameBuilding.FreeTechs;
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
        if(IsBuilt(building.Name)) return false;
//        if (building.Name.equals("Worker") || building.Name.equals("Settler")) return false;
        if(building.ResourceRequired) {
            boolean containsResourceWithImprovement = GetCity().GetCityTiles()
                    .any(new Predicate<TileInfo>() {
                @Override
                public boolean evaluate(TileInfo tile) {
                    return tile.Resource != null
                        && building.Name.equals(tile.GetTileResource().Building)
                        && tile.GetTileResource().Improvement.equals(tile.Improvement);
                }
            });
            if(!containsResourceWithImprovement) return false;
        }

        if (building.RequiredTech != null && !civInfo.Tech.IsResearched(building.RequiredTech)) return false;
        if (building.IsWonder && civInfo.Cities
                .any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        CityBuildings CB = arg0.cityBuildings;
                        return CB.IsBuilt(building.Name) || CB.IsBuilt(building.Name);
                    }
                }) ) return false;
        if (building.RequiredBuilding != null && !IsBuilt(building.RequiredBuilding)) return false;
        if (building.RequiredBuildingInAllCities != null ||
                civInfo.Cities.any(new Predicate<CityInfo>() {
                    @Override
                    public boolean evaluate(CityInfo arg0) {
                        return arg0.cityBuildings.IsBuilt(building.RequiredBuildingInAllCities);
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
                        return arg0.Name;
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
            stats.Gold -= gameBuilding.Maintainance;
        }
        return stats;
    }

    public int TurnsToBuilding(String buildingName)
    {
        int workDone = 0;
        if (InProgressBuildings.containsKey(buildingName)) workDone = InProgressBuildings.get(buildingName);
        float workLeft = GetGameBuilding(buildingName).Cost - workDone; // needs to be float so that we get the cieling properly ;)

        FullStats cityStats = GetCity().getCityStats();
        int production = cityStats.Production;
        if (buildingName.equals(Settler)) production += cityStats.Food;

        return (int) Math.ceil(workLeft / production);
    }
}