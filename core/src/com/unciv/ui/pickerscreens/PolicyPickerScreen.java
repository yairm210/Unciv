package com.unciv.ui.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.PolicyManager;
import com.unciv.models.linq.Linq;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Policy;
import com.unciv.models.gamebasics.PolicyBranch;
import com.unciv.models.gamebasics.StringUtils;
import com.unciv.ui.utils.ImageGetter;

public class PolicyPickerScreen extends PickerScreen {

    final CivilizationInfo civInfo;

    private Policy pickedPolicy;

    public PolicyPickerScreen(final CivilizationInfo civInfo) {
        this.civInfo = civInfo;

        final PolicyManager policies = civInfo.policies;
        Linq<String> tutorial = new Linq<String>();
        tutorial.add("Each turn, the culture you gain from all your " +
                "\r\n  cities is added to your Civilization's culture." +
                "\r\nWhen you have enough culture, you may pick a " +
                "\r\n  Social Policy, each one giving you a certain bonus.");
        tutorial.add("The policies are organized into branches, with each" +
                "\r\n  branch providing a bonus ability when all policies " +
                "\r\n  in the branch have been adopted.");
        tutorial.add("With each policy adopted, and with each city built," +
                "\r\n  the cost of adopting another policy rises - so choose wisely!");
        displayTutorials("PolicyPickerScreen",tutorial);

        rightSideButton.setText("Adopt policy\r\n(" + policies.storedCulture + "/" + policies.getCultureNeededForNextPolicy() + ")");

        if(policies.freePolicies>0) {
            rightSideButton.setText("Adopt free policy");
            closeButton.setColor(Color.GRAY);
            closeButton.setTouchable(Touchable.disabled);
        }

        rightSideButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if(policies.freePolicies>0) policies.freePolicies--;
                else policies.storedCulture -= policies.getCultureNeededForNextPolicy();
                civInfo.policies.adopt(pickedPolicy);

                game.setScreen(new PolicyPickerScreen(civInfo));
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
        if (civInfo.policies.isAdopted(policy.name)
                || !civInfo.policies.getAdoptedPolicies().containsAll(policy.requires)
                || !civInfo.policies.canAdoptPolicy()) {
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

        if (civInfo.policies.isAdopted(policy.name)) { // existing
            toReturn.setColor(Color.GREEN);
        } else if (!civInfo.policies.getAdoptedPolicies().containsAll(policy.requires)) // non-available
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