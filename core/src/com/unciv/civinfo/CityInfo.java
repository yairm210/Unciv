package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.HexMath;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;
import java.util.Comparator;

public class CityInfo {
    public final Vector2 cityLocation;
    public String Name;

    public CityBuildings cityBuildings;
    public CityPopulation cityPopulation;
    public int cultureStored;
    private int tilesClaimed;


    public LinqCollection<Vector2> CityTileLocations = new LinqCollection<Vector2>();

    public LinqCollection<TileInfo> getCityTiles(){
        return CityTileLocations.select(new com.unciv.models.LinqCollection.Func<Vector2, TileInfo>() {
            @Override
            public TileInfo GetBy(Vector2 arg0) {
                return UnCivGame.Current.civInfo.tileMap.get(arg0);
            }
        });
    }

    private String[] CityNames = new String[]{"Assur", "Ninveh", "Nimrud", "Kar-Tukuli-Ninurta", "Dur-Sharrukin"};

    public CityInfo(){
        cityLocation = Vector2.Zero;
    }  // for json parsing, we need to have a default constructor

    public int getCultureToNextTile(){
        // This one has conflicting sources -
        // http://civilization.wikia.com/wiki/Mathematics_of_Civilization_V says it's 20+(10(t-1))^1.1
        // https://www.reddit.com/r/civ/comments/58rxkk/how_in_gods_name_do_borders_expand_in_civ_vi/ has it
        //   (per game XML files) at 6*(t+0.4813)^1.3
        // The second seems to be more based, so I'll go with that
        double a = 6*Math.pow(tilesClaimed+1.4813,1.3);
        return (int)Math.round(a);
    }

    CityInfo(CivilizationInfo civInfo, Vector2 cityLocation) {
        Name = CityNames[civInfo.Cities.size()];
        this.cityLocation = cityLocation;
        cityBuildings = new CityBuildings(this);
        cityPopulation = new CityPopulation();

        for(Vector2 vector : HexMath.GetVectorsInDistance(cityLocation,2))
        {
            if(civInfo.tileMap.get(vector).GetCity() == null)
                CityTileLocations.add(vector);
        }

        autoAssignWorker();
        civInfo.Cities.add(this);
    }

    ArrayList<String> getLuxuryResources() {
        ArrayList<String> LuxuryResources = new ArrayList<String>();
        for (TileInfo tileInfo : getCityTiles()) {
            com.unciv.models.gamebasics.TileResource resource = tileInfo.GetTileResource();
            if (resource != null && resource.ResourceType == ResourceType.Luxury && resource.Improvement.equals(tileInfo.Improvement))
                LuxuryResources.add(tileInfo.Resource);
        }
        return LuxuryResources;
    }


    private int getWorkingPopulation() {
        return getCityTiles().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.IsWorked;
            }
        });
    }

    public int getFreePopulation() {
        return cityPopulation.Population - getWorkingPopulation();
    }

    public boolean hasNonWorkingPopulation() {
        return getFreePopulation() > 0;
    }

    public FullStats getCityStats() {
        FullStats stats = new FullStats() {{
            Happiness = -3 - cityPopulation.Population; // -3 happiness per city and -3 per population
        }};

        stats.Science += cityPopulation.Population;

        // Working ppl
        for (TileInfo cell : getCityTiles()) {
            if (cell.IsWorked || cell.IsCityCenter()) stats.add(cell.GetTileStats());
        }
        //idle ppl
        stats.Production += getFreePopulation();
        stats.Food -= cityPopulation.Population * 2;

        stats.add(cityBuildings.GetStats());

        return stats;
    }

    void nextTurn() {
        FullStats stats = getCityStats();

        if (cityBuildings.CurrentBuilding.equals(CityBuildings.Settler) && stats.Food > 0) {
            stats.Production += stats.Food;
            stats.Food = 0;
        }

        if (cityPopulation.NextTurn(stats.Food)) autoAssignWorker();

        cityBuildings.NextTurn(stats.Production);

        for (TileInfo tileInfo : getCityTiles()) {
            tileInfo.NextTurn();
        }

        cultureStored+=stats.Culture;
        if(cultureStored>=getCultureToNextTile()){
            addNewTile();
        }
    }

    private void addNewTile(){
        cultureStored -= getCultureToNextTile();
        tilesClaimed++;
        LinqCollection<Vector2> possibleNewTileVectors = new LinqCollection<Vector2>();
        for (TileInfo tile : getCityTiles())
            for (Vector2 vector : HexMath.GetAdjacentVectors(tile.Position))
                if(!CityTileLocations.contains(vector) && !possibleNewTileVectors.contains(vector))
                    possibleNewTileVectors.add(vector);

        LinqCollection<TileInfo> possibleNewTiles = new LinqCollection<TileInfo>();
        TileMap tileMap = UnCivGame.Current.civInfo.tileMap;
        for (Vector2 vector : possibleNewTileVectors)
            if(tileMap.contains(vector) && tileMap.get(vector).GetCity()==null)
                possibleNewTiles.add(tileMap.get(vector));

        TileInfo TileChosen=null;
        double TileChosenRank=0;
        for(TileInfo tile : possibleNewTiles){
            double rank = rankTile(tile);
            if(rank>TileChosenRank){
                TileChosenRank = rank;
                TileChosen = tile;
            }
        }

        CityTileLocations.add(TileChosen.Position);
    }

    private void autoAssignWorker() {
        double maxValue = 0;
        TileInfo toWork = null;
        for (TileInfo tileInfo : getCityTiles()) {
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

    private double rankTile(TileInfo tile){
        FullStats stats = tile.GetTileStats();
        double rank=0;
        if(stats.Food<2) rank+=stats.Food;
        else rank += 2 + (stats.Food-2)/2; // 1 point for each food up to 2, from there on half a point
        rank+=stats.Gold/2;
        rank+=stats.Production;
        rank+=stats.Science;
        rank+=stats.Culture;
        if(tile.Improvement==null) rank+=0.5; // Improvement potential!
        return rank;
    }
}