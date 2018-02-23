package com.unciv.logic.city;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.linq.Linq;
import com.unciv.models.stats.FullStats;

/**
 * Created by LENOVO on 1/13/2018.
 */

public class CityStats{

    public transient FullStats currentCityStats;  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones
    public transient CityInfo cityInfo;

    private FullStats getStatsFromTiles(){
        FullStats stats = new FullStats();
        for (TileInfo cell : cityInfo.getTilesInRange())
            if (cityInfo.name.equals(cell.workingCity))
                stats.add(cell.getTileStats(cityInfo,cityInfo.civInfo));
        return stats;
    }

    private FullStats getStatsFromSpecialists(FullStats specialists, Linq<String> policies){
        FullStats stats = new FullStats();

        // Specialists
        stats.culture+=specialists.culture*3;
        stats.production+=specialists.production*2;
        stats.science+=specialists.science*3;
        stats.gold+=specialists.gold*2;
        int numOfSpecialists = cityInfo.population.getNumberOfSpecialists();
        if(policies.contains("Commerce Complete")) stats.gold+=numOfSpecialists;
        if(policies.contains(("Secularism"))) stats.science+=numOfSpecialists*2;

        return stats;
    }

    private FullStats getStatsFromTradeRoute(){
        FullStats stats = new FullStats();
        if(!isCapital() && isConnectedToCapital(RoadStatus.Road)) {
            CivilizationInfo civInfo = cityInfo.civInfo;
            // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            double goldFromTradeRoute = civInfo.getCapital().population.population * 0.15
                    + cityInfo.population.population * 1.1 - 1;
            if(civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute+=2;
            if(civInfo.getBuildingUniques().contains("TradeRouteGoldIncrease")) goldFromTradeRoute*=1.25; // Machu Pichu speciality
            stats.gold += goldFromTradeRoute;
        }
        return stats;
    }

    private FullStats getStatsFromPolicies(Linq<String> adoptedPolicies){
        FullStats stats = new FullStats();
        if(adoptedPolicies.contains("Tradition") && isCapital())
            stats.culture+=3;
        if(adoptedPolicies.contains("Landed Elite") && isCapital())
            stats.food+=2;
        if(adoptedPolicies.contains("Tradition Complete"))
            stats.food+=2;
        if(adoptedPolicies.contains("Monarchy") && isCapital())
            stats.gold+=cityInfo.population.population/2;
        if(adoptedPolicies.contains("Liberty"))
            stats.culture+=1;
        if(adoptedPolicies.contains("Republic"))
            stats.production+=1;
        if(adoptedPolicies.contains("Universal Suffrage"))
            stats.production+=cityInfo.population.population/5;
        if(adoptedPolicies.contains("Free Speech"))
            stats.culture+=cityInfo.population.population/2;

        return stats;
    }


    private FullStats getStatsFromProduction(){
        FullStats stats = new FullStats();

        if("Gold".equals(cityInfo.cityConstructions.currentConstruction)) stats.gold+=stats.production/4;
        if("Science".equals(cityInfo.cityConstructions.currentConstruction)) {
            float scienceProduced=stats.production/4;
            if (cityInfo.civInfo.getBuildingUniques().contains("ScienceConversionIncrease"))
                scienceProduced*=1.33;
            if(cityInfo.civInfo.policies.isAdopted("Rationalism")) scienceProduced*=1.33;
            stats.science += scienceProduced;
        }
        return stats;
    }


    private FullStats getStatPercentBonusesFromRailroad(){
        FullStats stats = new FullStats();
        if( cityInfo.civInfo.tech.isResearched ("Combustion") &&
                (isCapital() || isConnectedToCapital(RoadStatus.Railroad)))
            stats.production += 25;
        return stats;
    }

    private FullStats getStatPercentBonusesFromGoldenAge(boolean isGoldenAge){
        FullStats stats = new FullStats();
        if(isGoldenAge)
            stats.production+=20;
        return stats;
    }

    private FullStats getStatPercentBonusesFromPolicies(Linq<String> policies, CityConstructions cityConstructions){
        FullStats stats = new FullStats();

        if(policies.contains("Collective Rule") && isCapital()
                && "Settler".equals(cityConstructions.currentConstruction))
            stats.production+=50;
        if(policies.contains("Republic") && cityConstructions.getCurrentConstruction() instanceof Building)
            stats.production+=5;
        if(policies.contains("Reformation") && cityConstructions.builtBuildings.any(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return GameBasics.Buildings.get(arg0).isWonder;
            }
        }))
            stats.culture+=33;
        if(policies.contains("Commerce") && isCapital())
            stats.gold+=25;
        if(policies.contains("Sovereignty") && cityInfo.civInfo.getHappinessForNextTurn() >= 0)
            stats.science+=15;
        if(policies.contains("Aristocracy")
                && cityConstructions.getCurrentConstruction() instanceof Building
                && ((Building)cityConstructions.getCurrentConstruction()).isWonder)
            stats.production+=15;

        return stats;
    }

    private FullStats getStatPercentBonusesFromMarble() {
        FullStats stats = new FullStats();
        IConstruction construction = cityInfo.cityConstructions.getCurrentConstruction();

        if (construction instanceof Building
                && ((Building) construction).isWonder
                && cityInfo.civInfo.getCivResources().containsKey(GameBasics.TileResources.get("Marble")))
            stats.production += 15;

        return stats;
    }

    private FullStats getStatPercentBonusesFromComputers() {
        FullStats stats = new FullStats();

        if (cityInfo.civInfo.tech.isResearched("Computers")) {
            stats.production += 10;
            stats.science += 10;
        }

        return stats;
    }

    private float getGrowthBonusFromPolicies(){
        float bonus = 0;
        if(cityInfo.civInfo.policies.isAdopted("Landed Elite")  && isCapital())
            bonus+=0.1;
        if(cityInfo.civInfo.policies.isAdopted("Tradition Complete"))
            bonus+=0.15;
        return bonus;
    }


    public void update() {
        CivilizationInfo civInfo = cityInfo.civInfo;

        FullStats stats = new FullStats();
        stats.science += cityInfo.population.population;
        stats.production += cityInfo.population.getFreePopulation();

        stats.add(getStatsFromTiles());
        stats.add(getStatsFromSpecialists(cityInfo.population.getSpecialists(), civInfo.policies.getAdoptedPolicies()));
        stats.add(getStatsFromTradeRoute());
        stats.add(cityInfo.cityConstructions.getStats());
        stats.add(getStatsFromPolicies(civInfo.policies.getAdoptedPolicies()));

        FullStats statPercentBonuses = cityInfo.cityConstructions.getStatPercentBonuses();
        statPercentBonuses.add(getStatPercentBonusesFromGoldenAge(cityInfo.civInfo.goldenAges.isGoldenAge()));
        statPercentBonuses.add(getStatPercentBonusesFromPolicies(civInfo.policies.getAdoptedPolicies(), cityInfo.cityConstructions));
        statPercentBonuses.add(getStatPercentBonusesFromRailroad());
        statPercentBonuses.add(getStatPercentBonusesFromMarble());
        statPercentBonuses.add(getStatPercentBonusesFromComputers());

        stats.production*=1+statPercentBonuses.production/100;  // So they get bonuses for production and gold/science

        stats.add(getStatsFromProduction());


        stats.gold*=1+statPercentBonuses.gold/100;
        stats.science*=1+statPercentBonuses.science/100;
        stats.culture*=1+statPercentBonuses.culture/100;

        boolean isUnhappy = civInfo.getHappinessForNextTurn() < 0;
        if (!isUnhappy) stats.food*=1+statPercentBonuses.food/100; // Regular food bonus revoked when unhappy per https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
        stats.food -= cityInfo.population.population * 2; // Food reduced after the bonus
        if(civInfo.policies.isAdopted("Civil Society"))
            stats.food+=cityInfo.population.getNumberOfSpecialists();

        if(isUnhappy) stats.food /= 4; // Reduce excess food to 1/4 per the same
        stats.food *= (1+getGrowthBonusFromPolicies());

        stats.gold-= cityInfo.cityConstructions.getMaintainanceCosts(); // this is AFTER the bonus calculation!
        this.currentCityStats = stats;
    }

    public float getCityHappiness(){ // needs to be a separate function because we need to know the global happiness state
        CivilizationInfo civInfo = cityInfo.civInfo;
        // in order to determine how much food is produced in a city!
        float happiness = -3; // -3 happiness per city
        float unhappinessFromCitizens = cityInfo.population.population;
        if(civInfo.policies.isAdopted("Democracy"))
            unhappinessFromCitizens -= cityInfo.population.getNumberOfSpecialists()*0.5f;
        if(civInfo.getBuildingUniques().contains("CitizenUnhappinessDecreased"))
            unhappinessFromCitizens*=0.9;
        if(civInfo.policies.isAdopted("Aristocracy"))
            unhappinessFromCitizens*=0.95;
        happiness-=unhappinessFromCitizens;

        if(civInfo.policies.isAdopted("Aristocracy"))
            happiness+=cityInfo.population.population/10;
        if(civInfo.policies.isAdopted("Monarchy") && isCapital())
            happiness+=cityInfo.population.population/2;
        if(civInfo.policies.isAdopted("Meritocracy") && isConnectedToCapital(RoadStatus.Road))
            happiness+=1;

        happiness+=(int) cityInfo.cityConstructions.getStats().happiness;

        return happiness;
    }


    boolean isConnectedToCapital(RoadStatus roadType){
        if(cityInfo.civInfo.getCapital()==null) return false;// first city!
        TileInfo capitalTile = cityInfo.civInfo.getCapital().getTile();
        Linq<TileInfo> tilesReached = new Linq<TileInfo>();
        Linq<TileInfo> tilesToCheck = new Linq<TileInfo>();
        tilesToCheck.add(cityInfo.getTile());
        while(!tilesToCheck.isEmpty()){
            Linq<TileInfo> newTiles = new Linq<TileInfo>();
            for(TileInfo tile : tilesToCheck)
                for (TileInfo maybeNewTile : cityInfo.getTileMap().getTilesInDistance(tile.position,1))
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

    private boolean isCapital(){ return cityInfo.civInfo.getCapital() == cityInfo; }

}
