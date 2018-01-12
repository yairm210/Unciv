package com.unciv.logic.civilization;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.city.CityInfo;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Policy;
import com.unciv.models.gamebasics.PolicyBranch;
import com.unciv.models.linq.Linq;
import com.unciv.ui.UnCivGame;
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen;
import com.unciv.ui.pickerscreens.PolicyPickerScreen;


public class PolicyManager {

    public int freePolicies=0;
    public int storedCulture=0;
    private Linq<String> adoptedPolicies = new Linq<String>();

    public Linq<String> getAdoptedPolicies(){return adoptedPolicies.clone();}
    public boolean isAdopted(String policyName){return adoptedPolicies.contains(policyName);}


    public int getCultureNeededForNextPolicy(){
        // from https://forums.civfanatics.com/threads/the-number-crunching-thread.389702/
        int basicPolicies = adoptedPolicies.count(new Predicate<String>() {
            @Override
            public boolean evaluate(String arg0) {
                return !arg0.endsWith("Complete");
            }
        });
        double baseCost = 25+ Math.pow(basicPolicies*6,1.7);
        double cityModifier = 0.3*(CivilizationInfo.current().cities.size()-1);
        if(isAdopted("Representation")) cityModifier *= 2/3f;
        int cost = (int) Math.round(baseCost*(1+cityModifier));
        if(isAdopted("Piety Complete")) cost*=0.9;
        if(CivilizationInfo.current().getBuildingUniques().contains("PolicyCostReduction")) cost*=0.9;
        return cost-cost%5; // round down to nearest 5
    }

    public boolean canAdoptPolicy(){
        return storedCulture >= getCultureNeededForNextPolicy();
    }

    public void adopt(Policy policy){
        adoptedPolicies.add(policy.name);

        PolicyBranch branch = GameBasics.PolicyBranches.get(policy.branch);
        int policiesCompleteInBranch = branch.policies.count(new Predicate<Policy>() {
            @Override
            public boolean evaluate(Policy arg0) {
                return isAdopted(arg0.name);
            }
        });

        if (policiesCompleteInBranch == branch.policies.size() - 1) { // All done apart from branch completion
            adopt(branch.policies.get(branch.policies.size() - 1)); // add branch completion!
        }
        if (policy.name.equals("Collective Rule"))
            CivilizationInfo.current().tileMap.
                    placeUnitNearTile(CivilizationInfo.current().getCapital().cityLocation, "Settler");
        if (policy.name.equals("Citizenship"))
            CivilizationInfo.current().tileMap.
                    placeUnitNearTile(CivilizationInfo.current().getCapital().cityLocation, "Worker");
        if (policy.name.equals("Representation") || policy.name.equals("Reformation"))
            CivilizationInfo.current().goldenAges.enterGoldenAge();

        if (policy.name.equals("Scientific Revolution"))
            CivilizationInfo.current().tech.freeTechs+=2;

        if (policy.name.equals("Legalism"))
            for (CityInfo city : CivilizationInfo.current().cities.subList(0,4))
                city.cityConstructions.addCultureBuilding();

        if (policy.name.equals("Free Religion"))
            freePolicies++;

        if (policy.name.equals("Liberty Complete"))
            UnCivGame.Current.setScreen(new GreatPersonPickerScreen());

    }

    public void nextTurn(float culture) {
        storedCulture+=culture;
        boolean couldAdoptPolicyBefore = canAdoptPolicy();
        if(!couldAdoptPolicyBefore && canAdoptPolicy())
            UnCivGame.Current.setScreen(new PolicyPickerScreen());
    }
}
