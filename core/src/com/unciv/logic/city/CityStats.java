package com.unciv.logic.city;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.PolicyManager;
import com.unciv.logic.map.RoadStatus;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.gamebasics.Building;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.stats.FullStats;

/**
 * Created by LENOVO on 1/13/2018.
 */

public class CityStats{

    public FullStats currentCityStats;  // This is so we won't have to calculate this multiple times - takes a lot of time, especially on phones!

    public transient CityInfo cityInfo;


    private FullStats getStatsFromTiles(){
        FullStats stats = new FullStats();
        for (TileInfo cell : cityInfo.getTilesInRange())
            if (cityInfo.name.equals(cell.workingCity))
                stats.add(cell.getTileStats(cityInfo));
        return stats;
    }

    private FullStats getStatsFromSpecialists(){
        FullStats stats = new FullStats();

        // Specialists
        FullStats specialists = cityInfo.population.getSpecialists();
        stats.culture+=specialists.culture*3;
        stats.production+=specialists.production*2;
        stats.science+=specialists.science*3;
        stats.gold+=specialists.gold*2;
        int numOfSpecialists = cityInfo.population.getNumberOfSpecialists();
        if(CivilizationInfo.current().policies.isAdopted("Commerce Complete")) stats.gold+=numOfSpecialists;
        if(CivilizationInfo.current().policies.isAdopted("Secularism")) stats.science+=numOfSpecialists*2;

        return stats;
    }

    private FullStats getStatsFromTradeRoute(){
        FullStats stats = new FullStats();
        if(!cityInfo.isCapital() && cityInfo.isConnectedToCapital(RoadStatus.Road)) {
            CivilizationInfo civInfo = CivilizationInfo.current();
            // Calculated by http://civilization.wikia.com/wiki/Trade_route_(Civ5)
            double goldFromTradeRoute = civInfo.getCapital().population.population * 0.15
                    + cityInfo.population.population * 1.1 - 1;
            if(civInfo.policies.isAdopted("Trade Unions")) goldFromTradeRoute+=2;
            if(civInfo.getBuildingUniques().contains("TradeRouteGoldIncrease")) goldFromTradeRoute*=1.25; // Machu Pichu speciality
            stats.gold += goldFromTradeRoute;
        }
        return stats;
    }

    private FullStats getStatsFromPolicies(){
        FullStats stats = new FullStats();
        PolicyManager policies =  CivilizationInfo.current().policies;

        if(policies.isAdopted("Tradition") && cityInfo.isCapital())
            stats.culture+=3;
        if(policies.isAdopted("Landed Elite") && cityInfo.isCapital())
            stats.food+=2;
        if(policies.isAdopted("Tradition Complete"))
            stats.food+=2;
        if(policies.isAdopted("Monarchy") && cityInfo.isCapital())
            stats.gold+=cityInfo.population.population/2;
        if(policies.isAdopted("Liberty"))
            stats.culture+=1;
        if(policies.isAdopted("Republic"))
            stats.production+=1;
        if(policies.isAdopted("Universal Suffrage"))
            stats.production+=cityInfo.population.population/5;
        if(policies.isAdopted("Free Speech"))
            stats.culture+=cityInfo.population.population/2;

        return stats;
    }


    private FullStats getStatsFromProduction(){
        FullStats stats = new FullStats();

        if("Gold".equals(cityInfo.cityConstructions.currentConstruction)) stats.gold+=stats.production/4;
        if("Science".equals(cityInfo.cityConstructions.currentConstruction)) {
            float scienceProduced=stats.production/4;
            if (CivilizationInfo.current().getBuildingUniques().contains("ScienceConversionIncrease"))
                scienceProduced*=1.33;
            if(CivilizationInfo.current().policies.isAdopted("Rationalism")) scienceProduced*=1.33;
            stats.science += scienceProduced;
        }
        return stats;
    }


    private FullStats getStatPercentBonusesFromRailroad(){
        FullStats stats = new FullStats();
        if( CivilizationInfo.current().tech.isResearched ("Combustion") &&
                (cityInfo.isCapital() || cityInfo.isConnectedToCapital(RoadStatus.Railroad)))
            stats.production += 25;
        return stats;
    }

    private FullStats getStatPercentBonusesFromGoldenAge(){
        FullStats stats = new FullStats();
        if(CivilizationInfo.current().goldenAges.isGoldenAge())
            stats.production+=20;
        return stats;
    }

    private FullStats getStatPercentBonusesFromPolicies(){
        FullStats stats = new FullStats();
        PolicyManager policies =  CivilizationInfo.current().policies;

        CityConstructions cityConstructions = cityInfo.cityConstructions;
        if(policies.isAdopted("Collective Rule") && cityInfo.isCapital()
                && "Settler".equals(cityConstructions.currentConstruction))
            stats.production+=50;
        if(policies.isAdopted("Republic") && cityConstructions.getCurrentConstruction() instanceof Building)
            stats.production+=5;
        if(policies.isAdopted("Reformation") && cityConstructions.builtBuildings.any(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return GameBasics.Buildings.get(arg0).isWonder;
            }
        }))
            stats.culture+=33;
        if(policies.isAdopted("Commerce") && cityInfo.isCapital())
            stats.gold+=25;
        if(policies.isAdopted("Sovereignty") && CivilizationInfo.current().getHappinessForNextTurn() >= 0)
            stats.science+=15;
        if(policies.isAdopted("Aristocracy")
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
                && CivilizationInfo.current().getCivResources().containsKey(GameBasics.TileResources.get("Marble")))
            stats.production += 15;

        return stats;
    }

    private FullStats getStatPercentBonusesFromComputers() {
        FullStats stats = new FullStats();

        if (CivilizationInfo.current().tech.isResearched("Computers")) {
            stats.production += 10;
            stats.science += 10;
        }

        return stats;
    }

    private float getGrowthBonusFromPolicies(){
        float bonus = 0;
        if(CivilizationInfo.current().policies.isAdopted("Landed Elite")  && cityInfo.isCapital())
            bonus+=0.1;
        if(CivilizationInfo.current().policies.isAdopted("Tradition Complete"))
            bonus+=0.15;
        return bonus;
    }


    public void update() {
        CivilizationInfo civInfo = CivilizationInfo.current();

        FullStats stats = new FullStats();
        stats.science += cityInfo.population.population;
        stats.add(getStatsFromTiles());
        stats.add(getStatsFromSpecialists());
        stats.production += cityInfo.population.getFreePopulation();
        stats.add(getStatsFromTradeRoute());
        stats.add(cityInfo.cityConstructions.getStats());
        stats.add(getStatsFromPolicies());

        FullStats statPercentBonuses = cityInfo.cityConstructions.getStatPercentBonuses();
        statPercentBonuses.add(getStatPercentBonusesFromGoldenAge());
        statPercentBonuses.add(getStatPercentBonusesFromPolicies());
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
        if(CivilizationInfo.current().policies.isAdopted("Civil Society"))
            stats.food+=cityInfo.population.getNumberOfSpecialists();

        if(isUnhappy) stats.food /= 4; // Reduce excess food to 1/4 per the same
        stats.food *= (1+getGrowthBonusFromPolicies());

        stats.gold-= cityInfo.cityConstructions.getMaintainanceCosts(); // this is AFTER the bonus calculation!
        this.currentCityStats = stats;
    }

    public float getCityHappiness(){ // needs to be a separate function because we need to know the global happiness state
        CivilizationInfo civInfo = CivilizationInfo.current();
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
        if(civInfo.policies.isAdopted("Monarchy") && cityInfo.isCapital())
            happiness+=cityInfo.population.population/2;
        if(civInfo.policies.isAdopted("Meritocracy") && cityInfo.isConnectedToCapital(RoadStatus.Road))
            happiness+=1;

        happiness+=(int) cityInfo.cityConstructions.getStats().happiness;

        return happiness;
    }

}
