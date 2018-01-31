package com.unciv.ui.worldscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.unciv.logic.civilization.CivilizationInfo;
import com.unciv.models.stats.CivStats;
import com.unciv.ui.utils.ImageGetter;

public class CivStatsTable extends Table {
    CivStatsTable(){
        TextureRegionDrawable civBackground = ImageGetter.getDrawable("skin/civTableBackground.png");
        setBackground(civBackground.tint(new Color(0x004085bf)));
    }

    void update(final WorldScreen screen) {
        CivilizationInfo civInfo = screen.game.civInfo;
        Skin skin = screen.skin;
        clear();
        row().pad(15);

        TextButton CivilopediaButton = new TextButton("Menu", skin);
        CivilopediaButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                screen.optionsTable.setVisible(!screen.optionsTable.isVisible());
            }
        });


        CivilopediaButton.getLabel().setFontScale(screen.buttonScale);
        add(CivilopediaButton)
                .size(CivilopediaButton.getWidth() * screen.buttonScale, CivilopediaButton.getHeight() * screen.buttonScale);

        add(new Label("Turns: " + civInfo.turns + "/400", skin));

        CivStats nextTurnStats = civInfo.getStatsForNextTurn();

        add(new Label("Gold: " + Math.round(civInfo.gold)
                + "(" + (nextTurnStats.gold > 0 ? "+" : "") + Math.round(nextTurnStats.gold) + ")", skin));

        Label scienceLabel = new Label("Science: +" + Math.round(nextTurnStats.science)
                + "\r\n" + civInfo.tech.getAmountResearchedText(), skin);
        scienceLabel.setAlignment(Align.center);
        add(scienceLabel);
        String happinessText = "Happiness: " + Math.round(civInfo.getHappinessForNextTurn());
        if (civInfo.goldenAges.isGoldenAge())
            happinessText += "\r\n GOLDEN AGE (" + civInfo.goldenAges.turnsLeftForCurrentGoldenAge + ")";
        else
            happinessText += "\r\n (" + civInfo.goldenAges.storedHappiness + "/"
                    + civInfo.goldenAges.happinessRequiredForNextGoldenAge() + ")";
        Label happinessLabel = new Label(happinessText, skin);
        happinessLabel.setAlignment(Align.center);
        add(happinessLabel);
        String cultureString = "Culture: " + "+" + Math.round(nextTurnStats.culture) + "\r\n"
                + "(" + civInfo.policies.storedCulture + "/" + civInfo.policies.getCultureNeededForNextPolicy() + ")";
        Label cultureLabel = new Label(cultureString, skin);
        cultureLabel.setAlignment(Align.center);
        add(cultureLabel);

        pack();

        setPosition(10, screen.stage.getHeight() - 10 - getHeight());
        setWidth(screen.stage.getWidth() - 20);

    }

}