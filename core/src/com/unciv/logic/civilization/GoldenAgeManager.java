package com.unciv.logic.civilization;

public class GoldenAgeManager{
    public transient CivilizationInfo civInfo;

    public int storedHappiness=0;
    public int numberOfGoldenAges=0;
    public int turnsLeftForCurrentGoldenAge=0;


    public boolean isGoldenAge(){return turnsLeftForCurrentGoldenAge>0;}
    public int happinessRequiredForNextGoldenAge(){
        return (int) ((500+numberOfGoldenAges*250)*(1+civInfo.cities.size()/100.0)); //https://forums.civfanatics.com/resources/complete-guide-to-happiness-vanilla.25584/
    }

    public void enterGoldenAge(){
        int turnsToGoldenAge = 10;
        if(civInfo.getBuildingUniques().contains("GoldenAgeLengthIncrease")) turnsToGoldenAge*=1.5;
        if(civInfo.policies.isAdopted("Freedom Complete")) turnsToGoldenAge*=1.5;
        turnsLeftForCurrentGoldenAge += turnsToGoldenAge;
        civInfo.gameInfo.addNotification("You have entered a golden age!",null);
    }

    public void nextTurn(int happiness) {

        if(happiness>0&& !isGoldenAge()) storedHappiness+=happiness;

        if(isGoldenAge()) turnsLeftForCurrentGoldenAge--;
        else if(storedHappiness > happinessRequiredForNextGoldenAge()){
            storedHappiness -= happinessRequiredForNextGoldenAge();
            enterGoldenAge();
            numberOfGoldenAges++;
        }
    }
}
