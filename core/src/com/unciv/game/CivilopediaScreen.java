package com.unciv.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Value;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.unciv.models.gamebasics.GameBasics;
import com.unciv.models.gamebasics.ICivilopedia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

public class CivilopediaScreen extends CameraStageBaseScreen {
    public CivilopediaScreen(final UnCivGame game) {

        super(game);
        Gdx.input.setInputProcessor(stage);
        Table buttonTable = new Table();
        buttonTable.pad(15);
        Table entryTable = new Table();
        SplitPane SP = new SplitPane(buttonTable, entryTable, true, skin);
        SP.setSplitAmount(0.2f);
        SP.setFillParent(true);

        stage.addActor(SP);

        final Label label = new Label("", skin);
        label.setWrap(true);

        TextButton goToGameButton = new TextButton("Return \r\nto game",skin);
        goToGameButton.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setWorldScreen();
                dispose();
            }
        });
        buttonTable.add(goToGameButton);

        final LinkedHashMap<String, Collection<ICivilopedia>> map = new LinkedHashMap<String, Collection<ICivilopedia>>();

        map.put("Basics", GameBasics.Helps.linqValues().as(ICivilopedia.class));
        map.put("Buildings", GameBasics.Buildings.linqValues().as(ICivilopedia.class));
        map.put("Resources", GameBasics.TileResources.linqValues().as(ICivilopedia.class));
        map.put("Terrains", GameBasics.Terrains.linqValues().as(ICivilopedia.class));
        map.put("Tile Improvements", GameBasics.TileImprovements.linqValues().as(ICivilopedia.class));

        final List<ICivilopedia> nameList = new List<ICivilopedia>(skin);

        final ClickListener namelistClickListener = new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ICivilopedia building = nameList.getSelected();
                if (building == null) return;
                label.setText(building.getDescription());
                super.clicked(event, x, y);
            }
        };
        nameList.addListener(namelistClickListener);

        nameList.getStyle().fontColorSelected = Color.BLACK;
        nameList.getStyle().font.getData().setScale(1.5f);

        final ArrayList<Button> buttons = new ArrayList<Button>();
        boolean first = true;
        for (final String str : map.keySet()) {
            final TextButton button = new TextButton(str, skin);
            button.getStyle().checkedFontColor = Color.BLACK;
                        buttons.add(button);
            ClickListener listener = new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Array<ICivilopedia> newArray = new Array<ICivilopedia>();
                    for (ICivilopedia civ : map.get(str)) newArray.add(civ);
                    nameList.setItems(newArray);
                    nameList.setSelected(nameList.getItems().get(0));
                    namelistClickListener.clicked(null, 0, 0); // fake-click the first item, so the text is displayed
                    for (Button btn : buttons) btn.setChecked(false);
                    button.setChecked(true);
                }
            };
            if (first) {// Fake-click the first button so that the user sees results immediately
                first = false;
                listener.clicked(null, 0, 0);
            }
            button.addListener(listener);
            button.getLabel().setFontScale(0.7f);
            buttonTable.add(button).width(button.getWidth()*0.7f);
        }

        ScrollPane sp = new ScrollPane(nameList);
        sp.setupOverscroll(5, 1, 200);
        entryTable.add(sp).width(Value.percentWidth(0.25f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable));
        entryTable.add(label).colspan(4).width(Value.percentWidth(0.65f, entryTable)).height(Value.percentHeight(0.7f, entryTable))
                .pad(Value.percentWidth(0.02f, entryTable));

        buttonTable.setWidth(stage.getWidth());
    }

}

