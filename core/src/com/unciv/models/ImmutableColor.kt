package com.unciv.models

import com.badlogic.gdx.graphics.Color

class ImmutableColor(color: Color): Color(color.r, color.g, color.b, color.a) {
    override fun add(color: Color?): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
    override fun mul(color: Color?): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
    override fun mul(r: Float, g: Float, b: Float, a: Float): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
    override fun premultiplyAlpha(): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
    override fun sub(color: Color?): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")   
    override fun lerp(target: Color?, t: Float): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
    override fun lerp(r: Float, g: Float, b: Float, a: Float, t: Float): Color = throw UnsupportedOperationException("ImmutableColor cannot be modified")
}