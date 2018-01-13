package com.unciv.logic.city;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.linq.LinqHashMap;
import com.unciv.models.stats.FullStats;

public class PopulationManager {

    transient public CityInfo cityInfo;
    public int population = 1;
    public int foodStored = 0;

    public LinqHashMap<String,FullStats> buildingsSpecialists = new LinqHashMap<String, FullStats>();

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


    public int foodToNextPopulation()
    {
        // civ v math,civilization.wikia
        return 15 + 6 * (population - 1) + (int)Math.floor(Math.pow(population - 1, 1.8f));
    }


    public int getFreePopulation() {
        int workingPopulation = cityInfo.getTilesInRange().count(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return cityInfo.name.equals(arg0.workingCity);
            }
        })-1; // 1 is the city center
        return population - workingPopulation - getNumberOfSpecialists();
    }

    public void nextTurn(float food) {

        foodStored += food;
        if (foodStored < 0) // starvation!
        {
            population--;
            foodStored = 0;
            CivilizationInfo.current().addNotification(cityInfo.name+" is starving!",cityInfo.cityLocation);
        }
        if (foodStored >= foodToNextPopulation()) // growth!
        {
            foodStored -= foodToNextPopulation();
            if(cityInfo.getBuildingUniques().contains("FoodCarriesOver")) foodStored+=0.4f*foodToNextPopulation(); // Aqueduct special
            population++;
            cityInfo.autoAssignWorker();
            CivilizationInfo.current().addNotification(cityInfo.name+" has grown!",cityInfo.cityLocation);
        }
    }
}
