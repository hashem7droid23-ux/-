package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.OtpSessionState
import com.example.data.PackageCategory
import com.example.data.QueryRecordEntity
import com.example.data.TelecomCarrier
import com.example.data.TelecomCatalogData
import com.example.data.TelecomPackage
import com.example.data.TelecomRepository
import com.example.data.UssdQuickCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val route: String, val titleAr: String) {
    QUERY("query", "الاستعلام"),
    PACKAGES("packages", "الباقات والأكواد"),
    CALCULATOR("calculator", "الحاسبة"),
    HISTORY("history", "السجل")
}

data class TelecomUiState(
    val currentTab: AppTab = AppTab.QUERY,
    val selectedCarrier: TelecomCarrier = TelecomCarrier.YEMEN_MOBILE,
    val phoneInput: String = "777889900",
    val subscriberLabelInput: String = "",
    val autoDetectedCarrier: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusBannerMessage: String? = null,
    // OTP Dialog State
    val activeOtpSession: OtpSessionState? = null,
    val otpDigitsInput: String = "",
    val otpRemainingSeconds: Int = 0,
    val isAutoSmsListening: Boolean = true,
    // Result View State
    val latestResult: QueryRecordEntity? = null,
    // Packages & USSD Filter State
    val selectedPackageCategory: PackageCategory = PackageCategory.ALL,
    val packageSearchQuery: String = "",
    // Smart Calculator State
    val calcAmountInput: String = "3000",
    val calcIsGrossToNet: Boolean = false, // false = I want X net balance, how much to pay? true = I pay X, how much net balance?
    val calcDataGbInput: String = "5",
    val calcDaysInput: String = "30",
    // History Filter State
    val historySearchQuery: String = "",
    val historyOnlyFavorites: Boolean = false,
    val selectedCarrierFilter: TelecomCarrier? = null
)

class TelecomViewModel(
    private val repository: TelecomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TelecomUiState())
    val uiState: StateFlow<TelecomUiState> = _uiState.asStateFlow()

    val isDarkTheme: StateFlow<Boolean> = repository.isDarkThemeFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val allRecords: StateFlow<List<QueryRecordEntity>> = repository.allRecordsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredHistoryRecords: StateFlow<List<QueryRecordEntity>> = combine(
        allRecords,
        _uiState
    ) { records, state ->
        records.filter { rec ->
            val matchesFav = !state.historyOnlyFavorites || rec.isFavorite
            val matchesCarrier = state.selectedCarrierFilter == null || rec.carrierId == state.selectedCarrierFilter.id
            val q = state.historySearchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                rec.phoneNumber.contains(q) ||
                rec.subscriberLabel.contains(q, ignoreCase = true) ||
                rec.activePackageName.contains(q, ignoreCase = true)
            matchesFav && matchesCarrier && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var otpCountdownJob: Job? = null

    init {
        viewModelScope.launch {
            val savedCarrier = repository.defaultCarrierFlow.first()
            _uiState.update { it.copy(selectedCarrier = savedCarrier) }
            val existing = repository.allRecordsFlow.first()
            if (existing.isEmpty()) {
                repository.seedInitialSampleIfEmpty(0)
            }
            val updated = repository.allRecordsFlow.first()
            if (updated.isNotEmpty() && _uiState.value.latestResult == null) {
                _uiState.update { it.copy(latestResult = updated.first()) }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(currentTab = tab, errorMessage = null, statusBannerMessage = null) }
    }

    fun toggleDarkTheme() {
        viewModelScope.launch {
            repository.setDarkTheme(!isDarkTheme.value)
        }
    }

    fun selectCarrier(carrier: TelecomCarrier) {
        _uiState.update {
            it.copy(
                selectedCarrier = carrier,
                autoDetectedCarrier = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            repository.setDefaultCarrier(carrier)
        }
    }

    fun updatePhoneInput(newPhone: String) {
        val filtered = newPhone.filter { it.isDigit() || it == '+' }.take(14)
        val detected = TelecomCarrier.detectFromPhone(filtered)
        _uiState.update { current ->
            val updatedCarrier = detected ?: current.selectedCarrier
            current.copy(
                phoneInput = filtered,
                selectedCarrier = updatedCarrier,
                autoDetectedCarrier = detected != null && detected != current.selectedCarrier,
                errorMessage = null
            )
        }
    }

    fun updateSubscriberLabelInput(label: String) {
        _uiState.update { it.copy(subscriberLabelInput = label.take(35)) }
    }

    fun initiatePortalBalanceQuery() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, statusBannerMessage = null) }
            val result = repository.requestOtpForPhone(state.phoneInput, state.selectedCarrier)
            result.fold(
                onSuccess = { session ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeOtpSession = session,
                            otpDigitsInput = "",
                            otpRemainingSeconds = 120
                        )
                    }
                    startOtpTimer(session)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "تعذر إرسال طلب التحقق"
                        )
                    }
                }
            )
        }
    }

    private fun startOtpTimer(session: OtpSessionState) {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            // Simulate automatic SMS receiver capture after 2.2 seconds if auto-listen is enabled
            delay(1800)
            if (_uiState.value.activeOtpSession?.sessionToken == session.sessionToken &&
                _uiState.value.otpDigitsInput.isEmpty() &&
                _uiState.value.isAutoSmsListening
            ) {
                _uiState.update {
                    it.copy(
                        otpDigitsInput = session.generatedOtpCode,
                        statusBannerMessage = "تم التقاط رمز التحقق (${session.generatedOtpCode}) تلقائياً من رسالة ${session.carrier.arabicName}"
                    )
                }
            }
            for (remaining in 118 downTo 0) {
                delay(1000)
                if (_uiState.value.activeOtpSession == null) break
                _uiState.update { it.copy(otpRemainingSeconds = remaining) }
            }
        }
    }

    fun updateOtpInput(otp: String) {
        val clean = otp.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpDigitsInput = clean, errorMessage = null) }
    }

    fun autoFillDemoOtp() {
        val session = _uiState.value.activeOtpSession ?: return
        _uiState.update {
            it.copy(
                otpDigitsInput = session.generatedOtpCode,
                statusBannerMessage = "تمت تعبئة رمز التحقق السريع: ${session.generatedOtpCode}"
            )
        }
    }

    fun dismissOtpDialog() {
        otpCountdownJob?.cancel()
        _uiState.update { it.copy(activeOtpSession = null, otpDigitsInput = "", errorMessage = null) }
    }

    fun confirmOtpAndFetchResult() {
        val state = _uiState.value
        val session = state.activeOtpSession ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.verifyOtpAndFetchBalance(
                session = session,
                enteredOtp = state.otpDigitsInput,
                customSubscriberLabel = state.subscriberLabelInput
            )
            result.fold(
                onSuccess = { record ->
                    otpCountdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeOtpSession = null,
                            otpDigitsInput = "",
                            latestResult = record,
                            statusBannerMessage = "تم تحديث بيانات الرصيد والباقات وحفظها في السجل بنجاح"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "رمز التحقق غير صحيح"
                        )
                    }
                }
            )
        }
    }

    fun selectRecordForInspection(record: QueryRecordEntity) {
        val carrier = TelecomCarrier.fromId(record.carrierId)
        _uiState.update {
            it.copy(
                currentTab = AppTab.QUERY,
                selectedCarrier = carrier,
                phoneInput = record.phoneNumber,
                subscriberLabelInput = record.subscriberLabel,
                latestResult = record,
                statusBannerMessage = "تم عرض تقرير الرقم ${record.phoneNumber}"
            )
        }
    }

    fun toggleFavoriteRecord(record: QueryRecordEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(record)
            if (_uiState.value.latestResult?.id == record.id) {
                _uiState.update {
                    it.copy(latestResult = record.copy(isFavorite = !record.isFavorite))
                }
            }
        }
    }

    fun deleteHistoryRecord(recordId: Long) {
        viewModelScope.launch {
            repository.deleteRecord(recordId)
            _uiState.update {
                it.copy(statusBannerMessage = "تم حذف السجل المحدد")
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _uiState.update {
                it.copy(statusBannerMessage = "تم مسح سجل العمليات بالكامل")
            }
        }
    }

    // Packages & USSD methods
    fun selectPackageCategory(category: PackageCategory) {
        _uiState.update { it.copy(selectedPackageCategory = category) }
    }

    fun updatePackageSearch(query: String) {
        _uiState.update { it.copy(packageSearchQuery = query) }
    }

    fun getFilteredPackages(): List<TelecomPackage> {
        val state = _uiState.value
        return TelecomCatalogData.packages.filter { pkg ->
            val matchesCarrier = pkg.carrier == state.selectedCarrier
            val matchesCategory = state.selectedPackageCategory == PackageCategory.ALL ||
                pkg.category == state.selectedPackageCategory
            val q = state.packageSearchQuery.trim()
            val matchesSearch = q.isEmpty() ||
                pkg.title.contains(q, ignoreCase = true) ||
                pkg.subtitle.contains(q, ignoreCase = true) ||
                pkg.activationUssd.contains(q)
            matchesCarrier && matchesCategory && matchesSearch
        }
    }

    fun getCarrierQuickUssdCodes(): List<UssdQuickCode> {
        val carrier = _uiState.value.selectedCarrier
        return TelecomCatalogData.quickUssdCodes.filter { it.carrier == carrier }
    }

    // Smart Calculator methods
    fun updateCalcAmount(amount: String) {
        val clean = amount.filter { it.isDigit() || it == '.' }.take(8)
        _uiState.update { it.copy(calcAmountInput = clean) }
    }

    fun toggleCalcMode(isGrossToNet: Boolean) {
        _uiState.update { it.copy(calcIsGrossToNet = isGrossToNet) }
    }

    fun updateCalcDataGb(gb: String) {
        val clean = gb.filter { it.isDigit() || it == '.' }.take(5)
        _uiState.update { it.copy(calcDataGbInput = clean) }
    }

    fun updateCalcDays(days: String) {
        val clean = days.filter { it.isDigit() }.take(3)
        _uiState.update { it.copy(calcDaysInput = clean) }
    }

    // History filters
    fun updateHistorySearch(query: String) {
        _uiState.update { it.copy(historySearchQuery = query) }
    }

    fun toggleHistoryFavoritesFilter() {
        _uiState.update { it.copy(historyOnlyFavorites = !it.historyOnlyFavorites) }
    }

    fun selectHistoryCarrierFilter(carrier: TelecomCarrier?) {
        _uiState.update { it.copy(selectedCarrierFilter = carrier) }
    }

    fun generateExportContent(asCsv: Boolean): String {
        val list = filteredHistoryRecords.value.ifEmpty { allRecords.value }
        return repository.formatExportReport(list, asCsv)
    }

    fun dismissBanner() {
        _uiState.update { it.copy(errorMessage = null, statusBannerMessage = null) }
    }

    companion object {
        fun provideFactory(repository: TelecomRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TelecomViewModel(repository) as T
                }
            }
        }
    }
}
