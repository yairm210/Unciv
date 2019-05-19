package net.mwplay.nativefont;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;

import org.robovm.apple.coregraphics.CGRect;
import org.robovm.apple.coregraphics.CGSize;
import org.robovm.apple.foundation.NSBundle;
import org.robovm.apple.foundation.NSData;
import org.robovm.apple.foundation.NSMutableAttributedString;
import org.robovm.apple.foundation.NSNumber;
import org.robovm.apple.foundation.NSRange;
import org.robovm.apple.foundation.NSString;
import org.robovm.apple.mobilecoreservices.UTType;
import org.robovm.apple.uikit.NSAttributedStringAttribute;
import org.robovm.apple.uikit.NSUnderlineStyle;
import org.robovm.apple.uikit.UIColor;
import org.robovm.apple.uikit.UIFont;
import org.robovm.apple.uikit.UIGraphics;
import org.robovm.apple.uikit.UIImage;
import org.robovm.apple.uikit.UILabel;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by tian on 2016/10/2.
 */

public class NativeFontIOS implements NativeFontListener {
    private HashMap<String, UIFont> fonts;
    private UIColor getColor(Color color) {
        return UIColor.fromRGBA((double) color.r, (double) color.g, (double) color.b, (double) color.a);
    }

    @Override
    public Pixmap getFontPixmap(String strings, NativeFontPaint vpaint) {
        if (fonts == null) fonts = new HashMap<String, UIFont>();
        UIFont font = fonts.get(vpaint.getName());
        if (font == null) {
            if (vpaint.getTTFName().equals("")) {
                if (vpaint.getFakeBoldText() || vpaint.getStrokeColor() != null) {
                    font = UIFont.getBoldSystemFont(vpaint.getTextSize());
                } else {
                    font = UIFont.getSystemFont(vpaint.getTextSize());
                }
            } else {
                TTFParser parser = new TTFParser();
                try {
                    parser.parse(NSBundle.getMainBundle().getResourcePath() + "/" + vpaint.getTTFName()
                            + (vpaint.getTTFName().endsWith(".ttf") ? "" : ".ttf"));
                    font = UIFont.getFont(parser.getFontPSName(), vpaint.getTextSize());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            fonts.put(vpaint.getName(), font);
        }
        NSString string = new NSString(strings);
        CGSize dim = string.getSize(font);
        UILabel label = new UILabel(new CGRect(0, 0, dim.getWidth(),
                dim.getHeight()));
        UILabel label2 = null;// 描边层
        label.setText(strings);
        label.setBackgroundColor(UIColor.fromRGBA(1, 1, 1, 0));
        label.setTextColor(getColor(vpaint.getColor()));
        label.setFont(font);
        label.setOpaque(false);
        label.setAlpha(1);
        NSRange range = new NSRange(0, strings.length());
        NSMutableAttributedString mutableString = new NSMutableAttributedString(
                strings);
        mutableString.addAttribute(NSAttributedStringAttribute.ForegroundColor,
                getColor(vpaint.getColor()), range);
        if (vpaint.getStrokeColor() != null) {
            label2 = new UILabel(new CGRect(0, 0, dim.getWidth(),
                    dim.getHeight()));
            label2.setText(strings);
            label2.setBackgroundColor(UIColor.fromRGBA(1, 1, 1, 0));
            label2.setTextColor(getColor(vpaint.getColor()));
            label2.setFont(font);
            label2.setOpaque(false);
            label2.setAlpha(1);
            NSMutableAttributedString mutableString2 = new NSMutableAttributedString(
                    strings);
            mutableString2.addAttribute(
                    NSAttributedStringAttribute.StrokeColor,
                    getColor(vpaint.getStrokeColor()), range);
            mutableString2.addAttribute(
                    NSAttributedStringAttribute.StrokeWidth,
                    NSNumber.valueOf(vpaint.getStrokeWidth()), range);
            label2.setAttributedText(mutableString2);
        } else if (vpaint.getUnderlineText() == true) {
            mutableString.addAttribute(
                    NSAttributedStringAttribute.UnderlineStyle,
                    NSNumber.valueOf(NSUnderlineStyle.StyleSingle.value()),
                    range);
        } else if (vpaint.getStrikeThruText() == true) {
            mutableString.addAttribute(
                    NSAttributedStringAttribute.StrikethroughStyle,
                    NSNumber.valueOf(NSUnderlineStyle.StyleSingle.value()
                            | NSUnderlineStyle.PatternSolid.value()), range);
        }
        label.setAttributedText(mutableString);
        UIGraphics.beginImageContext(dim, false, 1);
        label.getLayer().render(UIGraphics.getCurrentContext());
        if (vpaint.getStrokeColor() != null) {
            label2.getLayer().render(UIGraphics.getCurrentContext());
        }
        UIImage image = UIGraphics.getImageFromCurrentImageContext();
        UIGraphics.endImageContext();
        NSData nsData = image.toPNGData();
        Pixmap pixmap = new Pixmap(nsData.getBytes(), 0, nsData.getBytes().length);
        return pixmap;
    }
}
