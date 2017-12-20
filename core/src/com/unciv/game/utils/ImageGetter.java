package com.unciv.game.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

import java.util.HashMap;

public class ImageGetter {
    public static HashMap<String, TextureRegion> textureRegionByFileName = new HashMap<String, TextureRegion>();
    public static final String WhiteDot="skin/whiteDot.png";

    public static Image getImage(String fileName) {
        return new Image(getTextureRegion(fileName));
    }

    public static TextureRegionDrawable getDrawable(String fileName) {
        return new TextureRegionDrawable(getTextureRegion(fileName));
    }

    private static TextureRegion getTextureRegion(String fileName) {
        if (!textureRegionByFileName.containsKey(fileName))
            textureRegionByFileName.put(fileName, new TextureRegion(new Texture(Gdx.files.internal(fileName))));
        return textureRegionByFileName.get(fileName);
    }

    public static Image getStatIcon(String name) {
        return getImage("StatIcons/20x" + name + "5.png");
    }

}
