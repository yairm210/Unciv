package com.unciv.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.linq.Linq;
import com.unciv.models.linq.LinqCounter;

public class ScienceVictoryScreen extends com.unciv.ui.pickerscreens.PickerScreen {

    public ScienceVictoryScreen() {
        LinqCounter<String> builtSpaceshipParts = game.civInfo.scienceVictory.currentParts.clone();

        for (String key: game.civInfo.scienceVictory.requiredParts.keySet()) // can't take the keyset because we would be modifying it!
            for(int i = 0; i < game.civInfo.scienceVictory.requiredParts.get(key); i++)
                addPartButton(key,builtSpaceshipParts);

        rightSideButton.setVisible(false);

        if(!game.civInfo.getBuildingUniques().contains("ApolloProgram"))
            descriptionLabel.setText("You must build the Apollo Program before you can build spaceship parts!");
        else descriptionLabel.setText("Apollo program is built - you may construct spaceship parts in your cities!");

        Linq<String> tutorial = new Linq<String>();
        tutorial.add("This is the science victory screen, where you" +
                "\r\n  can see your progress towards constructing a " +
                "\r\n  spaceship to propel you towards the stars.");
        tutorial.add("There are 6 spaceship parts you must build, " +
                "\r\n  and they all require advanced technologies");
        if(!CivilizationInfo.current().getBuildingUniques().contains("ApolloProgram"))
        tutorial.add("You can start constructing spaceship parts" +
                "\r\n  only after you have finished the Apollo Program");
        displayTutorials("ScienceVictoryScreenEntered",tutorial);
    }

    private void addPartButton(String partName, LinqCounter<String> parts){
        topTable.row();
        TextButton button = new TextButton(partName,skin);
        button.setTouchable(Touchable.disabled);
        if(!game.civInfo.getBuildingUniques().contains("ApolloProgram"))
            button.setColor(Color.GRAY);
        else if (parts.get(partName)>0){
            button.setColor(Color.GREEN);
            parts.add(partName,-1);
        }
        topTable.add(button).pad(10);
    }
}


