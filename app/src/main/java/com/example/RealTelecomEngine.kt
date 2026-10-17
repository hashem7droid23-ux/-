package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern

data class RealUssdParsedBalance(
    val rawUssdResponse: String,
    val extractedBalanceRial: Double?,
    val extractedLoanRial: Double?,
    val extractedExpiryDate: String?,
    val extractedDataMb: Int?,
    val extractedMinutes: Int?,
    val extractedSms: Int?,
    val extractedPackageTitle: String?
)

data class RealSimSlotInfo(
    val slotIndex: Int,
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val number: String
)

data class RealInboxSmsItem(
    val id: Long,
    val sender: String,
    val body: String,
    val timestampMillis: Long,
    val parsed: RealUssdParsedBalance
)

fun interface RealIncomingSmsListener {
    fun onRealSmsReceived(sender: String, body: String, timestampMillis: Long)
}

object RealTelecomEngine {

    private val smsListeners = CopyOnWriteArrayList<RealIncomingSmsListener>()

    fun registerIncomingSmsListener(listener: RealIncomingSmsListener) {
        if (!smsListeners.contains(listener)) {
            smsListeners.add(listener)
        }
    }

    fun unregisterIncomingSmsListener(listener: RealIncomingSmsListener) {
        smsListeners.remove(listener)
    }

    fun notifyRealIncomingSms(sender: String, body: String, timestampMillis: Long) {
        for (listener in smsListeners) {
            try {
                listener.onRealSmsReceived(sender, body, timestampMillis)
            } catch (_: Throwable) {
            }
        }
    }

    fun hasCallPhonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasSmsPermissions(context: Context): Boolean {
        val receive = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val read = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val send = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        return receive && read && send
    }

    fun hasReadSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Reads the real active SIM cards installed in the device (SIM 1 / SIM 2)
     */
    @SuppressLint("MissingPermission")
    fun getDetectedSimCards(context: Context): List<RealSimSlotInfo> {
        if (!hasReadPhoneStatePermission(context)) {
            return emptyList()
        }
        return try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val activeSubs = subManager?.activeSubscriptionInfoList.orEmpty()
            activeSubs.map { info ->
                RealSimSlotInfo(
                    slotIndex = info.simSlotIndex,
                    subscriptionId = info.subscriptionId,
                    displayName = info.displayName?.toString().orEmpty().ifBlank { "شريحة ${info.simSlotIndex + 1}" },
                    carrierName = info.carrierName?.toString().orEmpty().ifBlank { "الشبكة ${info.simSlotIndex + 1}" },
                    number = info.number.orEmpty()
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Executes a real network USSD request directly on the device SIM card using Android's
     * TelephonyManager.sendUssdRequest (API 26+) and returns the live carrier response text.
     */
    @SuppressLint("MissingPermission")
    fun executeLiveUssdOnSim(
        context: Context,
        ussdCode: String,
        simSlotIndex: Int = -1,
        onSuccess: (RealUssdParsedBalance) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasCallPhonePermission(context)) {
            onError("يرجى منح إذن الهاتف (CALL_PHONE) لقراءة رد الشبكة مباشرة من الشريحة")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onError("إصدار أندرويد الحالي يتطلب فتح لوحة الاتصال لتنفيذ كود USSD")
            return
        }

        try {
            var telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (telephonyManager == null) {
                onError("تعذر الوصول إلى خدمة الشريحة (TelephonyManager) على هذا الجهاز")
                return
            }

            if (simSlotIndex >= 0 && hasReadPhoneStatePermission(context)) {
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val activeList = subManager?.activeSubscriptionInfoList
                    val matchingSub = activeList?.firstOrNull { it.simSlotIndex == simSlotIndex }
                        ?: activeList?.getOrNull(simSlotIndex)
                    if (matchingSub != null) {
                        telephonyManager = telephonyManager.createForSubscriptionId(matchingSub.subscriptionId)
                    }
                } catch (_: Throwable) {
                }
            }

            val mainHandler = Handler(Looper.getMainLooper())
            telephonyManager.sendUssdRequest(
                ussdCode,
                object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        response: CharSequence?
                    ) {
                        val rawText = response?.toString()?.trim().orEmpty()
                        if (rawText.isEmpty()) {
                            onError("استجابت الشبكة برد فارغ للكود $ussdCode")
                        } else {
                            onSuccess(parseCarrierUssdResponse(rawText))
                        }
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager?,
                        request: String?,
                        failureCode: Int
                    ) {
                        val reason = when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL ->
                                "خدمة USSD غير متوفرة حالياً على الشريحة المختارة أو لا توجد تغطية للشبكة. يمكنك استخدام زر الاستعلام عبر رسالة SMS (181) أو قراءة صندوق الوارد."
                            TelephonyManager.USSD_RETURN_FAILURE ->
                                "رفضت الشبكة تنفيذ الكود $ussdCode على الشريحة رقم ${simSlotIndex + 1} (تأكد من اختيار الشريحة الصحيحة للشبكة أو جرب الاستعلام عبر رسالة 181)."
                            else ->
                                "تعذر استلام رد USSD المباشر من الشريحة (رمز الخطأ: $failureCode). جرب التبديل إلى الشريحة الأخرى أو الاستعلام برسالة 181."
                        }
                        onError(reason)
                    }
                },
                mainHandler
            )
        } catch (t: Throwable) {
            onError("حدث خطأ أثناء الاتصال بالشريحة: ${t.localizedMessage ?: "خطأ غير معروف"}")
        }
    }

    /**
     * Sends a real SMS inquiry directly via the device's SIM card (e.g. sending "رصيد" to 181 for Yemen Mobile)
     * and invokes callbacks when the SMS is transmitted to the mobile network.
     */
    @SuppressLint("MissingPermission")
    fun sendRealSmsInquiryOnSim(
        context: Context,
        destinationAddress: String,
        messageBody: String,
        simSlotIndex: Int = 0,
        onSentSuccess: () -> Unit,
        onSentError: (String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            onSentError("يرجى منح إذن إرسال واستلام الرسائل (SMS) لإرسال طلب الاستعلام واستلام رد الشبكة تلقائياً")
            return
        }

        try {
            var targetSubId = SmsManager.getDefaultSmsSubscriptionId()
            if (hasReadPhoneStatePermission(context)) {
                try {
                    val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                    val activeList = subManager?.activeSubscriptionInfoList
                    val matchingSub = activeList?.firstOrNull { it.simSlotIndex == simSlotIndex }
                        ?: activeList?.getOrNull(simSlotIndex)
                    if (matchingSub != null) {
                        targetSubId = matchingSub.subscriptionId
                    }
                } catch (_: Throwable) {
                }
            }

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val baseSms = context.getSystemService(SmsManager::class.java)
                if (targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && baseSms != null) {
                    baseSms.createForSubscriptionId(targetSubId)
                } else {
                    baseSms ?: SmsManager.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                if (targetSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    SmsManager.getSmsManagerForSubscriptionId(targetSubId)
                } else {
                    SmsManager.getDefault()
                }
            }

            val actionName = "com.example.ACTION_REAL_SMS_SENT_${System.currentTimeMillis()}"
            val mainHandler = Handler(Looper.getMainLooper())

            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    try {
                        context.applicationContext.unregisterReceiver(this)
                    } catch (_: Throwable) {
                    }
                    mainHandler.post {
                        if (resultCode == Activity.RESULT_OK) {
                            onSentSuccess()
                        } else {
                            onSentError("تعذر إرسال رسالة الاستعلام إلى $destinationAddress عبر الشريحة ${simSlotIndex + 1} (رمز: $resultCode)")
                        }
                    }
                }
            }

            val filter = IntentFilter(actionName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(sentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(sentReceiver, filter)
            }

            val sentIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                (System.currentTimeMillis() and 0xFFFF).toInt(),
                Intent(actionName).setPackage(context.packageName),
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )

            smsManager.sendTextMessage(
                destinationAddress,
                null,
                messageBody,
                sentIntent,
                null
            )
        } catch (t: Throwable) {
            onSentError("فشل إرسال الرسالة النصية إلى $destinationAddress: ${t.localizedMessage ?: "خطأ في الشريحة"}")
        }
    }

    /**
     * Reads real SMS messages from the user's phone inbox (content://sms/inbox)
     * that came from Yemen Mobile (181, 121, YemenMobile), Sabafon (211), YOU (111), Yemen Net (114),
     * or contain real telecom balance / package / OTP keywords.
     */
    fun readRealCarrierSmsInbox(
        context: Context,
        maxCount: Int = 30
    ): Result<List<RealInboxSmsItem>> {
        if (!hasReadSmsPermission(context)) {
            return Result.failure(SecurityException("يرجى منح إذن قراءة الرسائل (READ_SMS) لاستخراج رسائل الرصيد الحقيقية من هاتفك"))
        }

        return try {
            val results = mutableListOf<RealInboxSmsItem>()
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val addrIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")
                val dateIdx = c.getColumnIndex("date")

                var scanned = 0
                while (c.moveToNext() && results.size < maxCount && scanned < 250) {
                    scanned++
                    val id = if (idIdx >= 0) c.getLong(idIdx) else System.currentTimeMillis() + scanned
                    val address = if (addrIdx >= 0) c.getString(addrIdx).orEmpty().trim() else ""
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty().trim() else ""
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else System.currentTimeMillis()

                    if (body.isNotBlank() && isRealTelecomSms(address, body)) {
                        val parsed = parseCarrierUssdResponse(body)
                        results.add(
                            RealInboxSmsItem(
                                id = id,
                                sender = address.ifBlank { "رسالة الشبكة" },
                                body = body,
                                timestampMillis = date,
                                parsed = parsed
                            )
                        )
                    }
                }
            }
            Result.success(results)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Checks whether an incoming or inbox SMS belongs to Yemen Mobile, Sabafon, YOU, Yemen Net, or a balance/OTP notification.
     */
    fun isRealTelecomSms(sender: String, body: String): Boolean {
        val s = sender.lowercase(Locale.ROOT)
        val b = normalizeArabicDigits(body).lowercase(Locale.ROOT)

        val carrierSenders = listOf(
            "181", "121", "180", "185", "199", "6000", "yemenmobile", "yemen mobile", "ym", "rialmobile",
            "211", "102", "202", "sabafon",
            "111", "120", "555", "you", "mtn",
            "114", "yemennet", "yemen 4g", "yemen4g", "adsl"
        )
        if (carrierSenders.any { s.contains(it) }) {
            return true
        }

        val telecomKeywords = listOf(
            "رصيدك", "الرصيد", "رصيد", "ريال", "ر.ي", "باقة", "باقات", "مزايا", "فورجي",
            "يمن موبايل", "سبأفون", "سبافون", "يمن نت", "جيجابايت", "ميجابايت", "صلاحية",
            "ينتهي", "سلفني", "رمز التحقق", "كود التحقق", "otp", "balance", "validity", "package"
        )
        return telecomKeywords.any { b.contains(it) }
    }

    /**
     * Extracts a real 4 to 8 digit OTP verification code from an incoming SMS message.
     */
    fun extractRealOtpFromSms(rawSms: String): String? {
        val normalized = normalizeArabicDigits(rawSms)
        val otpPatterns = listOf(
            Pattern.compile("(?:رمز|كود|التحقق|المرور|السري|code|otp|pin|verification)[^0-9]{0,20}\\b(\\d{4,8})\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(\\d{4,6})\\b")
        )
        for (pattern in otpPatterns) {
            val matcher = pattern.matcher(normalized)
            if (matcher.find()) {
                val candidate = matcher.group(1)
                if (!candidate.isNullOrBlank()) {
                    return candidate
                }
            }
        }
        return null
    }

    /**
     * Parses real Arabic or English USSD / SMS balance text from Yemen Mobile, Sabafon, YOU, or Yemen Net.
     */
    fun parseCarrierUssdResponse(rawMessage: String): RealUssdParsedBalance {
        val normalized = normalizeArabicDigits(rawMessage)

        // 1. Extract balance amount (looks for numbers near رصيد / balance / ريال / Rial / YR / YER)
        var balance: Double? = null
        val balancePatterns = listOf(
            Pattern.compile("(?:رصيدك الحالي هو|رصيدك الحالي|رصيدك الأساسي|الرصيد الأساسي|رصيدك|الرصيد|رصيد|balance is|balance|bal|credit)[^0-9]{0,25}([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("([0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:ريال يمني|ريال|ر\\.ي|ر\\.س|YR|YER|rial|ryal)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in balancePatterns) {
            val matcher = pattern.matcher(normalized)
            if (matcher.find()) {
                val parsed = matcher.group(1)?.toDoubleOrNull()
                if (parsed != null) {
                    balance = parsed
                    break
                }
            }
        }

        // Fallback: if message is short USSD balance response and no explicit keyword matched
        if (balance == null) {
            val genericNumber = Pattern.compile("\\b([0-9]{2,7}(?:\\.[0-9]{1,2})?)\\b").matcher(normalized)
            if (genericNumber.find()) {
                val candidate = genericNumber.group(1)?.toDoubleOrNull()
                if (candidate != null && candidate !in listOf(181.0, 121.0, 114.0, 211.0, 111.0, 2025.0, 2026.0, 2027.0)) {
                    balance = candidate
                }
            }
        }

        // 2. Extract loan/debt amount (سلفني / قرض / مديونية) if present
        var loan: Double? = null
        val loanMatcher = Pattern.compile("(?:سلفة|سلفني|مديونية|قرض|متأخرات|loan|debt)[^0-9]{0,20}([0-9]+(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(normalized)
        if (loanMatcher.find()) {
            loan = loanMatcher.group(1)?.toDoubleOrNull()
        }

        // 3. Extract expiry date (e.g., 2026/11/25 or 25-11-2026 or 25/11/2026)
        var expiry: String? = null
        val dateMatcher = Pattern.compile("(\\d{4}[/-]\\d{1,2}[/-]\\d{1,2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})").matcher(normalized)
        if (dateMatcher.find()) {
            expiry = dateMatcher.group(1)
        }

        // 4. Extract MB / GB if present
        var dataMb: Int? = null
        val gbMatcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:جيجابايت|جيجا بايت|جيجا|غيغا|GB|G\\.B|Giga)", Pattern.CASE_INSENSITIVE).matcher(normalized)
        val mbMatcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:ميجابايت|ميجا بايت|ميجا|ميغا|MB|M\\.B|Mega)", Pattern.CASE_INSENSITIVE).matcher(normalized)
        if (gbMatcher.find()) {
            val gb = gbMatcher.group(1)?.toDoubleOrNull()
            if (gb != null) dataMb = (gb * 1024).toInt()
        } else if (mbMatcher.find()) {
            dataMb = mbMatcher.group(1)?.toDoubleOrNull()?.toInt()
        }

        // 5. Extract Minutes if present
        var minutes: Int? = null
        val minMatcher = Pattern.compile("([0-9]+)\\s*(?:دقيقة|دقائق|دقيقه|min|mins|minute)", Pattern.CASE_INSENSITIVE).matcher(normalized)
        if (minMatcher.find()) {
            minutes = minMatcher.group(1)?.toIntOrNull()
        }

        // 6. Extract SMS if present
        var sms: Int? = null
        val smsMatcher = Pattern.compile("([0-9]+)\\s*(?:رسالة|رسائل|رساله|sms|msg)", Pattern.CASE_INSENSITIVE).matcher(normalized)
        if (smsMatcher.find()) {
            sms = smsMatcher.group(1)?.toIntOrNull()
        }

        // 7. Extract Package Title if mentioned
        var pkgTitle: String? = null
        val pkgMatcher = Pattern.compile("(باقة\\s+[^\\n.,،:]{3,30}|مزايا\\s+[^\\n.,،:]{2,25}|فورجي\\s+[^\\n.,،:]{2,25})").matcher(normalized)
        if (pkgMatcher.find()) {
            pkgTitle = pkgMatcher.group(1)?.trim()
        }

        return RealUssdParsedBalance(
            rawUssdResponse = rawMessage.trim(),
            extractedBalanceRial = balance,
            extractedLoanRial = loan,
            extractedExpiryDate = expiry,
            extractedDataMb = dataMb,
            extractedMinutes = minutes,
            extractedSms = sms,
            extractedPackageTitle = pkgTitle
        )
    }

    /**
     * Performs a real HTTP request to the configured portal endpoint
     * without generating any fake/mock numbers.
     */
    fun executeRealHttpPost(
        endpointUrl: String,
        jsonPayload: String,
        headers: Map<String, String> = emptyMap()
    ): Result<String> {
        return try {
            val url = URL(endpointUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json, text/plain, */*")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } else {
                ""
            }

            if (code in 200..299 && body.isNotBlank()) {
                Result.success(body)
            } else {
                Result.failure(
                    IllegalStateException("استجاب الخادم برمز ($code): ${body.take(180).ifBlank { "تعذر الوصول لبوابة الاستعلام" }}")
                )
            }
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun normalizeArabicDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                '٫', ',' -> sb.append('.')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun todayDateFormatted(): String {
        return SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
    }
}
