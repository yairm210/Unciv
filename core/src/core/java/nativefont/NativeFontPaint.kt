package core.java.nativefont

import com.badlogic.gdx.graphics.Color

/**
 * Created by tian on 2016/10/2.
 */
class NativeFontPaint {
    var textSize = 30 // 字号
    var color = Color.WHITE // 颜色
    var fakeBoldText = false // 是否粗体
    var underlineText = false // 是否下划线
    var strikeThruText = false // 是否删除线
    var strokeColor: Color? = null // 描边颜色
    var strokeWidth = 3 // 描边宽度
    var tTFName = ""
    val name: String
        get() {
            val name = StringBuffer()
            name.append(tTFName).append("_").append(textSize).append("_").append(color.toIntBits())
                    .append("_").append(booleanToInt(fakeBoldText)).append("_")
                    .append(booleanToInt(underlineText))
            if (strokeColor != null) {
                name.append("_").append(strokeColor!!.toIntBits()).append("_").append(strokeWidth)
            }
            return name.toString()
        }

    private fun booleanToInt(b: Boolean): Int {
        return if (b == true) 0 else 1
    }

    constructor() {}
    constructor(ttfName: String, textSize: Int, color: Color, stroke: Color?, strokeWidth: Int,
                bold: Boolean, line: Boolean, thru: Boolean) {
        tTFName = ttfName
        this.textSize = textSize
        this.color = color
        strokeColor = stroke
        this.strokeWidth = strokeWidth
        fakeBoldText = bold
        underlineText = line
        strikeThruText = thru
    }

    constructor(ttfName: String) {
        tTFName = ttfName
    }

    constructor(ttfName: String, size: Int) {
        tTFName = ttfName
        textSize = size
    }

    constructor(ttfName: String, size: Int, color: Color) {
        tTFName = ttfName
        textSize = size
        this.color = color
    }

    constructor(ttfName: String, color: Color) {
        tTFName = ttfName
        this.color = color
    }

    constructor(size: Int) {
        textSize = size
    }

    constructor(color: Color) {
        this.color = color
    }

    constructor(size: Int, color: Color) {
        textSize = size
        this.color = color
    }

}