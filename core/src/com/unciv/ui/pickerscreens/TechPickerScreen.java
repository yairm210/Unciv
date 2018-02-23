package com.unciv.ui.pickerscreens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.logic.civilization.TechManager;
import com.unciv.models.linq.Linq;
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
    final CivilizationInfo civInfo;
    TechManager civTech;
    ArrayList<String> techsToResearch;

    public TechPickerScreen(boolean freeTechPick, CivilizationInfo civInfo){
        this(civInfo);
        isFreeTechPick=freeTechPick;
    }

    public TechPickerScreen(final CivilizationInfo civInfo) {
        this.civInfo = civInfo;
        civTech = civInfo.tech;
        techsToResearch = new ArrayList<String>(civTech.techsToResearch);

        Technology[][] techMatrix = new Technology[17][10]; // Divided into columns, then rows

        for (Technology technology : GameBasics.Technologies.linqValues()) {
            techMatrix[technology.column.columnNumber-1][technology.row - 1] = technology;
        }

        for (int i = 0; i < 10; i++) {
            topTable.row().pad(5);

            for (int j = 0; j < techMatrix.length; j++) {
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
        }

        setButtonsInfo();

        rightSideButton.setText("Pick a tech");
        rightSideButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (isFreeTechPick) {
                    civTech.techsResearched.add(selectedTech.name);
                    civTech.freeTechs-=1;
                    civInfo.gameInfo.addNotification("We have stumbled upon the discovery of "+selectedTech.name+"!",null);
                    if(selectedTech.name.equals(civTech.currentTechnology()))
                        civTech.techsToResearch.remove(selectedTech.name);
                }
                else civTech.techsToResearch = techsToResearch;
                game.setWorldScreen();
                game.worldScreen.update();
                dispose();
            }
        });


        Linq<String> tutorial = new Linq<String>();
        tutorial.add("Technology is central to your civilization," +
                "\r\n as technological progress brings with it" +
                "\r\n more construction options, improvements, and abilities");
        tutorial.add("Most technologies are dependant on" +
                "\r\n other technologies being researched - " +
                "\r\n but you can choose a technology to aspire to," +
                "\r\n and your civilization will research the" +
                "\r\n necessary technologies to get there");
        displayTutorials("TechPickerScreen",tutorial);
    }

    public void setButtonsInfo() {
        for (String techName : techNameToButton.keySet()) {
            TextButton TB = techNameToButton.get(techName);
            //TB.getStyle().checkedFontColor = Color.BLACK;
            if (civTech.isResearched(techName)) TB.setColor(Color.GREEN);
            else if (techsToResearch.contains(techName)) TB.setColor(Color.BLUE.cpy().lerp(Color.WHITE,0.3f));
            else if (civTech.canBeResearched(techName)) TB.setColor(Color.WHITE);
            else TB.setColor(Color.BLACK);

            TB.setChecked(false);
            StringBuilder text = new StringBuilder(techName);

            if (selectedTech != null) {
                if (techName.equals(selectedTech.name)) {
                    TB.setChecked(true);
                    TB.setColor(TB.getColor().cpy().lerp(Color.LIGHT_GRAY, 0.5f));
                }
            }
            if (techsToResearch.contains(techName) && techsToResearch.size()>1) {
                text.append(" (").append(techsToResearch.indexOf(techName)+1).append(")");
            }

            if(!civTech.isResearched(techName)) text.append("\r\n"+civInfo.turnsToTech(techName) + " turns");
            TB.setText(text.toString());
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
            setButtonsInfo();
            return;
        }

        if (civTech.canBeResearched(tech.name)) {
            techsToResearch.clear();
            techsToResearch.add(tech.name);
        } else {
            Stack<String> Prerequisites = new Stack<String>();
            ArrayDeque<String> CheckPrerequisites = new ArrayDeque<String>();
            CheckPrerequisites.add(tech.name);
            while (!CheckPrerequisites.isEmpty()) {
                String techNameToCheck = CheckPrerequisites.pop();
                if (civTech.isResearched(techNameToCheck) || Prerequisites.contains(techNameToCheck))
                    continue; //no need to add or check prerequisites
                Technology techToCheck = GameBasics.Technologies.get(techNameToCheck);
                for (String str : techToCheck.prerequisites)
                    if (!CheckPrerequisites.contains(str)) CheckPrerequisites.add(str);
                Prerequisites.add(techNameToCheck);
            }
            techsToResearch.clear();
            while (!Prerequisites.isEmpty()) techsToResearch.add(Prerequisites.pop());
        }

        pick("Research \r\n" + techsToResearch.get(0));
        setButtonsInfo();
    }

    private void selectTechnologyForFreeTech(Technology tech){
        if (civTech.canBeResearched(tech.name)) {
            pick("Pick " + selectedTech.name+"\r\n as free tech!");
        }
        else {
            rightSideButton.setText("Pick a free tech");
            rightSideButton.setTouchable(Touchable.disabled);
            rightSideButton.setColor(Color.GRAY);
        }
    }

}

