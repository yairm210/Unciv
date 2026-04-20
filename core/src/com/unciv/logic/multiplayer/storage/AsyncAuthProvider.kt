package com.unciv.logic.multiplayer.storage

interface AsyncAuthProvider {
    fun authenticateAsync(password: String?, callback: (Result<Boolean>) -> Unit)
    fun setPasswordAsync(password: String, callback: (Result<Boolean>) -> Unit)
    fun checkAuthStatusAsync(userId: String, password: String, callback: (AuthStatus) -> Unit)
}
