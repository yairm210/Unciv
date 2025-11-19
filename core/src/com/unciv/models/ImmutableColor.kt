package com.unciv.models

import com.badlogic.gdx.graphics.Color

class ImmutableColor(color: Color): Color(color.r, color.g, color.b, color.a) {
    private fun throwModificationError(): Nothing =
        throw UnsupportedOperationException("ImmutableColor cannot be modified - use .cpy() first to create a mutable copy")
    override fun add(color: Color?): Color = throwModificationError()
    override fun mul(color: Color?): Color = throwModificationError()
    override fun mul(r: Float, g: Float, b: Float, a: Float): Color = throwModificationError()
    override fun premultiplyAlpha(): Color = throwModificationError()
    override fun sub(color: Color?): Color = throwModificationError()   
    override fun lerp(target: Color?, t: Float): Color = throwModificationError()
    override fun lerp(r: Float, g: Float, b: Float, a: Float, t: Float): Color = throwModificationError()
}
