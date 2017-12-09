package com.unciv.game.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.civinfo.CivilizationTech;
import com.unciv.game.UnCivGame;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.Technology;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

public class TechPickerScreen extends PickerScreen {

    HashMap<String, TextButton> techNameToButton = new HashMap<String, TextButton>();
    boolean isFreeTechPick;
    Technology selectedTech;
    CivilizationTech civTech = game.civInfo.tech;
    ArrayList<String> techsToResearch = new ArrayList<String>(civTech.techsToResearch);

    public TechPickerScreen(final UnCivGame game, boolean freeTechPick){
        this(game);
        isFreeTechPick=true;
    }

    public TechPickerScreen(final UnCivGame game) {
        super(game);

        Technology[][] techMatrix = new Technology[10][10]; // Divided into columns, then rows
        for (int i = 0; i < techMatrix.length; i++) {
            techMatrix[i] = new Technology[10];
        }

        for (Technology technology : GameBasics.Technologies.linqValues()) {
            techMatrix[technology.column.columnNumber-1][technology.row - 1] = technology;
        }

        for (int i = 0; i < 10; i++) {
            topTable.row().pad(5);

            for (int j = 0; j < 10; j++) {
                final Technology tech = techMatrix[j][i];
                if (tech == null) topTable.add(); // empty cell
                else {
                    final TextButton TB = new TextButton("", skin);
                    techNameToButton.put(tech.name, TB);
                    TB.addListener(new ClickListener() {
                        @Override
                        public void clicked(InputEvent event, float x, float y) {
                            selectTechnology(tech);
                        }
                    });
                    topTable.add(TB);
                }
            }
            SetButtonsInfo();
        }

        rightSideButton.setText("Pick a tech");
        rightSideButton.setTouchable(Touchable.disabled);
        rightSideButton.setColor(Color.GRAY);
        rightSideButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (isFreeTechPick) {
                    civTech.techsResearched.add(selectedTech.name);
                    civTech.freeTechs-=1;
                    game.civInfo.notifications.add("We have stumbled upon the discovery of "+selectedTech.name+"!");
                    if(selectedTech.name.equals(civTech.currentTechnology()))
                        civTech.techsToResearch.remove(selectedTech.name);
                }
                else civTech.techsToResearch = techsToResearch;
                game.setWorldScreen();
                dispose();
            }
        });
    }

    public void SetButtonsInfo() {
        for (String techName : techNameToButton.keySet()) {
            TextButton TB = techNameToButton.get(techName);
            TB.getStyle().checkedFontColor = Color.BLACK;
            if (civTech.isResearched(techName)) TB.setColor(Color.GREEN);
            else if (techsToResearch.contains(techName)) TB.setColor(Color.BLUE);
            else if (civTech.canBeResearched(techName)) TB.setColor(Color.WHITE);
            else TB.setColor(Color.GRAY);

            TB.setChecked(false);
            TB.setText(techName);

            if (selectedTech != null) {
                Technology thisTech = GameBasics.Technologies.get(techName);
                if (techName.equals(selectedTech.name)) {
                    TB.setChecked(true);
                    TB.setColor(TB.getColor().lerp(Color.LIGHT_GRAY, 0.5f));
                }

                if (thisTech.prerequisites.contains(selectedTech.name)) TB.setText("*" + techName);
                else if (selectedTech.prerequisites.contains(techName)) TB.setText(techName + "*");
            }
            if (techsToResearch.contains(techName)) {
                TB.setText(TB.getText() + " (" + techsToResearch.indexOf(techName) + ")");
            }

            if(!civTech.isResearched(techName)) TB.setText(TB.getText() + "\r\n" + game.civInfo.turnsToTech(techName) + " turns");
        }
    }

    public void selectTechnology(Technology tech) {
        selectedTech = tech;
        descriptionLabel.setText(tech.description);
        if(isFreeTechPick) {selectTechnologyForFreeTech(tech); return;}

        if (civTech.isResearched(tech.name)) {
            rightSideButton.setText("Research");
            rightSideButton.setTouchable(Touchable.disabled);
            rightSideButton.setColor(Color.GRAY);
            SetButtonsInfo();
            return;
        }

        rightSideButton.setTouchable(Touchable.enabled);
        rightSideButton.setColor(Color.WHITE);

        if (civTech.canBeResearched(tech.name)) {
            techsToResearch.clear();
            techsToResearch.add(tech.name);
        } else {
            Stack<String> Prerequisites = new Stack<String>();
            ArrayDeque<String> CheckPrerequisites = new ArrayDeque<String>();
            CheckPrerequisites.add(tech.name);
            while (!CheckPrerequisites.isEmpty()) {
                String techNameToCheck = CheckPrerequisites.pop();
                if (civTech.isResearched(techNameToCheck))
                    continue; //no need to add or check prerequisites
                Technology techToCheck = GameBasics.Technologies.get(techNameToCheck);
                for (String str : techToCheck.prerequisites)
                    if (!CheckPrerequisites.contains(str)) CheckPrerequisites.add(str);
                Prerequisites.add(techNameToCheck);
            }
            techsToResearch.clear();
            while (!Prerequisites.isEmpty()) techsToResearch.add(Prerequisites.pop());
        }

        rightSideButton.setText("Research \r\n" + techsToResearch.get(0));
        SetButtonsInfo();
    }

    private void selectTechnologyForFreeTech(Technology tech){
        if (civTech.canBeResearched(tech.name)) {
            rightSideButton.setText("Pick " + selectedTech.name+"\r\n as free tech!");
            rightSideButton.setTouchable(Touchable.enabled);
            rightSideButton.setColor(Color.WHITE);
        }
        else {
            rightSideButton.setText("Pick a free tech");
            rightSideButton.setTouchable(Touchable.disabled);
            rightSideButton.setColor(Color.GRAY);
        }
    }

}