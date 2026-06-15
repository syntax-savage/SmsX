package com.privatesms.data.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is a headless service that receives the intent to send an SMS response 
        // to an incoming call. Since it is a background operation, we can handle it 
        // by stopping immediately, as it is just a system requirement check.
        stopSelf()
        return START_NOT_STICKY
    }
}
