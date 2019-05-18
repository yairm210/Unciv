package core.java.nativefont;

import com.badlogic.gdx.graphics.Pixmap;

/**
 * Created by tian on 2016/10/2.
 */

public interface NativeFontListener {
    Pixmap getFontPixmap(String str, NativeFontPaint freePaint);
}
