package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

fun interface OtpSmsListener {
    fun onOtpCodeReceived(code: String)
}

object OtpSmsBridge {
    private val listeners = CopyOnWriteArrayList<OtpSmsListener>()

    @Volatile
    private var lastDispatchedCode: String = ""

    @Volatile
    private var lastDispatchedAtMillis: Long = 0L

    fun register(listener: OtpSmsListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregister(listener: OtpSmsListener) {
        listeners.remove(listener)
    }

    fun dispatchOtp(code: String) {
        val now = System.currentTimeMillis()
        if (code == lastDispatchedCode && (now - lastDispatchedAtMillis) < 10_000L) {
            return
        }
        lastDispatchedCode = code
        lastDispatchedAtMillis = now

        for (listener in listeners) {
            try {
                listener.onOtpCodeReceived(code)
            } catch (_: Throwable) {
            }
        }
    }

    fun extractOtpCode(rawMessage: String): String? {
        val normalized = StringBuilder(rawMessage.length).apply {
            for (ch in rawMessage) {
                when (ch) {
                    in '٠'..'٩' -> append((ch - '٠' + '0'.code).toChar())
                    in '۰'..'۹' -> append((ch - '۰' + '0'.code).toChar())
                    else -> append(ch)
                }
            }
        }.toString()

        val patterns = listOf(
            Pattern.compile("(?:رمز|كود|التحقق|تفعيل|code\\s*is|code|otp|pin|verify)[^0-9]{0,20}\\b(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{4,6})\\b")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(normalized)
            if (matcher.find()) {
                val found = matcher.group(1)
                if (!found.isNullOrBlank()) {
                    return found
                }
            }
        }
        return null
    }
}

/**
 * Listens passively to incoming SMS verification codes on Screen 2
 * so the OTP is filled automatically when RECEIVE_SMS permission is granted.
 */
class SmsLiveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBody = buildString {
                for (sms in messages) {
                    append(sms?.displayMessageBody ?: sms?.messageBody ?: "")
                }
            }.trim()

            if (fullBody.isEmpty()) return

            val otp = OtpSmsBridge.extractOtpCode(fullBody)
            if (!otp.isNullOrBlank()) {
                Handler(Looper.getMainLooper()).post {
                    OtpSmsBridge.dispatchOtp(otp)
                }
            }
        } catch (_: Throwable) {
        }
    }
}
