package com.unciv.logic.multiplayer.structs

import com.squareup.moshi.Json

data class RegisterRequest (
    @Json(name = "username")
    var username: String,
    @Json(name = "display_name")
    var displayName: String,
    @Json(name = "password")
    var password: String
)
