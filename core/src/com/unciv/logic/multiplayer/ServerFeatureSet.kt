package com.unciv.logic.multiplayer


/**
 * This class is used to store the features of the server.
 *
 * We use version numbers instead of simple boolean
 * to allow for future expansion and backwards compatibility.
 *
 * Everything is optional, so if a feature is not present, it is assumed to be 0.
 * Dropbox does not support anything of this, so it will always be 0.
 *
 * The API developed to replace dropbox which uses preview files and
 * polling via HTTP is referred to as API v1, [apiVersion] = 1.
 * It may or may not support auth. The new WebSocket-based and extended
 * API is referred to as API v2, [apiVersion] = 2. It's not directly a
 * feature set, but rather another refined interface.
 */
data class ServerFeatureSet(
    val authVersion: Int = 0,
    val apiVersion: Int
)
