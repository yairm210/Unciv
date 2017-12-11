package com.unciv.game;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.unciv.civinfo.CivilizationInfo;
import com.unciv.game.pickerscreens.PickerScreen;
import com.unciv.models.LinqCounter;

public class ScienceVictoryScreen extends PickerScreen {

    public ScienceVictoryScreen(UnCivGame game) {
        super(game);
        LinqCounter<String> spaceshipParts = new LinqCounter<String>();
        spaceshipParts.add(game.civInfo.spaceshipParts);

        for (int i = 0; i < 3; i++) {
            addPartButton("SS Booster",spaceshipParts);
        }
        addPartButton("SS Cockpit",spaceshipParts);
        addPartButton("SS Engine",spaceshipParts);
        addPartButton("SS Statis Chamber",spaceshipParts);
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


