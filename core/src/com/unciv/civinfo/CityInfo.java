package com.unciv.civinfo;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.game.UnCivGame;
import com.unciv.models.LinqCollection;
import com.unciv.models.gamebasics.ResourceType;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

import java.util.ArrayList;

public class CityInfo {
    public final Vector2 cityLocation;
    public String name;

    public CityBuildings cityBuildings;
    public CityPopulation cityPopulation;
    public int cultureStored;
    private int tilesClaimed;

    private TileMap getTileMap(){return UnCivGame.Current.civInfo.tileMap; }

    public LinqCollection<TileInfo> getTilesInRange(){
        return getTileMap().getTilesInDistance(cityLocation,3).where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return UnCivGame.Current.civInfo.civName.equals(arg0.owner);
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
        name = CityNames[civInfo.cities.size()];
        this.cityLocation = cityLocation;
        cityBuildings = new CityBuildings(this);
        cityPopulation = new CityPopulation();

        for(TileInfo tileInfo : civInfo.tileMap.getTilesInDistance(cityLocation,1)) {
            tileInfo.owner = civInfo.civName;
        }
        civInfo.tileMap.get(cityLocation).workingCity = this.name;


        autoAssignWorker();
        civInfo.cities.add(this);
    }

    ArrayList<String> getLuxuryResources() {
        ArrayList<String> LuxuryResources = new ArrayList<String>();
        for (TileInfo tileInfo : getTilesInRange()) {
            TileResource resource = tileInfo.getTileResource();
            if (resource != null && resource.ResourceType == ResourceType.Luxury && resource.Improvement.equals(tileInfo.improvement))
                LuxuryResources.add(tileInfo.resource);
        }
        return LuxuryResources;
    }


    private int getWorkingPopulation() {
        return getTilesInRange().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return name.equals(arg0.workingCity);
            }
        })-1; // 1 is the city center
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
        for (TileInfo cell : getTilesInRange())
            if (name.equals(cell.workingCity) || cell.isCityCenter())
                stats.add(cell.getTileStats());

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

        cultureStored+=stats.Culture;
        if(cultureStored>=getCultureToNextTile()){
            addNewTile();
        }
    }

    private void addNewTile(){
        cultureStored -= getCultureToNextTile();
        tilesClaimed++;
        LinqCollection<Vector2> possibleNewTileVectors = new LinqCollection<Vector2>();

        for (int i = 2; i <4 ; i++) {
            LinqCollection<TileInfo> tiles = getTileMap().getTilesInDistance(cityLocation,i);
            tiles = tiles.where(new Predicate<TileInfo>() {
                @Override
                public boolean evaluate(TileInfo arg0) {
                    return arg0.owner == null;
                }
            });
            if(tiles.size()==0) continue;

            TileInfo TileChosen=null;
            double TileChosenRank=0;
            for(TileInfo tile : tiles){
                double rank = rankTile(tile);
                if(rank>TileChosenRank){
                    TileChosenRank = rank;
                    TileChosen = tile;
                }
            }
            TileChosen.owner = UnCivGame.Current.civInfo.civName;
            return;
        }
    }

    private void autoAssignWorker() {
        double maxValue = 0;
        TileInfo toWork = null;
        for (TileInfo tileInfo : getTilesInRange()) {
            if (tileInfo.workingCity !=null) continue;
            FullStats stats = tileInfo.getTileStats();

            double value = stats.Food + stats.Production * 0.5;
            if (value > maxValue) {
                maxValue = value;
                toWork = tileInfo;
            }
        }
        toWork.workingCity = name;
    }

    private double rankTile(TileInfo tile){
        FullStats stats = tile.getTileStats();
        double rank=0;
        if(stats.Food<2) rank+=stats.Food;
        else rank += 2 + (stats.Food-2)/2; // 1 point for each food up to 2, from there on half a point
        rank+=stats.Gold/2;
        rank+=stats.Production;
        rank+=stats.Science;
        rank+=stats.Culture;
        if(tile.improvement ==null) rank+=0.5; // improvement potential!
        return rank;
    }
}