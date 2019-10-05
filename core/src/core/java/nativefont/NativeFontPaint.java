package core.java.nativefont;

import com.badlogic.gdx.graphics.Color;

/**
 * Created by tian on 2016/10/2.
 */

public class NativeFontPaint {
    private int textSize = 30;// 字号
    private Color color = Color.WHITE;// 颜色
    private boolean isFakeBoldText = false;// 是否粗体
    private boolean isUnderlineText = false;// 是否下划线
    private boolean isStrikeThruText = false;// 是否删除线
    private Color strokeColor = null;// 描边颜色
    private int strokeWidth = 3;// 描边宽度
    private String ttfName = "";

    public String getName() {
        StringBuffer name = new StringBuffer();
        name.append(ttfName).append("_").append(textSize).append("_").append(color.toIntBits())
                .append("_").append(booleanToInt(isFakeBoldText)).append("_")
                .append(booleanToInt(isUnderlineText));
        if (strokeColor != null) {
            name.append("_").append(strokeColor.toIntBits()).append("_").append(strokeWidth);
        }
        return name.toString();
    }

    private int booleanToInt(boolean b) {
        return b == true ? 0 : 1;
    }

    public NativeFontPaint() {
    }

    public NativeFontPaint(String ttfName, int textSize, Color color, Color stroke, int strokeWidth,
                           boolean bold, boolean line, boolean thru) {
        this.ttfName = ttfName;
        this.textSize = textSize;
        this.color = color;
        this.strokeColor = stroke;
        this.strokeWidth = strokeWidth;
        this.isFakeBoldText = bold;
        this.isUnderlineText = line;
        this.isStrikeThruText = thru;
    }

    public NativeFontPaint(String ttfName) {
        this.ttfName = ttfName;
    }

    public NativeFontPaint(String ttfName, int size) {
        this.ttfName = ttfName;
        this.textSize = size;
    }

    public NativeFontPaint(String ttfName, int size, Color color) {
        this.ttfName = ttfName;
        this.textSize = size;
        this.color = color;
    }

    public NativeFontPaint(String ttfName, Color color) {
        this.ttfName = ttfName;
        this.color = color;
    }

    public NativeFontPaint(int size) {
        this.textSize = size;
    }

    public NativeFontPaint(Color color) {
        this.color = color;
    }

    public NativeFontPaint(int size, Color color) {
        this.textSize = size;
        this.color = color;
    }

    public int getTextSize() {
        return textSize;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean getFakeBoldText() {
        return isFakeBoldText;
    }

    public void setFakeBoldText(boolean isFakeBoldText) {
        this.isFakeBoldText = isFakeBoldText;
    }

    public boolean getUnderlineText() {
        return isUnderlineText;
    }

    public void setUnderlineText(boolean isUnderlineText) {
        this.isUnderlineText = isUnderlineText;
    }

    public boolean getStrikeThruText() {
        return isStrikeThruText;
    }

    public void setStrikeThruText(boolean isStrikeThruText) {
        this.isStrikeThruText = isStrikeThruText;
    }

    public Color getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(Color strokeColor) {
        this.strokeColor = strokeColor;
    }

    public int getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(int strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    public void setTTFName(String ttfName) {
        this.ttfName = ttfName;
    }

    public String getTTFName() {
        return ttfName;
    }

}
