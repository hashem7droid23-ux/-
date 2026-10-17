package com.example

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QueryRecordItem(
    val id: Long,
    val phoneNumber: String,
    val carrierId: String,
    val subscriberLabel: String,
    val balanceRial: Double,
    val loanDebtRial: Double,
    val lineStatus: String,
    val expiryDate: String,
    val activePackageName: String,
    val remainingDataMb: Int,
    val totalDataMb: Int,
    val remainingMinutes: Int,
    val totalMinutes: Int,
    val remainingSms: Int,
    val totalSms: Int,
    val verificationRef: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)

data class OtpSessionItem(
    val phoneNumber: String,
    val carrierId: String,
    val sessionToken: String,
    val generatedOtpCode: String,
    val expiresAtMillis: Long
)

class LocalTelecomStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("yemen_telecom_real_v3", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }

    fun getDefaultCarrierId(): String = prefs.getString("default_carrier_id", "YEMEN_MOBILE") ?: "YEMEN_MOBILE"

    fun setDefaultCarrierId(carrierId: String) {
        prefs.edit().putString("default_carrier_id", carrierId).apply()
    }

    fun getSelectedSimSlot(): Int = prefs.getInt("selected_sim_slot", 0)

    fun setSelectedSimSlot(slot: Int) {
        prefs.edit().putInt("selected_sim_slot", slot).apply()
    }

    fun getPortalBaseUrl(): String =
        prefs.getString("portal_base_url", "https://selfcare.yemenmobile.com.ye") ?: "https://selfcare.yemenmobile.com.ye"

    fun setPortalBaseUrl(url: String) {
        prefs.edit().putString("portal_base_url", url.trim()).apply()
    }

    fun loadRecords(): List<QueryRecordItem> {
        val rawJson = prefs.getString("saved_records_json", null)
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<QueryRecordItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QueryRecordItem(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        phoneNumber = obj.optString("phoneNumber", ""),
                        carrierId = obj.optString("carrierId", "YEMEN_MOBILE"),
                        subscriberLabel = obj.optString("subscriberLabel", "مشترك"),
                        balanceRial = obj.optDouble("balanceRial", 0.0),
                        loanDebtRial = obj.optDouble("loanDebtRial", 0.0),
                        lineStatus = obj.optString("lineStatus", ""),
                        expiryDate = obj.optString("expiryDate", "-"),
                        activePackageName = obj.optString("activePackageName", ""),
                        remainingDataMb = obj.optInt("remainingDataMb", 0),
                        totalDataMb = obj.optInt("totalDataMb", 0),
                        remainingMinutes = obj.optInt("remainingMinutes", 0),
                        totalMinutes = obj.optInt("totalMinutes", 0),
                        remainingSms = obj.optInt("remainingSms", 0),
                        totalSms = obj.optInt("totalSms", 0),
                        verificationRef = obj.optString("verificationRef", ""),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
            list.sortedByDescending { it.timestampMillis }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun saveRecords(records: List<QueryRecordItem>) {
        try {
            val arr = JSONArray()
            records.forEach { rec ->
                val obj = JSONObject().apply {
                    put("id", rec.id)
                    put("phoneNumber", rec.phoneNumber)
                    put("carrierId", rec.carrierId)
                    put("subscriberLabel", rec.subscriberLabel)
                    put("balanceRial", rec.balanceRial)
                    put("loanDebtRial", rec.loanDebtRial)
                    put("lineStatus", rec.lineStatus)
                    put("expiryDate", rec.expiryDate)
                    put("activePackageName", rec.activePackageName)
                    put("remainingDataMb", rec.remainingDataMb)
                    put("totalDataMb", rec.totalDataMb)
                    put("remainingMinutes", rec.remainingMinutes)
                    put("totalMinutes", rec.totalMinutes)
                    put("remainingSms", rec.remainingSms)
                    put("totalSms", rec.totalSms)
                    put("verificationRef", rec.verificationRef)
                    put("timestampMillis", rec.timestampMillis)
                    put("isFavorite", rec.isFavorite)
                }
                arr.put(obj)
            }
            prefs.edit().putString("saved_records_json", arr.toString()).apply()
        } catch (_: Throwable) {
        }
    }

    companion object {
        fun normalizeYemenPhone(raw: String): String {
            val digits = RealTelecomEngine.normalizeArabicDigits(raw).filter { it.isDigit() }
            return when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") && digits.length > 9 -> digits.removePrefix("967")
                else -> digits
            }
        }

        fun createRecordFromRealUssd(
            phoneNumber: String,
            carrierId: String,
            carrierArabicName: String,
            subscriberLabel: String,
            ussdCode: String,
            parsed: RealUssdParsedBalance,
            timestampMillis: Long = System.currentTimeMillis()
        ): QueryRecordItem {
            val cleanPhone = normalizeYemenPhone(phoneNumber).ifEmpty { "الشريحة المباشرة" }
            val label = subscriberLabel.trim().ifEmpty {
                parsed.extractedPackageTitle ?: "استعلام حقيقي ($carrierArabicName)"
            }
            val dataMb = parsed.extractedDataMb ?: 0
            val minutes = parsed.extractedMinutes ?: 0
            val sms = parsed.extractedSms ?: 0

            return QueryRecordItem(
                id = System.currentTimeMillis(),
                phoneNumber = cleanPhone,
                carrierId = carrierId,
                subscriberLabel = label,
                balanceRial = parsed.extractedBalanceRial ?: 0.0,
                loanDebtRial = parsed.extractedLoanRial ?: 0.0,
                lineStatus = "رد الشبكة الحقيقي ($ussdCode)",
                expiryDate = parsed.extractedExpiryDate ?: RealTelecomEngine.todayDateFormatted(),
                activePackageName = parsed.rawUssdResponse,
                remainingDataMb = dataMb,
                totalDataMb = if (dataMb > 0) dataMb else 0,
                remainingMinutes = minutes,
                totalMinutes = if (minutes > 0) minutes else 0,
                remainingSms = sms,
                totalSms = if (sms > 0) sms else 0,
                verificationRef = "REAL-${System.currentTimeMillis().toString().takeLast(6)}",
                timestampMillis = timestampMillis,
                isFavorite = false
            )
        }

        fun formatExportText(
            records: List<QueryRecordItem>,
            carrierNameResolver: (String) -> String,
            asCsv: Boolean
        ): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            if (asCsv) {
                val sb = StringBuilder()
                sb.append("رقم الهاتف,الشبكة,اسم المشترك,الرصيد المستخرج (ريال),رد الشبكة الفعلي,تاريخ الصلاحية,وقت الاستعلام\n")
                records.forEach { rec ->
                    val cName = carrierNameResolver(rec.carrierId)
                    val dStr = dateFormat.format(Date(rec.timestampMillis))
                    val sanitizedResponse = rec.activePackageName.replace("\n", " ").replace(",", " - ")
                    sb.append("${rec.phoneNumber},$cName,${rec.subscriberLabel},${rec.balanceRial.toInt()},$sanitizedResponse,${rec.expiryDate},$dStr\n")
                }
                return sb.toString()
            } else {
                val sb = StringBuilder()
                sb.append("━━━ تقرير سجل الاستعلام الحقيقي للشبكات اليمنية ━━━\n")
                sb.append("إجمالي العمليات المسجلة: ${records.size}\n\n")
                records.forEachIndexed { idx, rec ->
                    val cName = carrierNameResolver(rec.carrierId)
                    val dStr = dateFormat.format(Date(rec.timestampMillis))
                    sb.append("${idx + 1}. الرقم/الشريحة: ${rec.phoneNumber} ($cName)\n")
                    sb.append("   • الوصف: ${rec.subscriberLabel}\n")
                    if (rec.balanceRial > 0.0) {
                        sb.append("   • الرصيد المستخرج: ${rec.balanceRial} ريال يمني\n")
                    }
                    sb.append("   • رد الشبكة النصي الكامل:\n     \"${rec.activePackageName}\"\n")
                    sb.append("   • التاريخ: $dStr\n")
                    sb.append("──────────────────────────────────────\n")
                }
                return sb.toString()
            }
        }
    }
}
