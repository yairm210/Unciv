package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.HexMath;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;

public class CityInfo {
    public final Vector2 cityLocation;
    public String Name;

    public CityBuildings cityBuildings;
    public CityPopulation cityPopulation;

    public LinqCollection<Vector2> CityTileLocations = new LinqCollection<Vector2>();

    public LinqCollection<TileInfo> GetCityTiles(){
        return CityTileLocations.select(new com.unciv.models.LinqCollection.Func<Vector2, TileInfo>() {
            @Override
            public TileInfo GetBy(Vector2 arg0) {
                return UnCivGame.Current.civInfo.tileMap.get(arg0);
            }
        });
    }

    String[] CityNames = new String[]{"Assur", "Ninveh", "Nimrud", "Kar-Tukuli-Ninurta", "Dur-Sharrukin"};

    public CityInfo(){
        cityLocation = Vector2.Zero;
    }  // for json parsing, we need to have a default constructor

    public CityInfo(CivilizationInfo civInfo, Vector2 cityLocation) {
        Name = CityNames[civInfo.Cities.size()];
        this.cityLocation = cityLocation;
        cityBuildings = new CityBuildings(this);
        cityPopulation = new CityPopulation();

        for(Vector2 vector : HexMath.GetVectorsInDistance(cityLocation,2))
        {
            if(civInfo.tileMap.get(vector).GetCity() == null)
                CityTileLocations.add(vector);
        }

        AutoAssignWorker();
        civInfo.Cities.add(this);
    }

    public ArrayList<String> GetLuxuryResources() {
        ArrayList<String> LuxuryResources = new ArrayList<String>();
        for (TileInfo tileInfo : GetCityTiles()) {
            com.unciv.models.gamebasics.TileResource resource = tileInfo.GetTileResource();
            if (resource != null && resource.ResourceType.equals("Luxury") && resource.Improvement.equals(tileInfo.Improvement))
                LuxuryResources.add(tileInfo.Resource);
        }
        return LuxuryResources;
    }


    public int GetWorkingPopulation() {
        return GetCityTiles().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.IsWorked;
            }
        });
    }

    public int GetFreePopulation() {
        return cityPopulation.Population - GetWorkingPopulation();
    }

    public boolean HasNonWorkingPopulation() {
        return GetFreePopulation() > 0;
    }

    public FullStats getCityStats() {
        FullStats stats = new FullStats() {{
            Happiness = -3 - cityPopulation.Population; // -3 happiness per city and -3 per population
        }};

        stats.Science += cityPopulation.Population;

        // Working ppl
        for (TileInfo cell : GetCityTiles()) {
            if (cell.IsWorked || cell.IsCityCenter()) stats.add(cell.GetTileStats());
        }
        //idle ppl
        stats.Production += GetFreePopulation();
        stats.Food -= cityPopulation.Population * 2;

        stats.add(cityBuildings.GetStats());

        return stats;
    }

    public void NextTurn() {
        FullStats stats = getCityStats();

        if (cityBuildings.CurrentBuilding.equals(cityBuildings.Settler) && stats.Food > 0) {
            stats.Production += stats.Food;
            stats.Food = 0;
        }

        if (cityPopulation.NextTurn(stats.Food)) AutoAssignWorker();

        cityBuildings.NextTurn(stats.Production);

        for (TileInfo tileInfo : GetCityTiles()) {
            tileInfo.NextTurn();
        }
    }

    public void AutoAssignWorker() {
        double maxValue = 0;
        TileInfo toWork = null;
        for (TileInfo tileInfo : GetCityTiles()) {
            if (tileInfo.IsWorked || tileInfo.IsCityCenter()) continue;
            FullStats stats = tileInfo.GetTileStats();

            double value = stats.Food + stats.Production * 0.5;
            if (value > maxValue) {
                maxValue = value;
                toWork = tileInfo;
            }
        }
        toWork.IsWorked = true;
    }
}