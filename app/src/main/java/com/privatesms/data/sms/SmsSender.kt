package com.privatesms.data.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmsSender @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun sendSms(address: String, body: String, messageId: Long, subscriptionId: Int? = null) {
        val smsManager: SmsManager = if (subscriptionId != null && subscriptionId != -1) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }

        val sentIntent = Intent("com.privatesms.SMS_SENT").apply {
            data = Uri.parse("sms_id://$messageId")
        }
        val sentPI = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            sentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deliveredIntent = Intent("com.privatesms.SMS_DELIVERED").apply {
            data = Uri.parse("sms_id://$messageId")
        }
        val deliveredPI = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val parts = smsManager.divideMessage(body)
        if (parts.size > 1) {
            val sentIntents = ArrayList<PendingIntent>().apply {
                for (i in 0 until parts.size) {
                    add(sentPI)
                }
            }
            val deliveredIntents = ArrayList<PendingIntent>().apply {
                for (i in 0 until parts.size) {
                    add(deliveredPI)
                }
            }
            smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveredIntents)
        } else {
            smsManager.sendTextMessage(address, null, body, sentPI, deliveredPI)
        }
    }
}
