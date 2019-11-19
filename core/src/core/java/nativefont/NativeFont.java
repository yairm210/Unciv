package core.java.nativefont;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.PixmapPacker.Page;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by tian on 2016/10/2.
 */

public class NativeFont extends BitmapFont {
    private static NativeFontListener listener;
    private static boolean robovm;

    private Set<String> charSet;
    private BitmapFontData data;
    private HashMap<String, EmojiDate> emojiSet;
    private Texture.TextureFilter magFilter;
    private Texture.TextureFilter minFilter;
    private PixmapPacker packer;
    private int pageWidth;
    private NativeFontPaint paint;
    private int size;

    public class EmojiDate {
        public String path;
        public int size;

        public EmojiDate(String path, int size) {
            this.path = path;
            this.size = size;
        }
    }


    public NativeFont() {
        this(new NativeFontPaint());
    }

    public NativeFont(NativeFontPaint paint) {
        super(new BitmapFontData(), new TextureRegion(), false);
        this.pageWidth = 512;
        this.paint = new NativeFontPaint();
        this.charSet = new HashSet();
        this.packer = null;
        this.minFilter = Texture.TextureFilter.Linear;
        this.magFilter = Texture.TextureFilter.Linear;
        this.emojiSet = new HashMap();
        updataSize(paint.getTextSize());
        if (listener == null) createListener();
        this.paint = paint;
    }

    private void createListener() {
        String className = "core.java.nativefont.NativeFont";
        if (Gdx.app.getType() == Application.ApplicationType.Desktop) {
            className += "Desktop";
        } else if (Gdx.app.getType() == Application.ApplicationType.Android) {
            className += "Android";
        } else if (Gdx.app.getType() == Application.ApplicationType.iOS) {
            if (robovm)
                className += "IOS";
            else
                className += "IOSMoe";
        }else if (Gdx.app.getType() == Application.ApplicationType.WebGL){
            className += "Html";
        }
        try {
            Class<? extends NativeFontListener> claz = (Class<? extends NativeFontListener>) Gdx.app.getClass().getClassLoader().loadClass(className);
            listener = claz.newInstance();
        } catch (Exception e) {
            throw new GdxRuntimeException("Class Not Found:" + e.getMessage());
        }
    }

    public void updataSize(int newSize) {
        this.data = getData();
        this.size = Math.max(newSize, this.size);
        this.data.down = (float) (-this.size);
        this.data.ascent = (float) (-this.size);
        this.data.capHeight = (float) this.size;
        this.data.lineHeight = (float) this.size;
    }

    public NativeFont setTextColor(Color color) {
        this.paint.setColor(color);
        return this;
    }

    public NativeFont setStrokeColor(Color color) {
        this.paint.setStrokeColor(color);
        return this;
    }

    public NativeFont setStrokeWidth(int width) {
        this.paint.setStrokeWidth(width);
        return this;
    }

    public NativeFont setSize(int size) {
        this.paint.setTextSize(size);
        return this;
    }

    public NativeFont setBold(boolean istrue) {
        this.paint.setFakeBoldText(istrue);
        return this;
    }

    public NativeFont setUnderline(boolean istrue) {
        this.paint.setUnderlineText(istrue);
        return this;
    }

    public NativeFont setStrikeThru(boolean istrue) {
        this.paint.setStrikeThruText(istrue);
        return this;
    }

    public NativeFont setPaint(NativeFontPaint paint) {
        this.paint = paint;
        return this;
    }

    public NativeFont addEmojiPath(String emojiKey, String imgPath, int size) {
        this.emojiSet.put(emojiKey, new EmojiDate(imgPath, size));
        return this;
    }

    public NativeFont appendEmoji(String txt, String imgname, int size) {
        Pixmap pixmap = new Pixmap(Gdx.files.internal(imgname));
       // Pixmap.setFilter(Pixmap.Filter.BiLinear);
        Pixmap pixmap2 = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap2.setFilter(Pixmap.Filter.BiLinear);
        pixmap2.drawPixmap(pixmap, 0, 0, pixmap.getWidth(), pixmap.getHeight(), 0, 0, size, size);
        pixmap.dispose();
        appendEmoji(txt, pixmap2);
        return this;
    }

    public NativeFont appendEmoji(String txt, Pixmap pixmap) {
        if (this.charSet.add(txt)) {
            if (this.packer == null) {
                this.packer = new PixmapPacker(this.pageWidth, this.pageWidth, Pixmap.Format.RGBA8888, 2, false);
            }
            putGlyph(txt.charAt(0), pixmap);
            updataSize(pixmap.getHeight());
            upData();
        }
        return this;
    }

    public NativeFont createText(String characters) {
        if (!(characters == null || characters.length() == 0)) {
            create(characters, true);
            end();
        }
        return this;
    }

    public NativeFont appendText(String characters) {
        if (!(characters == null || characters.length() == 0)) {
            create(characters, false);
        }
        return this;
    }

    private void create(String characters, boolean haveMinPageSize) {
        char c;
        characters = characters.replaceAll("[\\t\\n\\x0B\\f\\r]", "");
        Array<String> cs = new Array<String>();
        for (char c2 : characters.toCharArray()) {
            if (this.charSet.add((String.valueOf(c2)))) {
                cs.add((String.valueOf(c2)));
            }
        }
        if (haveMinPageSize) {
            this.pageWidth = (this.paint.getTextSize() + 2) * ((int) (Math.sqrt((double) cs.size) + 1.0d));
        }
        if (this.packer == null) {
            this.packer = new PixmapPacker(this.pageWidth, this.pageWidth, Pixmap.Format.RGBA8888, 2, false);
        }

        char c2;
        for (int i = 0; i < cs.size; i++) {
            String txt = cs.get(i);
            c2 = txt.charAt(0);
            String css = String.valueOf(c2);
            if (this.emojiSet.get(css) != null) {
                this.charSet.remove(css);
                EmojiDate date = this.emojiSet.get(css);
                appendEmoji(c2 + "", date.path, date.size);
            } else {
                putGlyph(c2, listener.getFontPixmap(txt, this.paint));
            }
        }

        updataSize(this.size);
        upData();
        if (getRegions().size == 1) {
            setOwnsTexture(true);
        } else {
            setOwnsTexture(false);
        }
    }

    private void putGlyph(char c, Pixmap pixmap) {
        Rectangle rect = this.packer.pack(String.valueOf(c), pixmap);
        pixmap.dispose();
        int pIndex = this.packer.getPageIndex((String.valueOf(c)));
        Glyph glyph = new Glyph();
        glyph.id = c;
        glyph.page = pIndex;
        glyph.srcX = (int) rect.x;
        glyph.srcY = (int) rect.y;
        glyph.width = (int) rect.width;
        glyph.height = (int) rect.height;
        glyph.xadvance = glyph.width;
        this.data.setGlyph(c, glyph);
    }

    private void upData() {
        Glyph spaceGlyph = this.data.getGlyph(' ');
        if (spaceGlyph == null) {
            spaceGlyph = new Glyph();
            Glyph xadvanceGlyph = this.data.getGlyph('l');
            if (xadvanceGlyph == null) {
                xadvanceGlyph = this.data.getFirstGlyph();
            }
            spaceGlyph.xadvance = xadvanceGlyph.xadvance;
            spaceGlyph.id = 32;
            this.data.setGlyph(32, spaceGlyph);
        }
        this.data.spaceXadvance = (float) (spaceGlyph.xadvance + spaceGlyph.width);

        Array<Page> pages = this.packer.getPages();
        Array<TextureRegion> regions = getRegions();
        int regSize = regions.size - 1;
        for (int i = 0; i < pages.size; i++) {
            Page p =  pages.get(i);
            if (i > regSize) {
                p.updateTexture(this.minFilter, this.magFilter, false);
                regions.add(new TextureRegion(p.getTexture()));
            } else {
                if (p.updateTexture(this.minFilter, this.magFilter, false)) {
                    regions.set(i, new TextureRegion(p.getTexture()));
                }
            }
        }

        for (Glyph[] page : this.data.glyphs) {
            if (page == null) continue;

            for (Glyph glyph : page) {
                if (glyph != null) {
                    TextureRegion region = getRegions().get(glyph.page);
                    if (region == null) {
                        throw new IllegalArgumentException("BitmapFont texture region array cannot contain null elements.");
                    }
                    this.data.setGlyphRegion(glyph, region);
                }
            }
        }
    }

    public NativeFont end() {
        this.paint = null;
        this.charSet.clear();
        this.charSet = null;
        this.packer.dispose();
        this.packer = null;
        return this;
    }

    public void dispose() {
        end();
        super.dispose();
    }

    public static void setRobovm() {
        robovm = true;
    }

    public static NativeFontListener getListener() {
        return listener;
    }
}
