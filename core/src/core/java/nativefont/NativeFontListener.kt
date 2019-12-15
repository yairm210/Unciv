package core.java.nativefont

import com.badlogic.gdx.graphics.Pixmap

/**
 * Created by tian on 2016/10/2.
 */
interface NativeFontListener {
    fun getFontPixmap(txt: String, vpaint: NativeFontPaint): Pixmap
}