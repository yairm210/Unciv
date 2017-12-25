package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.unciv.game.pickerscreens.PickerScreen;
import com.unciv.models.LinqCounter;

public class ScienceVictoryScreen extends PickerScreen {

    public ScienceVictoryScreen() {
        LinqCounter<String> builtSpaceshipParts = game.civInfo.scienceVictory.currentParts.clone();

        for (int i = 0; i < 3; i++) {
            addPartButton("SS Booster",builtSpaceshipParts);
        }
        addPartButton("SS Cockpit",builtSpaceshipParts);
        addPartButton("SS Engine",builtSpaceshipParts);
        addPartButton("SS Statis Chamber",builtSpaceshipParts);
        rightSideButton.setVisible(false);

        if(!game.civInfo.getBuildingUniques().contains("ApolloProject"))
            descriptionLabel.setText("You must build the Apollo Project before you can build spaceship parts!");
        else descriptionLabel.setText("Apollo project is built - you may construct spaceship parts in your cities!");
    }

    private void addPartButton(String partName, LinqCounter<String> parts){
        topTable.row();
        TextButton button = new TextButton(partName,skin);
        button.setTouchable(Touchable.disabled);
        if (parts.get(partName)>0){
            button.setColor(Color.GREEN);
            parts.add(partName,-1);
        }
        topTable.add(button).pad(10);
    }
}


