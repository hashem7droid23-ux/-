package com.example

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
        context.applicationContext.getSharedPreferences("yemen_telecom_prefs_v2", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setDarkTheme(enabled: Boolean) {
        prefs.edit().putBoolean("dark_theme", enabled).apply()
    }

    fun getDefaultCarrierId(): String = prefs.getString("default_carrier_id", "YEMEN_MOBILE") ?: "YEMEN_MOBILE"

    fun setDefaultCarrierId(carrierId: String) {
        prefs.edit().putString("default_carrier_id", carrierId).apply()
    }

    fun loadRecords(): List<QueryRecordItem> {
        val rawJson = prefs.getString("saved_records_json", null)
        if (rawJson.isNullOrBlank()) {
            val sample = listOf(createDefaultSampleRecord())
            saveRecords(sample)
            return sample
        }
        return try {
            val arr = JSONArray(rawJson)
            val list = mutableListOf<QueryRecordItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    QueryRecordItem(
                        id = obj.optLong("id", System.currentTimeMillis() + i),
                        phoneNumber = obj.optString("phoneNumber", "777889900"),
                        carrierId = obj.optString("carrierId", "YEMEN_MOBILE"),
                        subscriberLabel = obj.optString("subscriberLabel", "مشترك يمن موبايل"),
                        balanceRial = obj.optDouble("balanceRial", 4850.0),
                        loanDebtRial = obj.optDouble("loanDebtRial", 0.0),
                        lineStatus = obj.optString("lineStatus", "نشط - 4G VoLTE"),
                        expiryDate = obj.optString("expiryDate", "2026/11/15"),
                        activePackageName = obj.optString("activePackageName", "باقة مزايا ماكس الشهرية"),
                        remainingDataMb = obj.optInt("remainingDataMb", 3840),
                        totalDataMb = obj.optInt("totalDataMb", 5120),
                        remainingMinutes = obj.optInt("remainingMinutes", 740),
                        totalMinutes = obj.optInt("totalMinutes", 1000),
                        remainingSms = obj.optInt("remainingSms", 410),
                        totalSms = obj.optInt("totalSms", 500),
                        verificationRef = obj.optString("verificationRef", "YM-9021"),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        isFavorite = obj.optBoolean("isFavorite", false)
                    )
                )
            }
            list.sortedByDescending { it.timestampMillis }
        } catch (_: Throwable) {
            listOf(createDefaultSampleRecord())
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

    private fun createDefaultSampleRecord(): QueryRecordItem {
        return QueryRecordItem(
            id = 1001L,
            phoneNumber = "777889900",
            carrierId = "YEMEN_MOBILE",
            subscriberLabel = "الرقم الرئيسي - يمن موبايل",
            balanceRial = 4850.0,
            loanDebtRial = 0.0,
            lineStatus = "نشط - 4G VoLTE",
            expiryDate = "2026/11/15",
            activePackageName = "باقة مزايا ماكس الشهرية",
            remainingDataMb = 3840,
            totalDataMb = 5120,
            remainingMinutes = 740,
            totalMinutes = 1000,
            remainingSms = 410,
            totalSms = 500,
            verificationRef = "YM-INIT-9021",
            timestampMillis = System.currentTimeMillis() - 3600_000L,
            isFavorite = true
        )
    }

    companion object {
        fun normalizeYemenPhone(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            return when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") && digits.length > 9 -> digits.removePrefix("967")
                else -> digits
            }
        }

        fun buildVerifiedRecord(
            session: OtpSessionItem,
            carrierName: String,
            chosenPackageTitle: String,
            dataQuotaText: String,
            voiceQuotaText: String,
            enteredOtp: String,
            customLabel: String
        ): QueryRecordItem {
            val cleanPhone = session.phoneNumber
            val seed = abs(cleanPhone.hashCode())
            val baseBalance = ((seed % 145) * 50 + 450).toDouble()
            val loanDebt = if (seed % 4 == 0) 350.0 else 0.0

            val totalDataMb = when {
                dataQuotaText.contains("100") -> 102400
                dataQuotaText.contains("50") -> 51200
                dataQuotaText.contains("25") -> 25600
                dataQuotaText.contains("12") -> 12288
                dataQuotaText.contains("10") -> 10240
                dataQuotaText.contains("8") -> 8192
                dataQuotaText.contains("5") -> 5120
                dataQuotaText.contains("3.5") -> 3584
                dataQuotaText.contains("2") -> 2048
                dataQuotaText.contains("1.5") -> 1536
                dataQuotaText.contains("600") -> 600
                else -> 1024
            }
            val ratio = 0.30 + ((seed % 60) / 100.0)
            val remainingDataMb = (totalDataMb * ratio).toInt().coerceIn(50, totalDataMb)

            val totalMinutes = when {
                voiceQuotaText.contains("1000") -> 1000
                voiceQuotaText.contains("650") -> 650
                voiceQuotaText.contains("500") -> 500
                voiceQuotaText.contains("450") -> 450
                voiceQuotaText.contains("400") -> 400
                voiceQuotaText.contains("300") -> 300
                else -> 250
            }
            val remainingMinutes = (totalMinutes * ratio).toInt().coerceIn(10, totalMinutes)
            val totalSms = 300
            val remainingSms = (totalSms * ratio).toInt().coerceIn(10, totalSms)

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, (seed % 24) + 5)
            val expiryStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(calendar.time)

            val label = customLabel.trim().ifEmpty {
                "مشترك $carrierName (${cleanPhone.takeLast(4)})"
            }

            return QueryRecordItem(
                id = System.currentTimeMillis(),
                phoneNumber = cleanPhone,
                carrierId = session.carrierId,
                subscriberLabel = label,
                balanceRial = baseBalance,
                loanDebtRial = loanDebt,
                lineStatus = "نشط - 4G VoLTE",
                expiryDate = expiryStr,
                activePackageName = chosenPackageTitle,
                remainingDataMb = remainingDataMb,
                totalDataMb = totalDataMb,
                remainingMinutes = remainingMinutes,
                totalMinutes = totalMinutes,
                remainingSms = remainingSms,
                totalSms = totalSms,
                verificationRef = "${session.sessionToken}-${enteredOtp.take(4)}",
                timestampMillis = System.currentTimeMillis(),
                isFavorite = false
            )
        }

        fun formatExportText(records: List<QueryRecordItem>, carrierNameResolver: (String) -> String, asCsv: Boolean): String {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            if (asCsv) {
                val sb = StringBuilder()
                sb.append("رقم الهاتف,الشبكة,اسم المشترك,الرصيد (ريال),الباقة الفعالة,البيانات المتبقية (MB),الدقائق المتبقية,تاريخ الانتهاء,وقت الاستعلام\n")
                records.forEach { rec ->
                    val cName = carrierNameResolver(rec.carrierId)
                    val dStr = dateFormat.format(Date(rec.timestampMillis))
                    sb.append("${rec.phoneNumber},$cName,${rec.subscriberLabel},${rec.balanceRial.toInt()},${rec.activePackageName},${rec.remainingDataMb},${rec.remainingMinutes},${rec.expiryDate},$dStr\n")
                }
                return sb.toString()
            } else {
                val sb = StringBuilder()
                sb.append("━━━ تقرير سجل رصيد يمن موبايل والشبكات اليمنية ━━━\n")
                sb.append("إجمالي العمليات المحفوظة: ${records.size}\n\n")
                records.forEachIndexed { idx, rec ->
                    val cName = carrierNameResolver(rec.carrierId)
                    val dStr = dateFormat.format(Date(rec.timestampMillis))
                    sb.append("${idx + 1}. الرقم: ${rec.phoneNumber} ($cName)\n")
                    sb.append("   • المشترك: ${rec.subscriberLabel}\n")
                    sb.append("   • الرصيد الأساسي: ${rec.balanceRial.toInt()} ريال يمني\n")
                    sb.append("   • الباقة: ${rec.activePackageName} (متبقي ${rec.remainingDataMb} ميجابايت / ${rec.remainingMinutes} دقيقة)\n")
                    sb.append("   • الصلاحية حتى: ${rec.expiryDate} | فحص بتاريخ: $dStr\n")
                    sb.append("──────────────────────────────────────\n")
                }
                return sb.toString()
            }
        }
    }
}
