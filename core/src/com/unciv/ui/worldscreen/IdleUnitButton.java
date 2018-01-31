package com.unciv.ui.worldscreen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Predicate;
import com.unciv.logic.map.TileInfo;
import com.unciv.models.linq.Linq;
import com.unciv.ui.utils.CameraStageBaseScreen;

public class IdleUnitButton extends TextButton {

    final WorldScreen worldScreen;
    IdleUnitButton(final WorldScreen worldScreen) {
        super("Select next idle unit", CameraStageBaseScreen.skin);
        this.worldScreen = worldScreen;
        setPosition(worldScreen.stage.getWidth() / 2 - getWidth() / 2, 5);
        addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Linq<TileInfo> tilesWithIdleUnits = worldScreen.game.civInfo.tileMap.values().where(new Predicate<TileInfo>() {
                    @Override
                    public boolean evaluate(TileInfo arg0) {
                        return arg0.hasIdleUnit();
                    }
                });

                TileInfo tileToSelect;
                if (!tilesWithIdleUnits.contains(worldScreen.tileMapHolder.selectedTile))
                    tileToSelect = tilesWithIdleUnits.get(0);
                else {
                    int index = tilesWithIdleUnits.indexOf(worldScreen.tileMapHolder.selectedTile) + 1;
                    if (tilesWithIdleUnits.size() == index) index = 0;
                    tileToSelect = tilesWithIdleUnits.get(index);
                }
                worldScreen.tileMapHolder.setCenterPosition(tileToSelect.position);
                worldScreen.tileMapHolder.selectedTile = tileToSelect;
                worldScreen.update();
            }
        });
    }

    void update() {
        if (worldScreen.game.civInfo.tileMap.values().any(new Predicate<TileInfo>() {
            @Override
            public boolean evaluate(TileInfo arg0) {
                return arg0.hasIdleUnit();
            }
        })) {
            worldScreen.idleUnitButton.setColor(Color.WHITE);
            worldScreen.idleUnitButton.setTouchable(Touchable.enabled);
        } else {
            worldScreen.idleUnitButton.setColor(Color.GRAY);
            worldScreen.idleUnitButton.setTouchable(Touchable.disabled);
        }
    }
}
