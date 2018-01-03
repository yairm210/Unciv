package com.unciv.game.pickerscreens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.civinfo.CityInfo;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.game.UnCivGame;
import com.unciv.game.utils.ImageGetter;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Policy;
import com.unciv.models.gamebasics.PolicyBranch;
import com.unciv.models.gamebasics.StringUtils;

public class PolicyPickerScreen extends PickerScreen {

    private Policy pickedPolicy;

    public PolicyPickerScreen() {
        rightSideButton.setText("Adopt policy\r\n(" + ((int) game.civInfo.civStats.culture) + "/" + game.civInfo.getCultureNeededForNextPolicy() + ")");

        if(CivilizationInfo.current().freePolicies>0) {
            rightSideButton.setText("Adopt free policy");
            closeButton.setColor(Color.GRAY);
            closeButton.setTouchable(Touchable.disabled);
        }

        rightSideButton.setColor(Color.GRAY);
        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(game.civInfo.freePolicies>0) game.civInfo.freePolicies--;
                else game.civInfo.civStats.culture -= game.civInfo.getCultureNeededForNextPolicy();
                game.civInfo.policies.add(pickedPolicy.name);

                PolicyBranch branch = GameBasics.PolicyBranches.get(pickedPolicy.branch);
                int policiesCompleteInBranch = branch.policies.count(new Predicate<Policy>() {
                    @Override
                    public boolean evaluate(Policy arg0) {
                        return game.civInfo.policies.contains(arg0.name);
                    }
                });

                if (policiesCompleteInBranch == branch.policies.size() - 1) { // All done apart from branch completion
                    game.civInfo.policies.add(branch.policies.get(branch.policies.size() - 1).name); // add branch completion!
                }
                if (pickedPolicy.name.equals("Collective Rule"))
                    CivilizationInfo.current().tileMap.
                            placeUnitNearTile(CivilizationInfo.current().getCapital().cityLocation, "Settler");
                if (pickedPolicy.name.equals("Citizenship"))
                    CivilizationInfo.current().tileMap.
                            placeUnitNearTile(CivilizationInfo.current().getCapital().cityLocation, "Worker");
                if (pickedPolicy.name.equals("Representation") || pickedPolicy.name.equals("Reformation"))
                    CivilizationInfo.current().enterGoldenAge();

                if (pickedPolicy.name.equals("Scientific Revolution"))
                    CivilizationInfo.current().tech.freeTechs+=2;

                if (pickedPolicy.name.equals("Legalism"))
                    for (CityInfo city : game.civInfo.cities.subList(0,4))
                        city.cityConstructions.addCultureBuilding();

                if (pickedPolicy.name.equals("Free Religion"))
                    CivilizationInfo.current().freePolicies++;


                game.setScreen(new PolicyPickerScreen());
            }
        });


        topTable.row().pad(30);

        for (PolicyBranch branch : GameBasics.PolicyBranches.values()) {
            if (branch.name.equals("Commerce")) topTable.row();
            Table branchGroup = new Table();
            branchGroup.row().pad(20);
            branchGroup.add(getPolicyButton(branch, false)).row();

            int currentRow = 1;
            int currentColumn = 1;
            Table branchTable = new Table();
            for (Policy policy : branch.policies) {
                if (policy.name.endsWith("Complete")) continue;
                if (policy.row > currentRow) {
                    branchTable.row();
                    currentRow++;
                    currentColumn = 1;
                }
                if (policy.column > currentColumn) {
                    branchTable.add().colspan(policy.column - currentColumn); // empty space
                }
                branchTable.add(getPolicyButton(policy, true)).colspan(2);
                currentColumn = policy.column + 2;
            }
            branchTable.pack();
            branchGroup.add(branchTable).height(150).row();

            branchGroup.add(getPolicyButton(branch.policies.get(branch.policies.size() - 1), false)); // finisher

            topTable.add(branchGroup);
        }
        topTable.pack();
    }

    public void pickPolicy(Policy policy) {
        if (game.civInfo.policies.contains(policy.name) || !game.civInfo.policies.containsAll(policy.requires)
                || !game.civInfo.canAdoptPolicy()) {
            rightSideButton.setTouchable(Touchable.disabled);
            rightSideButton.setColor(Color.GRAY);
        } else {
            rightSideButton.setColor(Color.WHITE);
            rightSideButton.setTouchable(Touchable.enabled);
        }
        pickedPolicy = policy;
        String policyText = policy.name + "\r\n" + policy.description + "\r\n";
        if (!policy.name.endsWith("Complete") && policy.requires.size() > 0)
            policyText += "Requires " + StringUtils.join(", ", policy.requires);
        descriptionLabel.setText(policyText);
    }

    public Button getPolicyButton(final Policy policy, boolean image) {
        Button toReturn = new Button(skin);
        if (image) {
            Image policyImage = ImageGetter.getImage("PolicyIcons/" + policy.name.replace(" ", "_") + "_(Civ5).png");
            toReturn.add(policyImage).size(30);
        } else toReturn = new TextButton(policy.name, skin);

        if (game.civInfo.policies.contains(policy.name)) { // existing
            toReturn.setColor(Color.GREEN);
        } else if (!game.civInfo.policies.containsAll(policy.requires)) // non-available
        {
            toReturn.setColor(Color.GRAY);
        }
        toReturn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                pickPolicy(policy);
            }
        });
        toReturn.pack();
        return toReturn;
    }

}