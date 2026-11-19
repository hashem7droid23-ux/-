package com.example.data

enum class CarrierType(
    val arabicName: String,
    val prefixes: List<String>
) {
    YEMEN_MOBILE(
        arabicName = "يمن موبايل",
        prefixes = listOf("77", "78")
    ),
    SABAFON(
        arabicName = "سبأفون",
        prefixes = listOf("71")
    );

    companion object {
        fun normalizeDigits(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    in '٠'..'٩' -> sb.append((ch - '٠' + '0'.code).toChar())
                    in '۰'..'۹' -> sb.append((ch - '۰' + '0'.code).toChar())
                    in '0'..'9' -> sb.append(ch)
                }
            }
            val raw = sb.toString()
            return when {
                raw.startsWith("00967") -> raw.removePrefix("00967")
                raw.startsWith("967") && raw.length > 9 -> raw.removePrefix("967")
                else -> raw
            }
        }

        fun detectFromPhone(input: String): CarrierType {
            val digits = normalizeDigits(input)
            return when {
                digits.startsWith("71") -> SABAFON
                digits.startsWith("77") || digits.startsWith("78") -> YEMEN_MOBILE
                else -> YEMEN_MOBILE
            }
        }

        fun isValidYemenNumber(input: String): Boolean {
            val digits = normalizeDigits(input)
            return digits.length == 9 && (
                digits.startsWith("77") ||
                    digits.startsWith("78") ||
                    digits.startsWith("71")
                )
        }
    }
}

enum class AppScreen {
    QUERY,
    OTP,
    RESULT
}

data class ActivePackage(
    val packageName: String,
    val remaining: String,
    val expiryDate: String = "",
    val price: String = ""
)

data class BalanceInquiryResult(
    val carrier: CarrierType,
    val phoneNumber: String,
    val subscriberName: String,
    val payingMethod: String,
    val status: String,
    val balanceAmount: Double,
    val offers: List<ActivePackage>,
    val isDemo: Boolean = false,
    val fetchedAtMillis: Long = System.currentTimeMillis()
) {
    val payingMethodArabic: String
        get() = when (payingMethod.trim().lowercase()) {
            "prepaid", "pre-paid", "مسبق الدفع" -> "مسبق الدفع"
            "postpaid", "post-paid", "فوترة", "آجل الدفع" -> "فوترة"
            "" -> "مسبق الدفع"
            else -> payingMethod
        }
}
