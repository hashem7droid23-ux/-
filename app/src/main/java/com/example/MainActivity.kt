package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppPreferences
import com.example.data.AppScreen
import com.example.ui.BalanceResultScreen
import com.example.ui.BalanceViewModel
import com.example.ui.OtpVerifyScreen
import com.example.ui.QueryScreen
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.YemenBalanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = AppPreferences(applicationContext)

        setContent {
            val viewModel: BalanceViewModel = viewModel(
                factory = BalanceViewModel.provideFactory(prefs)
            )

            YemenBalanceTheme(
                carrier = viewModel.activeCarrier,
                darkTheme = viewModel.isDarkTheme
            ) {
                BalanceAppRoot(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BalanceAppRoot(viewModel: BalanceViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Optional RECEIVE_SMS permission for automatic OTP capture on Screen 2
    val receiveSmsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Manual OTP entry works regardless of permission result
    }

    LaunchedEffect(viewModel.currentScreen, viewModel.isDemoMode) {
        if (viewModel.currentScreen == AppScreen.OTP && !viewModel.isDemoMode) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                receiveSmsLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
        }
    }

    LaunchedEffect(viewModel.infoMessage) {
        val msg = viewModel.infoMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessages()
        }
    }

    // Back navigation for Screens 2 and 3
    BackHandler(enabled = viewModel.currentScreen != AppScreen.QUERY) {
        when (viewModel.currentScreen) {
            AppScreen.OTP -> viewModel.cancelOtpAndReturn()
            AppScreen.RESULT -> viewModel.startNewNumber()
            AppScreen.QUERY -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Demo Mode Top Banner
                AnimatedVisibility(visible = viewModel.isDemoMode) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("demo_mode_banner"),
                        color = StatusWarning
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Science,
                                contentDescription = null,
                                tint = Color(0xFF1F1600),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "  وضع تجريبي",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F1600)
                            )
                        }
                    }
                }

                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .padding(7.dp)
                                        .size(18.dp)
                                )
                            }
                            Text(
                                text = "استعلام الرصيد",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleDarkTheme() },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (viewModel.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = if (viewModel.isDarkTheme) "الوضع النهاري" else "الوضع الليلي",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = viewModel.currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                AppScreen.QUERY -> {
                    QueryScreen(
                        phoneInput = viewModel.phoneInput,
                        activeCarrier = viewModel.activeCarrier,
                        isLoading = viewModel.isLoading,
                        errorMessage = viewModel.errorMessage,
                        isDemoMode = viewModel.isDemoMode,
                        showDeveloperDialog = viewModel.showDeveloperDialog,
                        sabafonVerifyPath = viewModel.sabafonVerifyPath,
                        sabafonBalancePath = viewModel.sabafonBalancePath,
                        sabafonPackagesPath = viewModel.sabafonPackagesPath,
                        diagnosticLog = viewModel.getDiagnosticLog(),
                        onPhoneChanged = viewModel::updatePhoneInput,
                        onQueryClick = viewModel::submitPhoneInquiry,
                        onVersionTap = viewModel::onVersionTapped,
                        onToggleDemoMode = viewModel::setDemoModeEnabled,
                        onSaveDeveloperPaths = viewModel::saveSabafonDeveloperPaths,
                        onResetDeveloperPaths = viewModel::resetSabafonDeveloperPaths,
                        onCopyDiagnosticLog = { logText ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("diagnostic_log", logText))
                        },
                        onDismissDeveloperDialog = viewModel::dismissDeveloperDialog
                    )
                }

                AppScreen.OTP -> {
                    OtpVerifyScreen(
                        phoneNumber = viewModel.phoneInput,
                        carrier = viewModel.activeCarrier,
                        otpInput = viewModel.otpInput,
                        countdownSeconds = viewModel.otpCountdownSeconds,
                        isLoading = viewModel.isLoading,
                        errorMessage = viewModel.errorMessage,
                        onOtpChanged = viewModel::updateOtpInput,
                        onVerifyClick = viewModel::verifyOtp,
                        onResendClick = viewModel::resendOtp,
                        onCancelClick = viewModel::cancelOtpAndReturn
                    )
                }

                AppScreen.RESULT -> {
                    val result = viewModel.latestResult
                    if (result != null) {
                        BalanceResultScreen(
                            result = result,
                            isLoading = viewModel.isLoading,
                            errorMessage = viewModel.errorMessage,
                            onRefreshClick = viewModel::refreshCurrentBalance,
                            onNewNumberClick = viewModel::startNewNumber
                        )
                    }
                }
            }
        }
    }
}
