package com.unciv.app.turncheck

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Push-based multiplayer turn checker for APIv2
 */
class WorkerV2(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        TODO("Not yet implemented")
    }
}
