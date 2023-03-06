package com.unciv.logic.multiplayer.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Date

/**
 * Data serialization utility object
 */
object DataSerializer {
    @JvmStatic
    val moshiBuilder: Moshi.Builder = Moshi.Builder()
        .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
        .add(OffsetDateTimeAdapter())
        .add(LocalDateTimeAdapter())
        .add(LocalDateAdapter())
        .add(UUIDAdapter())
        .add(ByteArrayAdapter())
        .add(KotlinJsonAdapterFactory())

    @JvmStatic
    val moshi: Moshi by lazy {
        moshiBuilder.build()
    }
}
