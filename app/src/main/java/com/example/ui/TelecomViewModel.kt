package com.example.ui

import android.content.Context
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
import com.example.RealInboxSmsItem
import com.example.RealIncomingSmsListener
import com.example.RealSimSlotInfo
import com.example.RealTelecomEngine
import com.example.data.PackageCategory
import com.example.data.TelecomCarrier
import com.example.data.TelecomCatalogData
import com.example.data.TelecomPackage
import com.example.data.UssdQuickCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    var selectedSimSlot by mutableIntStateOf(storage.getSelectedSimSlot())
        private set

    var detectedSimCards by mutableStateOf<List<RealSimSlotInfo>>(emptyList())
        private set

    var phoneInput by mutableStateOf("")
        private set

    var subscriberLabelInput by mutableStateOf("")
        private set

    var customUssdCodeInput by mutableStateOf("")
        private set

    var rawSmsOrUssdPasteInput by mutableStateOf("")
        private set

    var portalBaseUrlInput by mutableStateOf(storage.getPortalBaseUrl())
        private set

    var autoDetectedCarrier by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isWaitingForRealSmsReply by mutableStateOf(false)
        private set

    var lastAutoReceivedSmsInfo by mutableStateOf<String?>(null)
        private set

    var inboxCarrierMessages by mutableStateOf<List<RealInboxSmsItem>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var statusBannerMessage by mutableStateOf<String?>(null)
        private set

    // OTP Dialog State (For Real Portal HTTP Request + Real Incoming SMS OTP Capture)
    var activeOtpSession by mutableStateOf<OtpSessionItem?>(null)
        private set

    var otpDigitsInput by mutableStateOf("")
        private set

    var otpRemainingSeconds by mutableIntStateOf(0)
        private set

    // Real Records & Latest Result State (Zero mock data)
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

    private val incomingSmsListener = RealIncomingSmsListener { sender, body, timestampMillis ->
        handleRealIncomingSms(sender, body, timestampMillis)
    }

    init {
        RealTelecomEngine.registerIncomingSmsListener(incomingSmsListener)
    }

    override fun onCleared() {
        RealTelecomEngine.unregisterIncomingSmsListener(incomingSmsListener)
        super.onCleared()
    }

    /**
     * Handles a real incoming SMS received from the Android BroadcastReceiver (SmsLiveReceiver)
     */
    private fun handleRealIncomingSms(sender: String, body: String, timestampMillis: Long) {
        // 1. If an OTP verification modal is open, extract the real OTP code from the incoming SMS
        if (activeOtpSession != null) {
            val realOtp = RealTelecomEngine.extractRealOtpFromSms(body)
            if (!realOtp.isNullOrBlank()) {
                otpDigitsInput = realOtp
                lastAutoReceivedSmsInfo = "تم استلام رمز التحقق الحقيقي ($realOtp) تلقائياً من الرسالة الواردة ($sender)"
                statusBannerMessage = lastAutoReceivedSmsInfo
            }
        }

        // 2. If the incoming SMS is from a telecom carrier (181, 121, Sabafon, YOU, YemenNet) or waiting for a reply
        if (isWaitingForRealSmsReply || RealTelecomEngine.isRealTelecomSms(sender, body)) {
            isWaitingForRealSmsReply = false
            isLoading = false

            val detectedCarrier = detectCarrierFromSender(sender, body) ?: selectedCarrier
            val parsed = RealTelecomEngine.parseCarrierUssdResponse(body)
            val newRecord = LocalTelecomStorage.createRecordFromRealUssd(
                phoneNumber = phoneInput.ifBlank { "وارد من $sender" },
                carrierId = detectedCarrier.id,
                carrierArabicName = detectedCarrier.arabicName,
                subscriberLabel = subscriberLabelInput.ifBlank { "رسالة حقيقية واردة ($sender)" },
                ussdCode = "SMS:$sender",
                parsed = parsed,
                timestampMillis = timestampMillis
            )

            val updated = listOf(newRecord) + allRecords
            storage.saveRecords(updated)
            allRecords = updated
            latestResult = newRecord

            val newInboxItem = RealInboxSmsItem(
                id = timestampMillis,
                sender = sender,
                body = body,
                timestampMillis = timestampMillis,
                parsed = parsed
            )
            inboxCarrierMessages = listOf(newInboxItem) + inboxCarrierMessages.filterNot {
                it.body == body && it.sender == sender
            }

            lastAutoReceivedSmsInfo = "تم استلام رسالة حقيقية الآن من ($sender) وتحديث الرصيد تلقائياً"
            statusBannerMessage = lastAutoReceivedSmsInfo
        }
    }

    private fun detectCarrierFromSender(sender: String, body: String): TelecomCarrier? {
        val s = sender.lowercase()
        val b = body.lowercase()
        return when {
            s.contains("181") || s.contains("121") || s.contains("yemenmobile") || b.contains("يمن موبايل") || b.contains("مزايا") ->
                TelecomCarrier.YEMEN_MOBILE
            s.contains("211") || s.contains("102") || s.contains("sabafon") || b.contains("سبأفون") || b.contains("سبافون") ->
                TelecomCarrier.SABAFON
            s.contains("111") || s.contains("120") || s.contains("555") || s.contains("you") ->
                TelecomCarrier.YOU
            s.contains("114") || s.contains("yemennet") || b.contains("يمن نت") || b.contains("يمن 4g") ->
                TelecomCarrier.YEMEN_NET
            else -> null
        }
    }

    fun refreshDetectedSimCards(context: Context) {
        val sims = RealTelecomEngine.getDetectedSimCards(context)
        detectedSimCards = sims
        val currentSim = sims.firstOrNull { it.slotIndex == selectedSimSlot } ?: sims.firstOrNull()
        if (currentSim != null && phoneInput.isBlank() && currentSim.number.isNotBlank()) {
            updatePhoneInput(currentSim.number)
        }
    }

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

    fun selectSimSlot(slotIndex: Int) {
        selectedSimSlot = slotIndex
        storage.setSelectedSimSlot(slotIndex)
        val simInfo = detectedSimCards.firstOrNull { it.slotIndex == slotIndex }
        if (simInfo != null && simInfo.number.isNotBlank() && phoneInput.isBlank()) {
            updatePhoneInput(simInfo.number)
        }
        statusBannerMessage = "تم اختيار الشريحة رقم ${slotIndex + 1} (${simInfo?.carrierName ?: selectedCarrier.arabicName}) للاستعلام الحقيقي"
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

    fun updateCustomUssdCodeInput(code: String) {
        customUssdCodeInput = code.filter { it.isDigit() || it == '*' || it == '#' }.take(20)
    }

    fun updateRawSmsPasteInput(text: String) {
        rawSmsOrUssdPasteInput = text
        errorMessage = null
    }

    fun updatePortalBaseUrl(url: String) {
        portalBaseUrlInput = url
        storage.setPortalBaseUrl(url)
    }

    /**
     * Method 1 (Real Live USSD on SIM): Executes a live USSD request directly on the device's SIM card
     * using Android TelephonyManager.sendUssdRequest() and parses the real carrier reply.
     */
    fun executeRealSimUssdBalanceQuery(context: Context, customUssdCode: String? = null) {
        refreshDetectedSimCards(context)
        val codeToRun = customUssdCode?.takeIf { it.isNotBlank() }
            ?: customUssdCodeInput.takeIf { it.isNotBlank() }
            ?: selectedCarrier.balanceUssd

        isLoading = true
        errorMessage = null
        statusBannerMessage = "جاري إرسال الكود $codeToRun مباشرة إلى شبكة ${selectedCarrier.arabicName} عبر الشريحة ${selectedSimSlot + 1}..."

        RealTelecomEngine.executeLiveUssdOnSim(
            context = context,
            ussdCode = codeToRun,
            simSlotIndex = selectedSimSlot,
            onSuccess = { parsed ->
                isLoading = false
                val simNumber = detectedSimCards.firstOrNull { it.slotIndex == selectedSimSlot }?.number.orEmpty()
                val effectivePhone = phoneInput.ifBlank {
                    simNumber.ifBlank { "شريحة ${selectedSimSlot + 1} (${selectedCarrier.arabicName})" }
                }
                val newRecord = LocalTelecomStorage.createRecordFromRealUssd(
                    phoneNumber = effectivePhone,
                    carrierId = selectedCarrier.id,
                    carrierArabicName = selectedCarrier.arabicName,
                    subscriberLabel = subscriberLabelInput,
                    ussdCode = codeToRun,
                    parsed = parsed
                )
                val updated = listOf(newRecord) + allRecords
                storage.saveRecords(updated)
                allRecords = updated
                latestResult = newRecord
                statusBannerMessage = "تم استلام رد الشبكة الحقيقي للكود ($codeToRun) وحفظه بنجاح"
            },
            onError = { errMsg ->
                isLoading = false
                errorMessage = errMsg
            }
        )
    }

    /**
     * Method 2 (Real Direct SMS Inquiry + Automatic Real SMS Receiving):
     * Sends a real SMS ("رصيد") via SmsManager to 181 (Yemen Mobile) / 211 (Sabafon) / 111 (YOU) / 114 (Yemen Net)
     * and listens via SmsLiveReceiver for the real incoming carrier reply SMS!
     */
    fun sendRealSmsBalanceInquiry(context: Context) {
        val (serviceNumber, smsBody) = when (selectedCarrier) {
            TelecomCarrier.YEMEN_MOBILE -> "181" to "رصيد"
            TelecomCarrier.SABAFON -> "211" to "رصيد"
            TelecomCarrier.YOU -> "111" to "رصيد"
            TelecomCarrier.YEMEN_NET -> "114" to "رصيد"
        }

        isLoading = true
        isWaitingForRealSmsReply = true
        errorMessage = null
        statusBannerMessage = "جاري إرسال رسالة حقيقية \"$smsBody\" إلى $serviceNumber عبر الشريحة ${selectedSimSlot + 1} وبانتظار استلام رد الشبكة تلقائياً..."

        RealTelecomEngine.sendRealSmsInquiryOnSim(
            context = context,
            destinationAddress = serviceNumber,
            messageBody = smsBody,
            simSlotIndex = selectedSimSlot,
            onSentSuccess = {
                isLoading = false
                statusBannerMessage = "تم إرسال رسالة الاستعلام إلى $serviceNumber بنجاح! سيتم التقاط رسالة الرد تلقائياً فور وصولها."
            },
            onSentError = { err ->
                isLoading = false
                isWaitingForRealSmsReply = false
                errorMessage = err
            }
        )
    }

    /**
     * Method 3 (Real SMS Inbox Scanner):
     * Reads actual SMS messages stored in the user's phone inbox (content://sms/inbox) from 181, 121, YemenMobile, etc.
     */
    fun scanRealSmsInbox(context: Context) {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val inboxResult = withContext(Dispatchers.IO) {
                RealTelecomEngine.readRealCarrierSmsInbox(context, maxCount = 30)
            }
            isLoading = false
            inboxResult.fold(
                onSuccess = { messages ->
                    inboxCarrierMessages = messages
                    val latestMsg = messages.firstOrNull()
                    if (latestMsg != null) {
                        selectInboxSmsAsResult(latestMsg)
                        statusBannerMessage = "تم قراءة ${messages.size} رسالة حقيقية من صندوق رسائل الهاتف واستخراج أحدث رصيد"
                    } else {
                        statusBannerMessage = "لم يتم العثور على رسائل رصيد سابقة في صندوق الوارد (أرسل رسالة إلى 181 أو استعلم عبر USSD)"
                    }
                },
                onFailure = { err ->
                    errorMessage = err.localizedMessage ?: "تعذر قراءة صندوق الرسائل الحقيقي"
                }
            )
        }
    }

    fun selectInboxSmsAsResult(smsItem: RealInboxSmsItem) {
        val detectedCarrier = detectCarrierFromSender(smsItem.sender, smsItem.body) ?: selectedCarrier
        val record = LocalTelecomStorage.createRecordFromRealUssd(
            phoneNumber = phoneInput.ifBlank { "رسالة واردة (${smsItem.sender})" },
            carrierId = detectedCarrier.id,
            carrierArabicName = detectedCarrier.arabicName,
            subscriberLabel = subscriberLabelInput.ifBlank { "صندوق الرسائل (${smsItem.sender})" },
            ussdCode = "INBOX:${smsItem.sender}",
            parsed = smsItem.parsed,
            timestampMillis = smsItem.timestampMillis
        )
        // Avoid duplicating identical timestamp record
        val existingIdx = allRecords.indexOfFirst {
            it.activePackageName == record.activePackageName && it.timestampMillis == record.timestampMillis
        }
        val updated = if (existingIdx >= 0) {
            allRecords
        } else {
            (listOf(record) + allRecords).also { storage.saveRecords(it) }
        }
        allRecords = updated
        latestResult = record
        statusBannerMessage = "تم استخراج بيانات الرصيد الحقيقية من رسالة (${smsItem.sender})"
    }

    /**
     * Method 4 (Real SMS / USSD Text Parser): Parses an actual SMS or USSD message pasted from the carrier (e.g. 181, 121)
     */
    fun parseAndSaveRealCarrierText(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.length < 3) {
            errorMessage = "يرجى لصق نص رسالة الرصيد الواردة من الشبكة أولاً"
            return
        }
        val parsed = RealTelecomEngine.parseCarrierUssdResponse(trimmed)
        val newRecord = LocalTelecomStorage.createRecordFromRealUssd(
            phoneNumber = phoneInput.ifBlank { "رسالة ${selectedCarrier.arabicName}" },
            carrierId = selectedCarrier.id,
            carrierArabicName = selectedCarrier.arabicName,
            subscriberLabel = subscriberLabelInput.ifBlank { "تحليل رسالة شبكة حقيقية" },
            ussdCode = "SMS/USSD",
            parsed = parsed
        )
        val updated = listOf(newRecord) + allRecords
        storage.saveRecords(updated)
        allRecords = updated
        latestResult = newRecord
        rawSmsOrUssdPasteInput = ""
        statusBannerMessage = "تم استخراج الرصيد الحقيقي من نص رسالة الشبكة وحفظه"
    }

    /**
     * Method 5 (Real Portal HTTP API Request): Connects to the actual server URL without generating fake numbers.
     */
    fun initiateRealPortalOtpRequest() {
        val cleanPhone = LocalTelecomStorage.normalizeYemenPhone(phoneInput)
        val valid = if (selectedCarrier == TelecomCarrier.YEMEN_NET) {
            cleanPhone.length in 7..9
        } else {
            cleanPhone.length == 9 && selectedCarrier.prefixes.any { cleanPhone.startsWith(it) }
        }

        if (!valid) {
            errorMessage = "يرجى إدخال رقم مشترك صحيح مكون من 9 أرقام يبدأ بـ ${selectedCarrier.prefixes.joinToString(" أو ")}"
            return
        }

        isLoading = true
        errorMessage = null
        statusBannerMessage = null

        viewModelScope.launch {
            val baseUrl = portalBaseUrlInput.trim().removeSuffix("/")
            val payload = JSONObject().apply {
                put("mobile", cleanPhone)
                put("phone", cleanPhone)
                put("carrier", selectedCarrier.id)
            }.toString()

            val responseResult = withContext(Dispatchers.IO) {
                RealTelecomEngine.executeRealHttpPost(
                    endpointUrl = "$baseUrl/api/otp/request",
                    jsonPayload = payload
                )
            }

            isLoading = false
            responseResult.fold(
                onSuccess = { serverBody ->
                    val session = OtpSessionItem(
                        phoneNumber = cleanPhone,
                        carrierId = selectedCarrier.id,
                        sessionToken = serverBody.take(60),
                        generatedOtpCode = "",
                        expiresAtMillis = System.currentTimeMillis() + 120_000L
                    )
                    activeOtpSession = session
                    otpDigitsInput = ""
                    otpRemainingSeconds = 120
                    startOtpTimer()
                },
                onFailure = { err ->
                    errorMessage = "تنبيه البوابة الرقمية: الخادم ($baseUrl) لم يستجب (${err.localizedMessage ?: "غير متصل"}). استخدم زر «فحص الرصيد الحقيقي عبر الشريحة USSD» أو «استعلام عبر رسالة 181 الحقيقية» بالأعلى."
                }
            )
        }
    }

    private fun startOtpTimer() {
        otpCountdownJob?.cancel()
        otpCountdownJob = viewModelScope.launch {
            for (remaining in 120 downTo 0) {
                delay(1000)
                if (activeOtpSession == null) break
                otpRemainingSeconds = remaining
            }
        }
    }

    fun updateOtpInput(otp: String) {
        otpDigitsInput = otp.filter { it.isDigit() }.take(8)
        errorMessage = null
    }

    fun dismissOtpDialog() {
        otpCountdownJob?.cancel()
        activeOtpSession = null
        otpDigitsInput = ""
        errorMessage = null
    }

    fun confirmRealOtpAndFetchResult() {
        val session = activeOtpSession ?: return
        val cleanOtp = otpDigitsInput.filter { it.isDigit() }
        if (cleanOtp.length < 4) {
            errorMessage = "يرجى إدخال رمز التحقق الحقيقي الوارد في الرسالة النصية"
            return
        }

        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            val baseUrl = portalBaseUrlInput.trim().removeSuffix("/")
            val payload = JSONObject().apply {
                put("mobile", session.phoneNumber)
                put("otp", cleanOtp)
                put("token", session.sessionToken)
            }.toString()

            val verifyResult = withContext(Dispatchers.IO) {
                RealTelecomEngine.executeRealHttpPost(
                    endpointUrl = "$baseUrl/api/otp/verify",
                    jsonPayload = payload
                )
            }

            isLoading = false
            verifyResult.fold(
                onSuccess = { responseBody ->
                    val carrier = TelecomCarrier.fromId(session.carrierId)
                    val parsed = RealTelecomEngine.parseCarrierUssdResponse(responseBody)
                    val record = LocalTelecomStorage.createRecordFromRealUssd(
                        phoneNumber = session.phoneNumber,
                        carrierId = carrier.id,
                        carrierArabicName = carrier.arabicName,
                        subscriberLabel = subscriberLabelInput,
                        ussdCode = "PORTAL-OTP",
                        parsed = parsed
                    )
                    val updatedList = listOf(record) + allRecords
                    storage.saveRecords(updatedList)
                    allRecords = updatedList
                    latestResult = record
                    otpCountdownJob?.cancel()
                    activeOtpSession = null
                    otpDigitsInput = ""
                    statusBannerMessage = "تم استلام النتيجة الحقيقية من البوابة وحفظها"
                },
                onFailure = { err ->
                    errorMessage = "فشل التحقق من الخادم: ${err.localizedMessage ?: "خطأ في الاتصال"}"
                }
            )
        }
    }

    fun selectRecordForInspection(record: QueryRecordItem) {
        val carrier = TelecomCarrier.fromId(record.carrierId)
        currentTab = AppTab.QUERY
        selectedCarrier = carrier
        phoneInput = record.phoneNumber.filter { it.isDigit() }
        subscriberLabelInput = record.subscriberLabel
        latestResult = record
        statusBannerMessage = "تم عرض سجل الاستعلام الحقيقي"
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
        if (latestResult?.id == recordId) {
            latestResult = updated.firstOrNull()
        }
        statusBannerMessage = "تم حذف السجل المحدد"
    }

    fun clearAllHistory() {
        storage.saveRecords(emptyList())
        allRecords = emptyList()
        latestResult = null
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
