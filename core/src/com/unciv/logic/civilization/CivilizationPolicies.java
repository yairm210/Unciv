package com.unciv.logic.civilization;

import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.city.CityInfo;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Policy;
import com.unciv.models.gamebasics.PolicyBranch;
import com.unciv.models.linq.Linq;
import com.unciv.ui.UnCivGame;
import com.unciv.ui.pickerscreens.GreatPersonPickerScreen;


public class CivilizationPolicies extends Linq<String> {

    public void adopt(Policy policy){

        PolicyBranch branch = GameBasics.PolicyBranches.get(policy.branch);
        int policiesCompleteInBranch = branch.policies.count(new Predicate<Policy>() {
            @Override
            public boolean evaluate(Policy arg0) {
                return contains(arg0.name);
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
            CivilizationInfo.current().enterGoldenAge();

        if (policy.name.equals("Scientific Revolution"))
            CivilizationInfo.current().tech.freeTechs+=2;

        if (policy.name.equals("Legalism"))
            for (CityInfo city : CivilizationInfo.current().cities.subList(0,4))
                city.cityConstructions.addCultureBuilding();

        if (policy.name.equals("Free Religion"))
            CivilizationInfo.current().freePolicies++;

        if (policy.name.equals("Liberty Complete"))
            UnCivGame.Current.setScreen(new GreatPersonPickerScreen());

    }
}
