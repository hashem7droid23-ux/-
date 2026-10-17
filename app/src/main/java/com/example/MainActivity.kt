package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.TelecomCarrier
import com.example.ui.AppTab
import com.example.ui.HistoryExportScreen
import com.example.ui.PackagesUssdScreen
import com.example.ui.QueryDashboardScreen
import com.example.ui.SmartCalculatorScreen
import com.example.ui.TelecomViewModel
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.RoyalMaroon
import com.example.ui.theme.SoftChampagneGold
import com.example.ui.theme.YemenBalanceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val storage = LocalTelecomStorage(applicationContext)

        setContent {
            val viewModel: TelecomViewModel = viewModel(
                factory = TelecomViewModel.provideFactory(storage)
            )

            YemenBalanceTheme(darkTheme = viewModel.isDarkTheme) {
                YemenTelecomAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YemenTelecomAppRoot(
    viewModel: TelecomViewModel
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var pendingUssdCodeForLiveSim by remember { mutableStateOf<String?>(null) }

    // 1. Runtime Permission Launcher for CALL_PHONE + READ_PHONE_STATE + RECEIVE_SMS to execute real SIM USSD
    val telephonyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshDetectedSimCards(context)
        val callGranted = permissions[Manifest.permission.CALL_PHONE] == true ||
            RealTelecomEngine.hasCallPhonePermission(context)
        val targetCode = pendingUssdCodeForLiveSim ?: viewModel.selectedCarrier.balanceUssd
        if (callGranted) {
            viewModel.executeRealSimUssdBalanceQuery(context, targetCode)
        } else {
            launchUssdDialer(context, targetCode) { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            }
        }
    }

    // 2. Runtime Permission Launcher for Real Direct SMS Inquiry (SEND_SMS + RECEIVE_SMS + READ_SMS + READ_PHONE_STATE)
    val directSmsInquiryPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshDetectedSimCards(context)
        val sendGranted = permissions[Manifest.permission.SEND_SMS] == true
        if (sendGranted || RealTelecomEngine.hasSmsPermissions(context)) {
            viewModel.sendRealSmsBalanceInquiry(context)
        } else {
            val (num, body) = when (viewModel.selectedCarrier) {
                TelecomCarrier.YEMEN_MOBILE -> "181" to "رصيد"
                TelecomCarrier.SABAFON -> "211" to "رصيد"
                TelecomCarrier.YOU -> "111" to "رصيد"
                TelecomCarrier.YEMEN_NET -> "114" to "رصيد"
            }
            launchSmsInquiry(context, num, body)
        }
    }

    // 3. Runtime Permission Launcher for Reading Real Carrier SMS Inbox (READ_SMS + RECEIVE_SMS)
    val inboxReaderPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[Manifest.permission.READ_SMS] == true ||
            RealTelecomEngine.hasReadSmsPermission(context)
        if (readGranted) {
            viewModel.scanRealSmsInbox(context)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("يرجى منح إذن قراءة الرسائل لقراءة رسائل 181 الحقيقية من الهاتف")
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDetectedSimCards(context)
        if (RealTelecomEngine.hasReadSmsPermission(context)) {
            viewModel.scanRealSmsInbox(context)
        }
    }

    if (viewModel.currentTab != AppTab.QUERY) {
        BackHandler {
            viewModel.selectTab(AppTab.QUERY)
        }
    }

    LaunchedEffect(viewModel.statusBannerMessage, viewModel.errorMessage) {
        val message = viewModel.errorMessage ?: viewModel.statusBannerMessage
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RoyalMaroon,
                            border = BorderStroke(1.dp, ImperialGold)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = null,
                                tint = SoftChampagneGold,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "رصيد يمن موبايل",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "استعلام واستلام حقيقي مباشر (USSD & SMS)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ImperialGold
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.5f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleDarkTheme() },
                            modifier = Modifier.testTag("theme_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (viewModel.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (viewModel.isDarkTheme) "تفعيل الوضع النهاري" else "تفعيل الوضع الليلي",
                                tint = ImperialGold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val selected = viewModel.currentTab == tab
                    val icon = when (tab) {
                        AppTab.QUERY -> if (selected) Icons.Filled.PhoneAndroid else Icons.Outlined.PhoneAndroid
                        AppTab.PACKAGES -> if (selected) Icons.Filled.LocalOffer else Icons.Outlined.LocalOffer
                        AppTab.CALCULATOR -> if (selected) Icons.Filled.Calculate else Icons.Outlined.Calculate
                        AppTab.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.titleAr
                            )
                        },
                        label = {
                            Text(
                                text = tab.titleAr,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SoftChampagneGold,
                            selectedTextColor = ImperialGold,
                            indicatorColor = RoyalMaroon,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.route}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (viewModel.currentTab) {
                AppTab.QUERY -> {
                    QueryDashboardScreen(
                        selectedCarrier = viewModel.selectedCarrier,
                        selectedSimSlot = viewModel.selectedSimSlot,
                        detectedSimCards = viewModel.detectedSimCards,
                        autoDetectedCarrier = viewModel.autoDetectedCarrier,
                        phoneInput = viewModel.phoneInput,
                        subscriberLabelInput = viewModel.subscriberLabelInput,
                        customUssdCodeInput = viewModel.customUssdCodeInput,
                        rawSmsOrUssdPasteInput = viewModel.rawSmsOrUssdPasteInput,
                        portalBaseUrlInput = viewModel.portalBaseUrlInput,
                        isLoading = viewModel.isLoading,
                        isWaitingForRealSmsReply = viewModel.isWaitingForRealSmsReply,
                        lastAutoReceivedSmsInfo = viewModel.lastAutoReceivedSmsInfo,
                        inboxCarrierMessages = viewModel.inboxCarrierMessages,
                        errorMessage = viewModel.errorMessage,
                        latestResult = viewModel.latestResult,
                        activeOtpSession = viewModel.activeOtpSession,
                        otpDigitsInput = viewModel.otpDigitsInput,
                        otpRemainingSeconds = viewModel.otpRemainingSeconds,
                        favoriteRecords = viewModel.getFavoriteRecords(),
                        onCarrierSelected = viewModel::selectCarrier,
                        onSimSlotSelected = viewModel::selectSimSlot,
                        onPhoneChanged = viewModel::updatePhoneInput,
                        onLabelChanged = viewModel::updateSubscriberLabelInput,
                        onCustomUssdCodeChanged = viewModel::updateCustomUssdCodeInput,
                        onRawSmsPasteChanged = viewModel::updateRawSmsPasteInput,
                        onParseSmsTextClick = {
                            viewModel.parseAndSaveRealCarrierText(viewModel.rawSmsOrUssdPasteInput)
                        },
                        onPasteFromClipboard = {
                            val pasted = readFromClipboard(context)
                            if (pasted.isNotBlank()) {
                                viewModel.updateRawSmsPasteInput(pasted)
                                viewModel.parseAndSaveRealCarrierText(pasted)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("الحافظة فارغة حالياً")
                                }
                            }
                        },
                        onPortalBaseUrlChanged = viewModel::updatePortalBaseUrl,
                        onLiveSimUssdQueryClick = { ussdCode ->
                            pendingUssdCodeForLiveSim = ussdCode
                            if (RealTelecomEngine.hasCallPhonePermission(context)) {
                                viewModel.executeRealSimUssdBalanceQuery(context, ussdCode)
                            } else {
                                telephonyPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.RECEIVE_SMS
                                    )
                                )
                            }
                        },
                        onSendDirectRealSmsInquiryClick = {
                            if (RealTelecomEngine.hasSmsPermissions(context)) {
                                viewModel.sendRealSmsBalanceInquiry(context)
                            } else {
                                directSmsInquiryPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.SEND_SMS,
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.READ_PHONE_STATE
                                    )
                                )
                            }
                        },
                        onScanRealSmsInboxClick = {
                            if (RealTelecomEngine.hasReadSmsPermission(context)) {
                                viewModel.scanRealSmsInbox(context)
                            } else {
                                inboxReaderPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_SMS,
                                        Manifest.permission.RECEIVE_SMS
                                    )
                                )
                            }
                        },
                        onSelectInboxSmsItem = viewModel::selectInboxSmsAsResult,
                        onDialUssdClick = { ussdCode ->
                            launchUssdDialer(context, ussdCode) { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        onSendSmsInquiryClick = { number, body ->
                            launchSmsInquiry(context, number, body)
                        },
                        onPortalQueryClick = {
                            if (!RealTelecomEngine.hasReadSmsPermission(context)) {
                                inboxReaderPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECEIVE_SMS,
                                        Manifest.permission.READ_SMS
                                    )
                                )
                            }
                            viewModel.initiateRealPortalOtpRequest()
                        },
                        onCopyText = { text, feedback ->
                            copyToClipboard(context, text)
                            coroutineScope.launch { snackbarHostState.showSnackbar(feedback) }
                        },
                        onShareReport = { reportText ->
                            sharePlainText(context, "تقرير رصيد مشترك", reportText)
                        },
                        onToggleFavorite = viewModel::toggleFavoriteRecord,
                        onSelectFavoriteRecord = viewModel::selectRecordForInspection,
                        onOtpInputChanged = viewModel::updateOtpInput,
                        onConfirmOtp = viewModel::confirmRealOtpAndFetchResult,
                        onDismissOtp = viewModel::dismissOtpDialog
                    )
                }

                AppTab.PACKAGES -> {
                    PackagesUssdScreen(
                        selectedCarrier = viewModel.selectedCarrier,
                        selectedCategory = viewModel.selectedPackageCategory,
                        searchQuery = viewModel.packageSearchQuery,
                        packages = viewModel.getFilteredPackages(),
                        quickCodes = viewModel.getCarrierQuickUssdCodes(),
                        onCarrierSelected = viewModel::selectCarrier,
                        onCategorySelected = viewModel::selectPackageCategory,
                        onSearchChanged = viewModel::updatePackageSearch,
                        onDialUssd = { code ->
                            pendingUssdCodeForLiveSim = code
                            if (RealTelecomEngine.hasCallPhonePermission(context)) {
                                viewModel.executeRealSimUssdBalanceQuery(context, code)
                            } else {
                                launchUssdDialer(context, code) { msg ->
                                    coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            }
                        },
                        onCopyCode = { code, msg ->
                            copyToClipboard(context, code)
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                AppTab.CALCULATOR -> {
                    SmartCalculatorScreen(
                        carrier = viewModel.selectedCarrier,
                        calcAmountInput = viewModel.calcAmountInput,
                        calcIsGrossToNet = viewModel.calcIsGrossToNet,
                        calcDataGbInput = viewModel.calcDataGbInput,
                        calcDaysInput = viewModel.calcDaysInput,
                        onAmountChanged = viewModel::updateCalcAmount,
                        onToggleCalcMode = viewModel::toggleCalcMode,
                        onDataGbChanged = viewModel::updateCalcDataGb,
                        onDaysChanged = viewModel::updateCalcDays,
                        onCopySummary = { text, msg ->
                            copyToClipboard(context, text)
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    )
                }

                AppTab.HISTORY -> {
                    HistoryExportScreen(
                        historySearchQuery = viewModel.historySearchQuery,
                        historyOnlyFavorites = viewModel.historyOnlyFavorites,
                        selectedCarrierFilter = viewModel.selectedCarrierFilter,
                        records = viewModel.getFilteredHistoryRecords(),
                        onSearchChanged = viewModel::updateHistorySearch,
                        onToggleFavoritesOnly = viewModel::toggleHistoryFavoritesFilter,
                        onCarrierFilterSelected = viewModel::selectHistoryCarrierFilter,
                        onInspectRecord = viewModel::selectRecordForInspection,
                        onToggleFavorite = viewModel::toggleFavoriteRecord,
                        onDeleteRecord = viewModel::deleteHistoryRecord,
                        onClearAll = viewModel::clearAllHistory,
                        onExportCsv = {
                            val csvContent = viewModel.generateExportContent(asCsv = true)
                            copyToClipboard(context, csvContent)
                            sharePlainText(context, "تصدير سجل الاستعلامات (CSV)", csvContent)
                        },
                        onShareFormattedReport = {
                            val reportContent = viewModel.generateExportContent(asCsv = false)
                            sharePlainText(context, "تقرير سجل رصيد الشبكات اليمنية", reportContent)
                        }
                    )
                }
            }
        }
    }
}

private fun launchUssdDialer(
    context: Context,
    ussdCode: String,
    onFallbackMessage: (String) -> Unit
) {
    try {
        val encodedHash = Uri.encode("#")
        val dialString = ussdCode.replace("#", encodedHash)
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialString")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        copyToClipboard(context, ussdCode)
        onFallbackMessage("تم نسخ الكود $ussdCode للاتصال المباشر")
    }
}

private fun launchSmsInquiry(context: Context, phoneNumber: String, messageBody: String) {
    try {
        val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phoneNumber")).apply {
            putExtra("sms_body", messageBody)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(smsIntent)
    } catch (_: Exception) {
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("yemen_telecom_clip", text))
}

private fun readFromClipboard(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val item = clipboard?.primaryClip?.getItemAt(0)
    return item?.text?.toString().orEmpty()
}

private fun sharePlainText(context: Context, title: String, content: String) {
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, content)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        copyToClipboard(context, content)
    }
}
