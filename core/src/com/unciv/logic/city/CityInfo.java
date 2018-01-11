package com.unciv.logic.city;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.logic.map.TileMap;
import com.unciv.ui.UnCivGame;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqCounter;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.TileResource;
import com.unciv.models.stats.FullStats;

public class CityInfo {
    public final Vector2 cityLocation;
    public String name;

    public CityConstructions cityConstructions;
    public int cultureStored;
    private int tilesClaimed;
    public int population = 1;
    public int foodStored = 0;
    public LinqHashMap<String,FullStats> buildingsSpecialists = new LinqHashMap<String, FullStats>();

    public FullStats cityStats; // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones!

    private TileMap getTileMap(){return UnCivGame.Current.civInfo.tileMap; }

    public TileInfo getTile(){return getTileMap().get(cityLocation);}
    public Linq<TileInfo> getTilesInRange(){
        return getTileMap().getTilesInDistance(cityLocation,3).where(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return UnCivGame.Current.civInfo.civName.equals(arg0.owner);
            }
        });
    }

    private String[] CityNames = new String[]{
        "New Bark","Cherrygrove","Violet","Azalea","Goldenrod","Ecruteak","Olivine","Cianwood","Mahogany","Blackthorn",
        "Pallet","Viridian","Pewter","Cerulean","Vermillion","Lavender","Celadon","Fuchsia","Saffron","Cinnibar"};

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
        if(CivilizationInfo.current().getBuildingUniques().contains("NewTileCostReduction")) a *= 0.75; //Speciality of Angkor Wat
        if(CivilizationInfo.current().policies.contains("Tradition")) a *= 0.75;
        return (int)Math.round(a);
    }

    public CityInfo(CivilizationInfo civInfo, Vector2 cityLocation) {
        name = CityNames[civInfo.cities.size()];
        this.cityLocation = cityLocation;
        civInfo.cities.add(this);
        CivilizationInfo.current().addNotification(name+" has been founded!",cityLocation);
        cityConstructions = new CityConstructions(this);
        if(civInfo.policies.contains("Legalism") && civInfo.cities.size() <= 4) cityConstructions.addCultureBuilding();
        if(civInfo.cities.size()==1) {
            cityConstructions.builtBuildings.add("Palace");
            cityConstructions.currentConstruction = "Worker"; // Default for first city only!
        }

        for(TileInfo tileInfo : civInfo.tileMap.getTilesInDistance(cityLocation,1)) {
            tileInfo.owner = civInfo.civName;
        }

        TileInfo tile = getTile();
        tile.workingCity = this.name;
        tile.roadStatus = RoadStatus.Railroad;
        if("Forest".equals(tile.terrainFeature) || "Jungle".equals(tile.terrainFeature) || "Marsh".equals(tile.terrainFeature))
            tile.terrainFeature=null;

        autoAssignWorker();
        updateCityStats();
    }

    public LinqCounter<TileResource> getCityResources(){
        LinqCounter<TileResource> cityResources = new LinqCounter<TileResource>();

        for (TileInfo tileInfo : getTilesInRange()) {
            TileResource resource = tileInfo.getTileResource();
            if (resource != null && (resource.improvement.equals(tileInfo.improvement) || tileInfo.isCityCenter()))
                cityResources.add(resource,1);
        }
        // Remove resources required by buildings
        for(Building building : cityConstructions.getBuiltBuildings()){
            if(building.requiredResource!=null){
                TileResource resource = GameBasics.TileResources.get(building.requiredResource);
                cityResources.add(resource,-1);
            }
        }
        return cityResources;
    }


    public FullStats getSpecialists(){
        FullStats allSpecialists = new FullStats();
        for(FullStats stats : buildingsSpecialists.values())
            allSpecialists.add(stats);
        return allSpecialists;
    }

    public int getNumberOfSpecialists(){
        FullStats specialists = getSpecialists();
        return (int) (specialists.science+specialists.production+specialists.culture+specialists.gold);
    }

    public int getFreePopulation() {
        int workingPopulation = getTilesInRange().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return name.equals(arg0.workingCity);
            }
        })-1; // 1 is the city center
        return population - workingPopulation - getNumberOfSpecialists();
    }

    public boolean hasNonWorkingPopulation() {
        return getFreePopulation() > 0;
    }

    public void updateCityStats() {
        CivilizationInfo civInfo = CivilizationInfo.current();
        FullStats stats = new FullStats();
        stats.science += population;

        // Working ppl
        for (TileInfo cell : getTilesInRange())
            if (name.equals(cell.workingCity))
                stats.add(cell.getTileStats(this));

        // Specialists
        FullStats specialists = getSpecialists();
        stats.culture+=specialists.culture*3;
        stats.production+=specialists.production*2;
        stats.science+=specialists.science*3;
        stats.gold+=specialists.gold*2;
        if(civInfo.policies.contains("Commerce Complete")) stats.gold+=getNumberOfSpecialists();
        if(civInfo.policies.contains("Secularism")) stats.science+=getNumberOfSpecialists()*2;

        //idle ppl
        stats.production += getFreePopulation();

        if(!isCapital() && isConnectedToCapital(RoadStatus.Road)) {
            // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            double goldFromTradeRoute = civInfo.getCapital().population * 0.15
                    + population * 1.1 - 1;
            if(civInfo.policies.contains("Trade Unions")) goldFromTradeRoute+=2;
            if(civInfo.getBuildingUniques().contains("TradeRouteGoldIncrease")) goldFromTradeRoute*=1.25; // Machu Pichu speciality
            stats.gold += goldFromTradeRoute;
        }

        stats.add(cityConstructions.getStats());
        if(civInfo.policies.contains("Tradition") && isCapital())
            stats.culture+=3;
        if(civInfo.policies.contains("Landed Elite") && isCapital())
            stats.food+=2;
        if(CivilizationInfo.current().policies.contains("Tradition Complete"))
            stats.food+=2;
        if(CivilizationInfo.current().policies.contains("Monarchy") && isCapital())
            stats.gold+=population/2;
        if(CivilizationInfo.current().policies.contains("Liberty"))
            stats.culture+=1;
        if(CivilizationInfo.current().policies.contains("Republic"))
            stats.production+=1;
        if(CivilizationInfo.current().policies.contains("Universal Suffrage"))
            stats.production+=population/5;
        if(CivilizationInfo.current().policies.contains("Free Speech"))
            stats.culture+=population/2;

        FullStats statPercentBonuses = cityConstructions.getStatPercentBonuses();
        if( civInfo.tech.isResearched ("Combustion") &&
                (isCapital() || isConnectedToCapital(RoadStatus.Railroad))) statPercentBonuses.production += 25;
        if(civInfo.isGoldenAge()) statPercentBonuses.production+=20;
        IConstruction currentConstruction = cityConstructions.getCurrentConstruction();
        if(currentConstruction instanceof Building && ((Building)currentConstruction).isWonder){
            if(civInfo.getCivResources().containsKey(GameBasics.TileResources.get("Marble")))
                statPercentBonuses.production+=15;
            if(civInfo.policies.contains("Aristocracy"))
                statPercentBonuses.production+=15;
        }

        if(civInfo.tech.isResearched("Computers")){
            statPercentBonuses.production+=10;
            statPercentBonuses.science+=10;
        }

        if(civInfo.policies.contains("Collective Rule") && isCapital()
                && "Settler".equals(cityConstructions.currentConstruction))
            statPercentBonuses.production+=50;
        if(civInfo.policies.contains("Republic") && currentConstruction instanceof Building)
            statPercentBonuses.production+=5;
        if(civInfo.policies.contains("Reformation") && cityConstructions.builtBuildings.any(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return GameBasics.Buildings.get(arg0).isWonder;
            }
        }))
            statPercentBonuses.culture+=33;
        if(civInfo.policies.contains("Commerce") && isCapital())
            statPercentBonuses.gold+=25;
        if(civInfo.policies.contains("Sovereignty") && civInfo.getHappinessForNextTurn() >= 0)
            statPercentBonuses.science+=15;

        stats.production*=1+statPercentBonuses.production/100;  // So they get bonuses for production and gold/science
        if("Gold".equals(cityConstructions.currentConstruction)) stats.gold+=stats.production/4;
        if("Science".equals(cityConstructions.currentConstruction)) {
            float scienceProduced=stats.production/4;
            if (civInfo.getBuildingUniques().contains("ScienceConversionIncrease"))
                scienceProduced*=1.33;
            if(civInfo.policies.contains("Rationalism")) scienceProduced*=1.33;
            stats.science += scienceProduced;
        }

        stats.gold*=1+statPercentBonuses.gold/100;
        stats.science*=1+statPercentBonuses.science/100;
        stats.culture*=1+statPercentBonuses.culture/100;

        boolean isUnhappy = civInfo.getHappinessForNextTurn() < 0;
        if (!isUnhappy) stats.food*=1+statPercentBonuses.food/100; // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        stats.food -= population * 2; // Food reduced after the bonus
        if(CivilizationInfo.current().policies.contains("Civil Society"))
            stats.food+=getNumberOfSpecialists();

        if(isUnhappy) stats.food /= 4; // Reduce excess food to 1/4 per the same
        if(civInfo.policies.contains("Landed Elite")  && isCapital())
            stats.food*=1.1;
        if(CivilizationInfo.current().policies.contains("Tradition Complete"))
            stats.food*=1.15;

        stats.gold-= cityConstructions.getMaintainanceCosts(); // this is AFTER the bonus calculation!
        this.cityStats = stats;
    }

    public float getCityHappiness(){ // needs to be a separate function because we need to know the global happiness state
        CivilizationInfo civInfo = CivilizationInfo.current();
        // in order to determine how much food is produced in a city!
        float happiness = -3; // -3 happiness per city
        float unhappinessFromCitizens = population;
        if(civInfo.policies.contains("Democracy")) unhappinessFromCitizens-=getNumberOfSpecialists()*0.5f;
        if(civInfo.getBuildingUniques().contains("CitizenUnhappinessDecreased"))
            unhappinessFromCitizens*=0.9;
        if(civInfo.policies.contains("Aristocracy"))
            unhappinessFromCitizens*=0.95;
        happiness-=unhappinessFromCitizens;

        if(civInfo.policies.contains("Aristocracy"))
            happiness+=population/10;
        if(civInfo.policies.contains("Monarchy") && isCapital())
            happiness+=population/2;
        if(civInfo.policies.contains("Meritocracy") && isConnectedToCapital(RoadStatus.Road))
            happiness+=1;

        happiness+=(int) cityConstructions.getStats().happiness;

        return happiness;
    }

    public void nextTurn() {
        FullStats stats = cityStats;
        if (cityConstructions.currentConstruction.equals(CityConstructions.Settler) && stats.food > 0) {
            stats.production += stats.food;
            stats.food = 0;
        }

        foodStored += stats.food;
        if (foodStored < 0) // starvation!
        {
            population--;
            foodStored = 0;
            CivilizationInfo.current().addNotification(name+" is starving!",cityLocation);
        }
        if (foodStored >= foodToNextPopulation()) // growth!
        {
            foodStored -= foodToNextPopulation();
            if(getBuildingUniques().contains("FoodCarriesOver")) foodStored+=0.4f*foodToNextPopulation(); // Aqueduct special
            population++;
            autoAssignWorker();
            CivilizationInfo.current().addNotification(name+" has grown!",cityLocation);
        }

        cityConstructions.nextTurn(stats);

        cultureStored+=stats.culture;
        if(cultureStored>=getCultureToNextTile()){
            addNewTile();
            CivilizationInfo.current().addNotification(name+" has expanded its borders!",cityLocation);
        }
    }

    private void addNewTile(){
        cultureStored -= getCultureToNextTile();
        tilesClaimed++;
        Linq<Vector2> possibleNewTileVectors = new Linq<Vector2>();

        for (int i = 2; i <4 ; i++) {
            Linq<TileInfo> tiles = getTileMap().getTilesInDistance(cityLocation,i);
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
            double value = rankTile(tileInfo);
            if (value > maxValue) {
                maxValue = value;
                toWork = tileInfo;
            }
        }

        if(toWork!=null) // This is when we've run out of tiles!
            toWork.workingCity = name;
    }

    private double rankTile(TileInfo tile){
        FullStats stats = tile.getTileStats(this);
        double rank=0;
        if(stats.food <2) rank+=stats.food;
        else rank += 2 + (stats.food -2)/2; // 1 point for each food up to 2, from there on half a point
        rank+=stats.gold /2;
        rank+=stats.production;
        rank+=stats.science;
        rank+=stats.culture;
        if(tile.improvement ==null) rank+=0.5; // improvement potential!
        return rank;
    }

    private boolean isCapital(){ return CivilizationInfo.current().getCapital() == this; }

    private boolean isConnectedToCapital(RoadStatus roadType){
        if(CivilizationInfo.current().getCapital()==null) return false;// first city!
        TileInfo capitalTile = CivilizationInfo.current().getCapital().getTile();
        Linq<TileInfo> tilesReached = new Linq<TileInfo>();
        Linq<TileInfo> tilesToCheck = new Linq<TileInfo>();
        tilesToCheck.add(getTile());
        while(!tilesToCheck.isEmpty()){
            Linq<TileInfo> newTiles = new Linq<TileInfo>();
            for(TileInfo tile : tilesToCheck)
                for (TileInfo maybeNewTile : getTileMap().getTilesInDistance(tile.position,1))
                    if(!tilesReached.contains(maybeNewTile) && !tilesToCheck.contains(maybeNewTile) && !newTiles.contains(maybeNewTile)
                            && (roadType != RoadStatus.Road || maybeNewTile.roadStatus != RoadStatus.None)
                            && (roadType!=RoadStatus.Railroad || maybeNewTile.roadStatus == roadType))
                        newTiles.add(maybeNewTile);

            if(newTiles.contains(capitalTile)) return true;
            tilesReached.addAll(tilesToCheck);
            tilesToCheck = newTiles;
        }
        return false;
    }

    public int foodToNextPopulation()
    {
        // civ v math,civilization.wikia
        return 15 + 6 * (population - 1) + (int)Math.floor(Math.pow(population - 1, 1.8f));
    }

    public Linq<String> getBuildingUniques(){
        return cityConstructions.getBuiltBuildings().select(new Linq.Func<Building, String>() {
            @Override
            public String GetBy(Building arg0) {
                return arg0.unique;
            }
        }).where(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return arg0!=null;
            }
        });
    }

    public FullStats getGreatPersonPoints(){
        FullStats greatPersonPoints = getSpecialists().multiply(3);
        CivilizationInfo civInfo = CivilizationInfo.current();

        for(Building building : cityConstructions.getBuiltBuildings())
            if(building.greatPersonPoints!=null)
                greatPersonPoints.add(building.greatPersonPoints);

        float multiplier = 1;
        if(civInfo.getBuildingUniques().contains("GreatPersonGenerationIncrease"))
            greatPersonPoints = greatPersonPoints.multiply(1.33f);
        if(civInfo.policies.contains("Entrepreneurship"))
            greatPersonPoints.gold*=1.25;
        if(civInfo.policies.contains("Freedom"))
            greatPersonPoints = greatPersonPoints.multiply(1.25f);

        return greatPersonPoints;
    }




}