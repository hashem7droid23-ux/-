package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.CarrierType
import com.example.ui.theme.SabafonBlueDeep
import com.example.ui.theme.SabafonBluePrimary
import com.example.ui.theme.YemenRedDeep
import com.example.ui.theme.YemenRedPrimary

@Composable
fun QueryScreen(
    phoneInput: String,
    activeCarrier: CarrierType,
    isLoading: Boolean,
    errorMessage: String?,
    isDemoMode: Boolean,
    showDeveloperDialog: Boolean,
    sabafonVerifyPath: String,
    sabafonBalancePath: String,
    sabafonPackagesPath: String,
    diagnosticLog: String,
    isDiscoveringRoutes: Boolean,
    onPhoneChanged: (String) -> Unit,
    onQueryClick: () -> Unit,
    onVersionTap: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onSaveDeveloperPaths: (String, String, String) -> Unit,
    onResetDeveloperPaths: () -> Unit,
    onDiscoverSabafonRoutes: () -> Unit,
    onCopyDiagnosticLog: (String) -> Unit,
    onDismissDeveloperDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headerGradient = when (activeCarrier) {
        CarrierType.YEMEN_MOBILE -> listOf(YemenRedPrimary, YemenRedDeep)
        CarrierType.SABAFON -> listOf(SabafonBluePrimary, SabafonBlueDeep)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Carrier Identity Card (Automatically transitions Red <-> Blue)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("carrier_banner_card"),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.linearGradient(headerGradient))
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = activeCarrier.arabicName,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(14.dp)
                                    .size(32.dp)
                            )
                        }

                        Text(
                            text = activeCarrier.arabicName,
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "الاستعلام السريع عن الرصيد والباقات الحالية",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )

                        // Carrier Prefix Indicator Pills
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CarrierIndicatorPill(
                                label = "يمن موبايل (77 / 78)",
                                selected = activeCarrier == CarrierType.YEMEN_MOBILE
                            )
                            CarrierIndicatorPill(
                                label = "سبأفون (71)",
                                selected = activeCarrier == CarrierType.SABAFON
                            )
                        }
                    }
                }
            }

            // Main Phone Input & Query Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_query_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "أدخل رقم الهاتف",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Phone input displayed strictly LTR so digits read naturally left-to-right
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = onPhoneChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("phone_number_input"),
                            placeholder = {
                                Text(
                                    text = "77xxxxxxx / 71xxxxxxx",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            },
                            prefix = {
                                Text(
                                    text = "+967 ",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.PhoneAndroid,
                                    contentDescription = "رقم الهاتف",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (phoneInput.isNotEmpty() && !isLoading) {
                                    IconButton(onClick = { onPhoneChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "مسح الرقم",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                letterSpacing = 1.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            singleLine = true,
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onQueryClick() }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = !errorMessage.isNullOrBlank(),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = onQueryClick,
                        enabled = !isLoading && phoneInput.length == 9,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("submit_query_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "جاري الاستعلام...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "استعلام",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Version footer (5 taps opens Developer / Demo menu)
            Text(
                text = "الإصدار ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onVersionTap
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("app_version_text")
            )
        }
    }

    if (showDeveloperDialog) {
        DeveloperOptionsDialog(
            isDemoMode = isDemoMode,
            sabafonVerifyPath = sabafonVerifyPath,
            sabafonBalancePath = sabafonBalancePath,
            sabafonPackagesPath = sabafonPackagesPath,
            diagnosticLog = diagnosticLog,
            isDiscoveringRoutes = isDiscoveringRoutes,
            onToggleDemoMode = onToggleDemoMode,
            onSavePaths = onSaveDeveloperPaths,
            onResetPaths = onResetDeveloperPaths,
            onDiscoverSabafonRoutes = onDiscoverSabafonRoutes,
            onCopyDiagnosticLog = onCopyDiagnosticLog,
            onDismiss = onDismissDeveloperDialog
        )
    }
}

@Composable
private fun CarrierIndicatorPill(
    label: String,
    selected: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 1f else 0.3f))
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFF1A1214) else Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun DeveloperOptionsDialog(
    isDemoMode: Boolean,
    sabafonVerifyPath: String,
    sabafonBalancePath: String,
    sabafonPackagesPath: String,
    diagnosticLog: String,
    isDiscoveringRoutes: Boolean,
    onToggleDemoMode: (Boolean) -> Unit,
    onSavePaths: (String, String, String) -> Unit,
    onResetPaths: () -> Unit,
    onDiscoverSabafonRoutes: () -> Unit,
    onCopyDiagnosticLog: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var verifyField by remember(sabafonVerifyPath) { mutableStateOf(sabafonVerifyPath) }
    var balanceField by remember(sabafonBalancePath) { mutableStateOf(sabafonBalancePath) }
    var packagesField by remember(sabafonPackagesPath) { mutableStateOf(sabafonPackagesPath) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = "قائمة المطور",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضع تجريبي (Demo)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "عرض بيانات تجريبية بدون اتصال إنترنت بعد 1.5 ثانية",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isDemoMode,
                        onCheckedChange = onToggleDemoMode,
                        modifier = Modifier.testTag("demo_mode_switch")
                    )
                }

                HorizontalDivider()

                Text(
                    text = "مسارات سبأفون (اختياري للمطور)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedButton(
                    onClick = onDiscoverSabafonRoutes,
                    enabled = !isDiscoveringRoutes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_sabafon_routes_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isDiscoveringRoutes) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("جاري فحص مسارات سبأفون...")
                    } else {
                        Text("اكتشاف مسارات سبأفون تلقائياً")
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = verifyField,
                            onValueChange = { verifyField = it },
                            label = { Text("Verify Path") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = balanceField,
                            onValueChange = { balanceField = it },
                            label = { Text("Balance Path") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = packagesField,
                            onValueChange = { packagesField = it },
                            label = { Text("Packages Path") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل التشخيص (HTTP)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { onCopyDiagnosticLog(diagnosticLog) }) {
                        Text("نسخ السجل")
                    }
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = diagnosticLog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSavePaths(verifyField, balanceField, packagesField) }) {
                Text("حفظ وإغلاق")
            }
        },
        dismissButton = {
            TextButton(onClick = onResetPaths) {
                Text("الافتراضي")
            }
        }
    )
}
