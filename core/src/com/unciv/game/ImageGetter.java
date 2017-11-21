package com.unciv.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

import java.util.HashMap;

public class ImageGetter {
        static HashMap<String, TextureRegion> textureRegionByFileName = new HashMap<String, TextureRegion>();

        public static Image getImageByFilename(String fileName) {
                if (!textureRegionByFileName.containsKey(fileName))
                        textureRegionByFileName.put(fileName, new TextureRegion(new Texture(Gdx.files.internal(fileName))));
                return new Image(textureRegionByFileName.get(fileName));
        }

        public static Image getStatIcon(String name) {
                return getImageByFilename("StatIcons/20x" + name + "5.png");
        }
}
