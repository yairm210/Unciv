package core.java.nativefont

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import java.io.ByteArrayOutputStream
import java.util.*

/**
 * Created by tian on 2016/10/2.
 */
class NativeFontAndroid : NativeFontListener {
    private val fontFaces = HashMap<String, Typeface>()
    private val androidApplication = Gdx.app as AndroidApplication
    override fun getFontPixmap(txt: String, vpaint: NativeFontPaint): Pixmap {
        val paint = Paint()
        if (vpaint.tTFName != "") {
            Gdx.app.log("app", Gdx.files.internal(vpaint.tTFName
                    + if (vpaint.tTFName.endsWith(".ttf")) "" else ".ttf").file().path)
            val fontFace = Typeface.createFromAsset(androidApplication.assets, vpaint.tTFName
                    + if (vpaint.tTFName.endsWith(".ttf")) "" else ".ttf")
            fontFaces[vpaint.tTFName] = fontFace
            paint.typeface = fontFace
        }
        paint.isAntiAlias = true
        paint.textSize = vpaint.textSize.toFloat()
        val fm = paint.fontMetrics
        var w = paint.measureText(txt).toInt()
        var h = (fm.descent - fm.ascent).toInt()
        if (w == 0) {
            h = vpaint.textSize
            w = h
        }
        var bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var canvas: Canvas? = Canvas(bitmap!!)
        // 如果是描边类型
        if (vpaint.strokeColor != null) { // 绘制外层
            paint.color = getColor(vpaint.strokeColor)
            paint.strokeWidth = vpaint.strokeWidth.toFloat() // 描边宽度
            paint.style = Paint.Style.FILL_AND_STROKE // 描边种类
            paint.isFakeBoldText = true // 外层text采用粗体
            canvas!!.drawText(txt, 0f, -fm.ascent, paint)
            paint.isFakeBoldText = false
        } else {
            paint.isUnderlineText = vpaint.underlineText
            paint.isStrikeThruText = vpaint.strikeThruText
            paint.isFakeBoldText = vpaint.fakeBoldText
        }
        // 绘制内层
        paint.strokeWidth = 0f
        paint.color = getColor(vpaint.color)
        canvas!!.drawText(txt, 0f, -fm.ascent, paint)
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        val encodedData = buffer.toByteArray()
        val pixmap = Pixmap(encodedData, 0, encodedData.size)
        buffer.close()
        bitmap.recycle()
        return pixmap
    }

    private fun getColor(color: Color?): Int {
        return (color!!.a * 255.0f).toInt() shl 24 or ((color.r * 255.0f).toInt() shl 16) or ((color.g * 255.0f).toInt() shl 8) or (color.b * 255.0f).toInt()
    }
}