package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.OtpSmsBridge
import com.example.OtpSmsListener
import com.example.data.AppPreferences
import com.example.data.AppScreen
import com.example.data.BalanceInquiryResult
import com.example.data.CarrierType
import com.example.data.InquiryFetchOutcome
import com.example.data.InquiryStartOutcome
import com.example.data.TelecomApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BalanceViewModel(
    private val prefs: AppPreferences,
    private val apiClient: TelecomApiClient
) : ViewModel() {

    var currentScreen by mutableStateOf(AppScreen.QUERY)
        private set

    var isDarkTheme by mutableStateOf(prefs.isDarkTheme())
        private set

    var isDemoMode by mutableStateOf(prefs.isDemoMode())
        private set

    var phoneInput by mutableStateOf("")
        private set

    var activeCarrier by mutableStateOf(CarrierType.YEMEN_MOBILE)
        private set

    var otpInput by mutableStateOf("")
        private set

    var otpCountdownSeconds by mutableIntStateOf(0)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var infoMessage by mutableStateOf<String?>(null)
        private set

    var latestResult by mutableStateOf<BalanceInquiryResult?>(null)
        private set

    var showDeveloperDialog by mutableStateOf(false)
        private set

    var sabafonVerifyPath by mutableStateOf(prefs.getSabafonVerifyPath())
        private set

    var sabafonBalancePath by mutableStateOf(prefs.getSabafonBalancePath())
        private set

    var sabafonPackagesPath by mutableStateOf(prefs.getSabafonPackagesPath())
        private set

    private var versionTapCount = 0
    private var lastVersionTapMillis = 0L
    private var countdownJob: Job? = null

    private val smsOtpListener = OtpSmsListener { receivedCode ->
        if (currentScreen == AppScreen.OTP && !isLoading) {
            otpInput = receivedCode.take(8)
            errorMessage = null
            infoMessage = "تمت قراءة رمز التحقق تلقائياً من الرسالة"
            verifyOtp()
        }
    }

    init {
        OtpSmsBridge.register(smsOtpListener)
    }

    override fun onCleared() {
        countdownJob?.cancel()
        OtpSmsBridge.unregister(smsOtpListener)
        super.onCleared()
    }

    fun toggleDarkTheme() {
        val next = !isDarkTheme
        isDarkTheme = next
        prefs.setDarkTheme(next)
    }

    fun onVersionTapped() {
        val now = System.currentTimeMillis()
        if (now - lastVersionTapMillis > 2500L) {
            versionTapCount = 0
        }
        lastVersionTapMillis = now
        versionTapCount++
        if (versionTapCount >= 5) {
            versionTapCount = 0
            showDeveloperDialog = true
        }
    }

    fun dismissDeveloperDialog() {
        showDeveloperDialog = false
    }

    fun setDemoModeEnabled(enabled: Boolean) {
        isDemoMode = enabled
        prefs.setDemoMode(enabled)
        infoMessage = if (enabled) "تم تفعيل الوضع التجريبي" else "تم إيقاف الوضع التجريبي"
    }

    fun saveSabafonDeveloperPaths(verify: String, balance: String, packages: String) {
        val cleanVerify = verify.trim().ifBlank { AppPreferences.DEFAULT_SABAFON_VERIFY_PATH }
        val cleanBalance = balance.trim().ifBlank { AppPreferences.DEFAULT_SABAFON_BALANCE_PATH }
        val cleanPackages = packages.trim().ifBlank { AppPreferences.DEFAULT_SABAFON_PACKAGES_PATH }
        sabafonVerifyPath = cleanVerify
        sabafonBalancePath = cleanBalance
        sabafonPackagesPath = cleanPackages
        prefs.setSabafonVerifyPath(cleanVerify)
        prefs.setSabafonBalancePath(cleanBalance)
        prefs.setSabafonPackagesPath(cleanPackages)
        showDeveloperDialog = false
        infoMessage = "تم حفظ إعدادات المسارات"
    }

    fun resetSabafonDeveloperPaths() {
        saveSabafonDeveloperPaths(
            AppPreferences.DEFAULT_SABAFON_VERIFY_PATH,
            AppPreferences.DEFAULT_SABAFON_BALANCE_PATH,
            AppPreferences.DEFAULT_SABAFON_PACKAGES_PATH
        )
    }

    fun updatePhoneInput(raw: String) {
        val normalized = CarrierType.normalizeDigits(raw).take(9)
        phoneInput = normalized
        if (normalized.length >= 2) {
            activeCarrier = CarrierType.detectFromPhone(normalized)
        }
        errorMessage = null
    }

    fun updateOtpInput(raw: String) {
        val digits = CarrierType.normalizeDigits(raw).take(8)
        otpInput = digits
        errorMessage = null
    }

    fun clearBannerMessages() {
        errorMessage = null
        infoMessage = null
    }

    // ── Screen 1 Action: "استعلام" ──
    fun submitPhoneInquiry() {
        val cleanPhone = CarrierType.normalizeDigits(phoneInput)
        if (!CarrierType.isValidYemenNumber(cleanPhone)) {
            errorMessage = "يرجى إدخال رقم صحيح مكون من 9 أرقام يبدأ بـ 77 أو 78 أو 71"
            return
        }

        val carrier = CarrierType.detectFromPhone(cleanPhone)
        phoneInput = cleanPhone
        activeCarrier = carrier
        errorMessage = null
        infoMessage = null
        isLoading = true

        viewModelScope.launch {
            if (isDemoMode) {
                // Demo Mode: Zero network calls, 1.5s delay then direct Result screen
                delay(1500L)
                latestResult = apiClient.buildDemoResult(cleanPhone, carrier)
                isLoading = false
                currentScreen = AppScreen.RESULT
                return@launch
            }

            val outcome = withContext(Dispatchers.IO) {
                apiClient.startInquiry(cleanPhone, carrier)
            }

            isLoading = false
            when (outcome) {
                is InquiryStartOutcome.NeedsOtp -> {
                    otpInput = ""
                    currentScreen = AppScreen.OTP
                    startOtpCountdown()
                }
                is InquiryStartOutcome.DirectResult -> {
                    latestResult = outcome.result
                    currentScreen = AppScreen.RESULT
                }
                is InquiryStartOutcome.Error -> {
                    errorMessage = outcome.message
                }
            }
        }
    }

    // ── Screen 2 Actions: Verify / Resend / Cancel ──
    fun verifyOtp() {
        val cleanOtp = CarrierType.normalizeDigits(otpInput)
        if (cleanOtp.length < 4) {
            errorMessage = "يرجى إدخال رمز التحقق المرسل في الرسالة النصية"
            return
        }

        val cleanPhone = CarrierType.normalizeDigits(phoneInput)
        val carrier = activeCarrier
        errorMessage = null
        isLoading = true

        viewModelScope.launch {
            if (isDemoMode) {
                delay(1500L)
                latestResult = apiClient.buildDemoResult(cleanPhone, carrier)
                isLoading = false
                stopOtpCountdown()
                currentScreen = AppScreen.RESULT
                return@launch
            }

            val outcome = withContext(Dispatchers.IO) {
                apiClient.verifyOtpAndFetch(cleanPhone, carrier, cleanOtp)
            }

            isLoading = false
            when (outcome) {
                is InquiryFetchOutcome.Success -> {
                    latestResult = outcome.result
                    stopOtpCountdown()
                    otpInput = ""
                    currentScreen = AppScreen.RESULT
                }
                is InquiryFetchOutcome.NeedsReauth -> {
                    errorMessage = "انتهت صلاحية الجلسة، يرجى إعادة إرسال الرمز"
                }
                is InquiryFetchOutcome.Error -> {
                    errorMessage = outcome.message
                }
            }
        }
    }

    fun resendOtp() {
        if (otpCountdownSeconds > 0 || isLoading) return
        val cleanPhone = CarrierType.normalizeDigits(phoneInput)
        val carrier = activeCarrier
        errorMessage = null
        isLoading = true

        viewModelScope.launch {
            if (isDemoMode) {
                delay(800L)
                isLoading = false
                startOtpCountdown()
                infoMessage = "تمت إعادة إرسال رمز التحقق"
                return@launch
            }

            val outcome = withContext(Dispatchers.IO) {
                apiClient.startInquiry(cleanPhone, carrier)
            }

            isLoading = false
            when (outcome) {
                is InquiryStartOutcome.NeedsOtp -> {
                    otpInput = ""
                    startOtpCountdown()
                    infoMessage = "تمت إعادة إرسال رمز التحقق"
                }
                is InquiryStartOutcome.DirectResult -> {
                    latestResult = outcome.result
                    stopOtpCountdown()
                    currentScreen = AppScreen.RESULT
                }
                is InquiryStartOutcome.Error -> {
                    errorMessage = outcome.message
                }
            }
        }
    }

    fun cancelOtpAndReturn() {
        stopOtpCountdown()
        otpInput = ""
        isLoading = false
        errorMessage = null
        currentScreen = AppScreen.QUERY
    }

    // ── Screen 3 Actions: "تحديث" & "رقم جديد" ──
    fun refreshCurrentBalance() {
        val current = latestResult ?: return
        if (isLoading) return

        errorMessage = null
        isLoading = true

        viewModelScope.launch {
            if (isDemoMode) {
                delay(1500L)
                latestResult = apiClient.buildDemoResult(current.phoneNumber, current.carrier)
                isLoading = false
                infoMessage = "تم تحديث الرصيد والباقات"
                return@launch
            }

            val outcome = withContext(Dispatchers.IO) {
                apiClient.refreshBalance(current.phoneNumber, current.carrier)
            }

            isLoading = false
            when (outcome) {
                is InquiryFetchOutcome.Success -> {
                    latestResult = outcome.result
                    infoMessage = "تم تحديث الرصيد والباقات"
                }
                is InquiryFetchOutcome.NeedsReauth -> {
                    // Token expired -> re-trigger OTP seamlessly
                    submitPhoneInquiry()
                }
                is InquiryFetchOutcome.Error -> {
                    errorMessage = outcome.message
                }
            }
        }
    }

    fun startNewNumber() {
        stopOtpCountdown()
        phoneInput = ""
        otpInput = ""
        latestResult = null
        errorMessage = null
        infoMessage = null
        isLoading = false
        currentScreen = AppScreen.QUERY
    }

    private fun startOtpCountdown() {
        countdownJob?.cancel()
        otpCountdownSeconds = 60
        countdownJob = viewModelScope.launch {
            while (otpCountdownSeconds > 0) {
                delay(1000L)
                otpCountdownSeconds--
            }
        }
    }

    private fun stopOtpCountdown() {
        countdownJob?.cancel()
        otpCountdownSeconds = 0
    }

    companion object {
        fun provideFactory(prefs: AppPreferences): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BalanceViewModel(prefs, TelecomApiClient(prefs)) as T
                }
            }
        }
    }
}
