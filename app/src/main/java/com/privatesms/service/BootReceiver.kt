package com.privatesms.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.privatesms.data.db.DatabaseManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var databaseManager: DatabaseManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // DatabaseManager init block will run automatically upon app start (which this broadcast triggers)
            // and reschedule alarms if the app lock is disabled. Otherwise, they will be rescheduled
            // on the first unlock event.
        }
    }
}
