package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.TelecomRepository
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

        val database = AppDatabase.getInstance(applicationContext)
        val repository = TelecomRepository(applicationContext, database.queryRecordDao())

        setContent {
            val viewModel: TelecomViewModel = viewModel(
                factory = TelecomViewModel.provideFactory(repository)
            )
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            YemenBalanceTheme(darkTheme = isDarkTheme) {
                YemenTelecomAppRoot(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YemenTelecomAppRoot(
    viewModel: TelecomViewModel,
    isDarkTheme: Boolean
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredHistoryRecords.collectAsStateWithLifecycle()
    val favoriteRecords = remember(allRecords) { allRecords.filter { it.isFavorite } }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Handle system back button when on secondary tabs
    if (uiState.currentTab != AppTab.QUERY) {
        BackHandler {
            viewModel.selectTab(AppTab.QUERY)
        }
    }

    LaunchedEffect(uiState.statusBannerMessage, uiState.errorMessage) {
        val message = uiState.errorMessage ?: uiState.statusBannerMessage
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
                                text = "الاستعلام الذكي وخدمات الشبكات اليمنية",
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
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDarkTheme) "تفعيل الوضع النهاري" else "تفعيل الوضع الليلي",
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
                    val selected = uiState.currentTab == tab
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
            when (uiState.currentTab) {
                AppTab.QUERY -> {
                    QueryDashboardScreen(
                        uiState = uiState,
                        favoriteRecords = favoriteRecords,
                        onCarrierSelected = viewModel::selectCarrier,
                        onPhoneChanged = viewModel::updatePhoneInput,
                        onLabelChanged = viewModel::updateSubscriberLabelInput,
                        onPortalQueryClick = viewModel::initiatePortalBalanceQuery,
                        onDialUssdClick = { ussdCode ->
                            launchUssdDialer(context, ussdCode) { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
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
                        onAutoFillOtp = viewModel::autoFillDemoOtp,
                        onConfirmOtp = viewModel::confirmOtpAndFetchResult,
                        onDismissOtp = viewModel::dismissOtpDialog
                    )
                }

                AppTab.PACKAGES -> {
                    PackagesUssdScreen(
                        selectedCarrier = uiState.selectedCarrier,
                        selectedCategory = uiState.selectedPackageCategory,
                        searchQuery = uiState.packageSearchQuery,
                        packages = viewModel.getFilteredPackages(),
                        quickCodes = viewModel.getCarrierQuickUssdCodes(),
                        onCarrierSelected = viewModel::selectCarrier,
                        onCategorySelected = viewModel::selectPackageCategory,
                        onSearchChanged = viewModel::updatePackageSearch,
                        onDialUssd = { code ->
                            launchUssdDialer(context, code) { msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
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
                        uiState = uiState,
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
                        uiState = uiState,
                        records = filteredRecords,
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("yemen_telecom_clip", text))
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
