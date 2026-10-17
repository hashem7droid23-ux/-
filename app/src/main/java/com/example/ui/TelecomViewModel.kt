package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.LocalTelecomStorage
import com.example.OtpSessionItem
import com.example.QueryRecordItem
import com.example.data.PackageCategory
import com.example.data.TelecomCarrier
import com.example.data.TelecomCatalogData
import com.example.data.TelecomPackage
import com.example.data.UssdQuickCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

enum class AppTab(val route: String, val titleAr: String) {
    QUERY("query", "الاستعلام"),
    PACKAGES("packages", "الباقات والأكواد"),
    CALCULATOR("calculator", "الحاسبة"),
    HISTORY("history", "السجل")
}

class TelecomViewModel(
    private val storage: LocalTelecomStorage
) : ViewModel() {

    var isDarkTheme by mutableStateOf(storage.isDarkTheme())
        private set

    var currentTab by mutableStateOf(AppTab.QUERY)
        private set

    var selectedCarrier by mutableStateOf(TelecomCarrier.fromId(storage.getDefaultCarrierId()))
        private set

    var phoneInput by mutableStateOf("777889900")
        private set

    var subscriberLabelInput by mutableStateOf("")
        private set

    var autoDetectedCarrier by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var statusBannerMessage by mutableStateOf<String?>(null)
        private set

    // OTP Dialog State
    var activeOtpSession by mutableStateOf<OtpSessionItem?>(null)
        private set

    var otpDigitsInput by mutableStateOf("")
        private set

    var otpRemainingSeconds by mutableIntStateOf(0)
        private set

    // Records & Result State
    var allRecords by mutableStateOf(storage.loadRecords())
        private set

    var latestResult by mutableStateOf<QueryRecordItem?>(allRecords.firstOrNull())
        private set

    // Packages & USSD Filter State
    var selectedPackageCategory by mutableStateOf(PackageCategory.ALL)
        private set

    var packageSearchQuery by mutableStateOf("")
        private set

    // Calculator State
    var calcAmountInput by mutableStateOf("3000")
        private set

    var calcIsGrossToNet by mutableStateOf(false)
        private set

    var calcDataGbInput by mutableStateOf("5")
        private set

    var calcDaysInput by mutableStateOf("30")
        private set

    // History Filter State
    var historySearchQuery by mutableStateOf("")
        private set

    var historyOnlyFavorites by mutableStateOf(false)
        private set

    var selectedCarrierFilter by mutableStateOf<TelecomCarrier?>(null)
        private set

    private var otpCountdownJob: Job? = null

    fun selectTab(tab: AppTab) {
        currentTab = tab
        errorMessage = null
        statusBannerMessage = null
    }

    fun toggleDarkTheme() {
        val next = !isDarkTheme
        isDarkTheme = next
        storage.setDarkTheme(next)
    }

    fun selectCarrier(carrier: TelecomCarrier) {
        selectedCarrier = carrier
        autoDetectedCarrier = false
        errorMessage = null
        storage.setDefaultCarrierId(carrier.id)
    }

    fun updatePhoneInput(newPhone: String) {
        val filtered = newPhone.filter { it.isDigit() || it == '+' }.take(14)
        val detected = TelecomCarrier.detectFromPhone(filtered)
        phoneInput = filtered
        if (detected != null) {
            autoDetectedCarrier = detected != selectedCarrier
            selectedCarrier = detected
        } else {
            autoDetectedCarrier = false
        }
        errorMessage = null
    }

    fun updateSubscriberLabelInput(label: String) {
        subscriberLabelInput = label.take(35)
    }

    fun initiatePortalBalanceQuery() {
        val cleanPhone = LocalTelecomStorage.normalizeYemenPhone(phoneInput)
        val valid = if (selectedCarrier == TelecomCarrier.YEMEN_NET) {
            cleanPhone.length in 7..9
        } else {
            cleanPhone.length == 9 && selectedCarrier.prefixes.any { cleanPhone.startsWith(it) }
        }

        if (!valid) {
            errorMessage = "يرجى إدخال رقم صحيح مكون من 9 أرقام يبدأ بـ ${selectedCarrier.prefixes.joinToString(" أو ")}"
            return
        }

        isLoading = true
        errorMessage = null
        statusBannerMessage = null

        viewModelScope.launch {
            delay(350)
            val hashSeed = abs(cleanPhone.hashCode())
            val otpCode = String.format(Locale.US, "%06d", (hashSeed % 900000) + 100000)
            val session = OtpSessionItem(
                phoneNumber = cleanPhone,
                carrierId = selectedCarrier.id,
                sessionToken = "YM-${System.currentTimeMillis().toString().takeLast(5)}",
                generatedOtpCode = otpCode,
                expiresAtMillis = System.currentTimeMillis() + 120_000L
            )
            isLoading = false
            activeOtpSession = session
            otpDigitsInput = ""
            otpRemainingSeconds = 120
            startOtpTimer(session)
        }
    }

    private fun startOtpTimer(session: OtpSessionItem) {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            delay(1400)
            if (activeOtpSession?.sessionToken == session.sessionToken && otpDigitsInput.isEmpty()) {
                val carrierAr = TelecomCarrier.fromId(session.carrierId).arabicName
                otpDigitsInput = session.generatedOtpCode
                statusBannerMessage = "تم التقاط رمز التحقق (${session.generatedOtpCode}) تلقائياً من رسالة $carrierAr"
            }
            for (remaining in 118 downTo 0) {
                delay(1000)
                if (activeOtpSession == null) break
                otpRemainingSeconds = remaining
            }
        }
    }

    fun updateOtpInput(otp: String) {
        otpDigitsInput = otp.filter { it.isDigit() }.take(6)
        errorMessage = null
    }

    fun autoFillDemoOtp() {
        val session = activeOtpSession ?: return
        otpDigitsInput = session.generatedOtpCode
        statusBannerMessage = "تمت تعبئة رمز التحقق السريع: ${session.generatedOtpCode}"
    }

    fun dismissOtpDialog() {
        otpCountdownJob?.cancel()
        activeOtpSession = null
        otpDigitsInput = ""
        errorMessage = null
    }

    fun confirmOtpAndFetchResult() {
        val session = activeOtpSession ?: return
        val cleanOtp = otpDigitsInput.filter { it.isDigit() }
        if (cleanOtp.length < 4) {
            errorMessage = "يرجى إدخال رمز التحقق المكون من 6 أرقام"
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            delay(400)
            val carrier = TelecomCarrier.fromId(session.carrierId)
            val carrierPackages = TelecomCatalogData.packages.filter { it.carrier == carrier }
            val seed = abs(session.phoneNumber.hashCode())
            val chosenPkg = carrierPackages.getOrElse(seed % carrierPackages.size.coerceAtLeast(1)) {
                TelecomCatalogData.packages.first()
            }

            val record = LocalTelecomStorage.buildVerifiedRecord(
                session = session,
                carrierName = carrier.arabicName,
                chosenPackageTitle = chosenPkg.title,
                dataQuotaText = chosenPkg.dataQuota,
                voiceQuotaText = chosenPkg.voiceQuota,
                enteredOtp = cleanOtp,
                customLabel = subscriberLabelInput
            )

            val updatedList = listOf(record) + allRecords
            storage.saveRecords(updatedList)
            allRecords = updatedList
            latestResult = record
            otpCountdownJob?.cancel()
            activeOtpSession = null
            otpDigitsInput = ""
            isLoading = false
            statusBannerMessage = "تم تحديث بيانات الرصيد والباقات وحفظها في السجل بنجاح"
        }
    }

    fun selectRecordForInspection(record: QueryRecordItem) {
        val carrier = TelecomCarrier.fromId(record.carrierId)
        currentTab = AppTab.QUERY
        selectedCarrier = carrier
        phoneInput = record.phoneNumber
        subscriberLabelInput = record.subscriberLabel
        latestResult = record
        statusBannerMessage = "تم عرض تقرير الرقم ${record.phoneNumber}"
    }

    fun toggleFavoriteRecord(record: QueryRecordItem) {
        val updated = allRecords.map {
            if (it.id == record.id) it.copy(isFavorite = !it.isFavorite) else it
        }
        storage.saveRecords(updated)
        allRecords = updated
        if (latestResult?.id == record.id) {
            latestResult = record.copy(isFavorite = !record.isFavorite)
        }
    }

    fun deleteHistoryRecord(recordId: Long) {
        val updated = allRecords.filterNot { it.id == recordId }
        storage.saveRecords(updated)
        allRecords = updated
        statusBannerMessage = "تم حذف السجل المحدد"
    }

    fun clearAllHistory() {
        storage.saveRecords(emptyList())
        allRecords = emptyList()
        statusBannerMessage = "تم مسح سجل العمليات بالكامل"
    }

    fun getFavoriteRecords(): List<QueryRecordItem> {
        return allRecords.filter { it.isFavorite }
    }

    fun getFilteredHistoryRecords(): List<QueryRecordItem> {
        val q = historySearchQuery.trim()
        return allRecords.filter { rec ->
            val matchesFav = !historyOnlyFavorites || rec.isFavorite
            val matchesCarrier = selectedCarrierFilter == null || rec.carrierId == selectedCarrierFilter?.id
            val matchesQuery = q.isEmpty() ||
                rec.phoneNumber.contains(q) ||
                rec.subscriberLabel.contains(q, ignoreCase = true) ||
                rec.activePackageName.contains(q, ignoreCase = true)
            matchesFav && matchesCarrier && matchesQuery
        }
    }

    // Packages & USSD methods
    fun selectPackageCategory(category: PackageCategory) {
        selectedPackageCategory = category
    }

    fun updatePackageSearch(query: String) {
        packageSearchQuery = query
    }

    fun getFilteredPackages(): List<TelecomPackage> {
        val q = packageSearchQuery.trim()
        return TelecomCatalogData.packages.filter { pkg ->
            val matchesCarrier = pkg.carrier == selectedCarrier
            val matchesCategory = selectedPackageCategory == PackageCategory.ALL ||
                pkg.category == selectedPackageCategory
            val matchesSearch = q.isEmpty() ||
                pkg.title.contains(q, ignoreCase = true) ||
                pkg.subtitle.contains(q, ignoreCase = true) ||
                pkg.activationUssd.contains(q)
            matchesCarrier && matchesCategory && matchesSearch
        }
    }

    fun getCarrierQuickUssdCodes(): List<UssdQuickCode> {
        return TelecomCatalogData.quickUssdCodes.filter { it.carrier == selectedCarrier }
    }

    // Smart Calculator methods
    fun updateCalcAmount(amount: String) {
        calcAmountInput = amount.filter { it.isDigit() || it == '.' }.take(8)
    }

    fun toggleCalcMode(isGrossToNet: Boolean) {
        calcIsGrossToNet = isGrossToNet
    }

    fun updateCalcDataGb(gb: String) {
        calcDataGbInput = gb.filter { it.isDigit() || it == '.' }.take(5)
    }

    fun updateCalcDays(days: String) {
        calcDaysInput = days.filter { it.isDigit() }.take(3)
    }

    // History filters
    fun updateHistorySearch(query: String) {
        historySearchQuery = query
    }

    fun toggleHistoryFavoritesFilter() {
        historyOnlyFavorites = !historyOnlyFavorites
    }

    fun selectHistoryCarrierFilter(carrier: TelecomCarrier?) {
        selectedCarrierFilter = carrier
    }

    fun generateExportContent(asCsv: Boolean): String {
        val list = getFilteredHistoryRecords().ifEmpty { allRecords }
        return LocalTelecomStorage.formatExportText(
            records = list,
            carrierNameResolver = { id -> TelecomCarrier.fromId(id).arabicName },
            asCsv = asCsv
        )
    }

    fun dismissBanner() {
        errorMessage = null
        statusBannerMessage = null
    }

    companion object {
        fun provideFactory(storage: LocalTelecomStorage): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TelecomViewModel(storage) as T
                }
            }
        }
    }
}
