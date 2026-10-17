package com.example.data

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.RoyalMaroon
import com.example.ui.theme.SabafonAccentColor
import com.example.ui.theme.SabafonBrandColor
import com.example.ui.theme.YemenNetAccentColor
import com.example.ui.theme.YemenNetBrandColor
import com.example.ui.theme.YouAccentColor
import com.example.ui.theme.YouBrandColor

enum class TelecomCarrier(
    val id: String,
    val arabicName: String,
    val shortTag: String,
    val prefixes: List<String>,
    val primaryColor: Color,
    val accentColor: Color,
    val balanceUssd: String,
    val packagesUssd: String,
    val customerServiceNumber: String,
    val defaultTaxRatePercent: Double
) {
    YEMEN_MOBILE(
        id = "YEMEN_MOBILE",
        arabicName = "يمن موبايل",
        shortTag = "YM 4G/VoLTE",
        prefixes = listOf("77", "78"),
        primaryColor = RoyalMaroon,
        accentColor = ImperialGold,
        balanceUssd = "*181#",
        packagesUssd = "*121#",
        customerServiceNumber = "121",
        defaultTaxRatePercent = 17.5
    ),
    SABAFON(
        id = "SABAFON",
        arabicName = "سبأفون",
        shortTag = "Sabafon GSM/4G",
        prefixes = listOf("71"),
        primaryColor = SabafonBrandColor,
        accentColor = SabafonAccentColor,
        balanceUssd = "*102#",
        packagesUssd = "*121#",
        customerServiceNumber = "211",
        defaultTaxRatePercent = 17.5
    ),
    YOU(
        id = "YOU",
        arabicName = "YOU يو",
        shortTag = "YOU 4G",
        prefixes = listOf("73"),
        primaryColor = YouBrandColor,
        accentColor = YouAccentColor,
        balanceUssd = "*120#",
        packagesUssd = "*555#",
        customerServiceNumber = "111",
        defaultTaxRatePercent = 17.5
    ),
    YEMEN_NET(
        id = "YEMEN_NET",
        arabicName = "يمن نت و 4G",
        shortTag = "ADSL & Yemen 4G",
        prefixes = listOf("01", "02", "03", "04", "05", "06", "07", "10"),
        primaryColor = YemenNetBrandColor,
        accentColor = YemenNetAccentColor,
        balanceUssd = "*114#",
        packagesUssd = "*114*1#",
        customerServiceNumber = "114",
        defaultTaxRatePercent = 10.0
    );

    companion object {
        fun detectFromPhone(input: String): TelecomCarrier? {
            val digits = input.filter { it.isDigit() }
            val normalized = when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") && digits.length > 9 -> digits.removePrefix("967")
                else -> digits
            }
            if (normalized.length < 2) return null
            val prefix2 = normalized.take(2)
            return entries.firstOrNull { carrier ->
                carrier.prefixes.any { prefix2.startsWith(it) }
            }
        }

        fun fromId(id: String): TelecomCarrier {
            return entries.firstOrNull { it.id == id } ?: YEMEN_MOBILE
        }
    }
}

enum class PackageCategory(val arabicLabel: String) {
    ALL("الكل"),
    MAZAYA("باقات مزايا"),
    INTERNET_4G("إنترنت 4G"),
    VOICE_SMS("مكالمات ورسائل"),
    ADSL_HOME("إنترنت منزلي")
}

data class TelecomPackage(
    val id: String,
    val carrier: TelecomCarrier,
    val category: PackageCategory,
    val title: String,
    val subtitle: String,
    val priceYemeniRial: Int,
    val netCostWithTax: Int,
    val validityDays: Int,
    val dataQuota: String,
    val voiceQuota: String,
    val smsQuota: String,
    val activationUssd: String,
    val cancelUssd: String,
    val isPopular: Boolean = false
)

data class UssdQuickCode(
    val id: String,
    val carrier: TelecomCarrier,
    val title: String,
    val description: String,
    val code: String,
    val categoryTag: String
)

object TelecomCatalogData {
    val packages: List<TelecomPackage> = listOf(
        // Yemen Mobile Packages
        TelecomPackage(
            id = "ym_mazaya_max_monthly",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.MAZAYA,
            title = "باقة مزايا ماكس الشهرية",
            subtitle = "الباقة الشاملة الأكثر طلباً للمكالمات والإنترنت",
            priceYemeniRial = 3000,
            netCostWithTax = 3525,
            validityDays = 30,
            dataQuota = "5 جيجابايت",
            voiceQuota = "1000 دقيقة",
            smsQuota = "500 رسالة",
            activationUssd = "*121*3*1#",
            cancelUssd = "*121*3*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "ym_mazaya_plus_monthly",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.MAZAYA,
            title = "باقة مزايا بلس الشهرية",
            subtitle = "توازن مثالي بين الإنترنت والاتصال داخل الشبكة",
            priceYemeniRial = 1500,
            netCostWithTax = 1765,
            validityDays = 30,
            dataQuota = "2 جيجابايت",
            voiceQuota = "500 دقيقة",
            smsQuota = "300 رسالة",
            activationUssd = "*121*1*1#",
            cancelUssd = "*121*1*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "ym_mazaya_weekly",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.MAZAYA,
            title = "باقة مزايا الأسبوعية",
            subtitle = "باقة توفيرية لمدة 7 أيام",
            priceYemeniRial = 600,
            netCostWithTax = 705,
            validityDays = 7,
            dataQuota = "600 ميجابايت",
            voiceQuota = "250 دقيقة",
            smsQuota = "150 رسالة",
            activationUssd = "*121*2*1#",
            cancelUssd = "*121*2*0#"
        ),
        TelecomPackage(
            id = "ym_4g_12gb",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.INTERNET_4G,
            title = "باقة فورجي 12 جيجابايت",
            subtitle = "سرعة فائقة عبر شبكة الجيل الرابع VoLTE",
            priceYemeniRial = 4200,
            netCostWithTax = 4935,
            validityDays = 30,
            dataQuota = "12 جيجابايت",
            voiceQuota = "300 دقيقة",
            smsQuota = "300 رسالة",
            activationUssd = "*121*4*2#",
            cancelUssd = "*121*4*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "ym_4g_25gb",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.INTERNET_4G,
            title = "باقة فورجي 25 جيجابايت",
            subtitle = "مخصصة للاستخدام المكثف والبث المرئي",
            priceYemeniRial = 7500,
            netCostWithTax = 8815,
            validityDays = 30,
            dataQuota = "25 جيجابايت",
            voiceQuota = "500 دقيقة",
            smsQuota = "500 رسالة",
            activationUssd = "*121*4*4#",
            cancelUssd = "*121*4*0#"
        ),
        TelecomPackage(
            id = "ym_voice_gold",
            carrier = TelecomCarrier.YEMEN_MOBILE,
            category = PackageCategory.VOICE_SMS,
            title = "باقة الاتصال الذهبية",
            subtitle = "دقائق اتصال وفيرة لجميع أرقام يمن موبايل",
            priceYemeniRial = 1000,
            netCostWithTax = 1175,
            validityDays = 30,
            dataQuota = "بدون إنترنت",
            voiceQuota = "650 دقيقة",
            smsQuota = "250 رسالة",
            activationUssd = "*121*6*1#",
            cancelUssd = "*121*6*0#"
        ),

        // Sabafon Packages
        TelecomPackage(
            id = "sf_super_4g_8gb",
            carrier = TelecomCarrier.SABAFON,
            category = PackageCategory.INTERNET_4G,
            title = "سبأفون سوبر 4G - 8 جيجا",
            subtitle = "إنترنت الجيل الرابع عالي السرعة مع دقائق مجانية",
            priceYemeniRial = 2800,
            netCostWithTax = 3290,
            validityDays = 30,
            dataQuota = "8 جيجابايت",
            voiceQuota = "400 دقيقة",
            smsQuota = "200 رسالة",
            activationUssd = "*121*4*1#",
            cancelUssd = "*121*4*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "sf_yalla_weekly",
            carrier = TelecomCarrier.SABAFON,
            category = PackageCategory.MAZAYA,
            title = "باقة يلا الأسبوعية",
            subtitle = "باقة تواصل اجتماعي ومكالمات أسبوعية",
            priceYemeniRial = 750,
            netCostWithTax = 880,
            validityDays = 7,
            dataQuota = "1.5 جيجابايت",
            voiceQuota = "200 دقيقة",
            smsQuota = "100 رسالة",
            activationUssd = "*121*2*2#",
            cancelUssd = "*121*2*0#"
        ),

        // YOU Packages
        TelecomPackage(
            id = "you_smart_10gb",
            carrier = TelecomCarrier.YOU,
            category = PackageCategory.INTERNET_4G,
            title = "باقة YOU سمارت 10 جيجا",
            subtitle = "تصفح بلا حدود على تطبيقات التواصل + رصيد بيانات",
            priceYemeniRial = 3100,
            netCostWithTax = 3645,
            validityDays = 30,
            dataQuota = "10 جيجابايت",
            voiceQuota = "500 دقيقة",
            smsQuota = "300 رسالة",
            activationUssd = "*555*1*1#",
            cancelUssd = "*555*1*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "you_mix_monthly",
            carrier = TelecomCarrier.YOU,
            category = PackageCategory.MAZAYA,
            title = "باقة مكس بلس الشهرية",
            subtitle = "مكالمات وإنترنت ورسائل لكل الشبكات",
            priceYemeniRial = 1800,
            netCostWithTax = 2115,
            validityDays = 30,
            dataQuota = "3.5 جيجابايت",
            voiceQuota = "450 دقيقة",
            smsQuota = "250 رسالة",
            activationUssd = "*555*2*1#",
            cancelUssd = "*555*2*0#"
        ),

        // Yemen Net & Yemen 4G Packages
        TelecomPackage(
            id = "yn_4g_home_50gb",
            carrier = TelecomCarrier.YEMEN_NET,
            category = PackageCategory.ADSL_HOME,
            title = "يمن 4G المنزلي - 50 جيجا",
            subtitle = "سرعة تصل إلى 20 ميجابت للمنازل والمكاتب",
            priceYemeniRial = 8400,
            netCostWithTax = 9240,
            validityDays = 30,
            dataQuota = "50 جيجابايت",
            voiceQuota = "خط بيانات",
            smsQuota = "إشعارات النظام",
            activationUssd = "*114*1#",
            cancelUssd = "*114*0#",
            isPopular = true
        ),
        TelecomPackage(
            id = "yn_adsl_gold_100gb",
            carrier = TelecomCarrier.YEMEN_NET,
            category = PackageCategory.ADSL_HOME,
            title = "سوبر نت ADSL - 100 جيجا",
            subtitle = "باقة الإنترنت الأرضي عالية السعة",
            priceYemeniRial = 9500,
            netCostWithTax = 10450,
            validityDays = 30,
            dataQuota = "100 جيجابايت",
            voiceQuota = "هاتف ثابت",
            smsQuota = "-",
            activationUssd = "*114*2#",
            cancelUssd = "*114*0#"
        )
    )

    val quickUssdCodes: List<UssdQuickCode> = listOf(
        // Yemen Mobile
        UssdQuickCode("ym_bal", TelecomCarrier.YEMEN_MOBILE, "الاستعلام عن الرصيد الأساسي", "معرفة الرصيد المتبقي وتاريخ انتهاء الصلاحية", "*181#", "رصيد"),
        UssdQuickCode("ym_pkg", TelecomCarrier.YEMEN_MOBILE, "قائمة باقات يمن موبايل", "الدخول الموحد لجميع باقات مزايا وفورجي", "*121#", "باقات"),
        UssdQuickCode("ym_volte", TelecomCarrier.YEMEN_MOBILE, "تفعيل خدمة VoLTE والـ 4G", "فحص وتفعيل الاتصال عالي الجودة عبر الجيل الرابع", "*121*4#", "خدمات"),
        UssdQuickCode("ym_loan", TelecomCarrier.YEMEN_MOBILE, "خدمة سلفني (الرصيد الطارئ)", "طلب رصيد إضافي عند نفاد الرصيد الأساسي", "*180#", "طوارئ"),
        UssdQuickCode("ym_num", TelecomCarrier.YEMEN_MOBILE, "معرفة رقم شريحتك", "إظهار رقم الهاتف الخاص بالشريحة الحالية", "*199#", "معلومات"),
        UssdQuickCode("ym_trans", TelecomCarrier.YEMEN_MOBILE, "تحويل الرصيد لمشترك آخر", "تحويل مبلغ من رصيدك إلى رقم يمن موبايل آخر", "*185#", "تحويل"),

        // Sabafon
        UssdQuickCode("sf_bal", TelecomCarrier.SABAFON, "استعلام رصيد سبأفون", "عرض الرصيد الأساسي والوحدات المتبقية", "*102#", "رصيد"),
        UssdQuickCode("sf_pkg", TelecomCarrier.SABAFON, "باقات وعروض سبأفون", "قائمة التفعيل السريع لباقات سبأفون 4G", "*121#", "باقات"),
        UssdQuickCode("sf_loan", TelecomCarrier.SABAFON, "خدمة الرصيد الإضافي سبأفون", "استلاف رصيد فوري عند الحاجة", "*202#", "طوارئ"),

        // YOU
        UssdQuickCode("you_bal", TelecomCarrier.YOU, "استعلام رصيد YOU", "معرفة الرصيد المتبقي وصلاحية الخط", "*120#", "رصيد"),
        UssdQuickCode("you_pkg", TelecomCarrier.YOU, "باقات YOU الذكية", "عروض الإنترنت والمكالمات الشهرية والأسبوعية", "*555#", "باقات"),
        UssdQuickCode("you_my_num", TelecomCarrier.YOU, "معرفة رقم خط YOU", "عرض رقم الشريحة على الشاشة", "*222#", "معلومات"),

        // Yemen Net
        UssdQuickCode("yn_bal", TelecomCarrier.YEMEN_NET, "استعلام رصيد يمن نت و4G", "فحص الرصيد المتبقي لخطوط الإنترنت الأرضي و4G", "*114#", "رصيد"),
        UssdQuickCode("yn_support", TelecomCarrier.YEMEN_NET, "الدعم الفني للاتصالات اليمنية", "التواصل المباشر مع مركز خدمة المشتركين", "114", "دعم")
    )
}
