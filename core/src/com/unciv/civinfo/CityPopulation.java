package com.unciv.civinfo;

public class CityPopulation
{
    public int Population = 1;
    public int FoodStored = 0;
    public int FoodToNextPopulation()
    {
        // civ v math,civilization.wikia
            return 15 + 6 * (Population - 1) + (int)Math.floor(Math.pow(Population - 1, 1.8f));

    }

    /**
     * @param FoodProduced
     * @return whether a growth occured
     */
    public boolean NextTurn(int FoodProduced)
    {
        FoodStored += FoodProduced;
        if (FoodStored < 0) // starvation!
        {
            Population--;
            FoodStored = 0;
        }
        if (FoodStored >= FoodToNextPopulation()) // growth!
        {
            FoodStored -= FoodToNextPopulation();
            Population++;
            return true;
        }
        return false;
    }
}
