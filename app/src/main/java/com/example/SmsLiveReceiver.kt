package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony

/**
 * Real Android BroadcastReceiver that intercepts live incoming SMS messages
 * from Yemen Mobile (181, 121), Sabafon (211), YOU (111), Yemen Net (114), or OTP gateways.
 */
class SmsLiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            // Group multi-part SMS messages by originating sender address
            val groupedBySender = LinkedHashMap<String, StringBuilder>()
            var timestamp = System.currentTimeMillis()

            for (sms in messages) {
                if (sms == null) continue
                val sender = sms.displayOriginatingAddress ?: sms.originatingAddress ?: "الشبكة"
                val bodyPart = sms.displayMessageBody ?: sms.messageBody ?: ""
                if (sms.timestampMillis > 0) {
                    timestamp = sms.timestampMillis
                }
                val builder = groupedBySender.getOrPut(sender) { StringBuilder() }
                builder.append(bodyPart)
            }

            val mainHandler = Handler(Looper.getMainLooper())
            for ((sender, fullBodyBuilder) in groupedBySender) {
                val fullText = fullBodyBuilder.toString().trim()
                if (fullText.isNotEmpty()) {
                    mainHandler.post {
                        RealTelecomEngine.notifyRealIncomingSms(
                            sender = sender,
                            body = fullText,
                            timestampMillis = timestamp
                        )
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }
}
