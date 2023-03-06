package com.unciv.logic.multiplayer.structs

import com.squareup.moshi.Json

data class LoginRequest (
    @Json(name = "username")
    var username: String,
    @Json(name = "password")
    var password: String
)
