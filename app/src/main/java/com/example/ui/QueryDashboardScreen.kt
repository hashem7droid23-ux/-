package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.OtpSessionItem
import com.example.QueryRecordItem
import com.example.RealInboxSmsItem
import com.example.RealSimSlotInfo
import com.example.data.TelecomCarrier
import com.example.ui.theme.DeepMaroon
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ImperialGold
import com.example.ui.theme.RoyalMaroon
import com.example.ui.theme.SoftChampagneGold
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueryDashboardScreen(
    selectedCarrier: TelecomCarrier,
    selectedSimSlot: Int,
    detectedSimCards: List<RealSimSlotInfo>,
    autoDetectedCarrier: Boolean,
    phoneInput: String,
    subscriberLabelInput: String,
    customUssdCodeInput: String,
    rawSmsOrUssdPasteInput: String,
    portalBaseUrlInput: String,
    isLoading: Boolean,
    isWaitingForRealSmsReply: Boolean,
    lastAutoReceivedSmsInfo: String?,
    inboxCarrierMessages: List<RealInboxSmsItem>,
    errorMessage: String?,
    latestResult: QueryRecordItem?,
    activeOtpSession: OtpSessionItem?,
    otpDigitsInput: String,
    otpRemainingSeconds: Int,
    favoriteRecords: List<QueryRecordItem>,
    onCarrierSelected: (TelecomCarrier) -> Unit,
    onSimSlotSelected: (Int) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onCustomUssdCodeChanged: (String) -> Unit,
    onRawSmsPasteChanged: (String) -> Unit,
    onParseSmsTextClick: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onPortalBaseUrlChanged: (String) -> Unit,
    onLiveSimUssdQueryClick: (String) -> Unit,
    onSendDirectRealSmsInquiryClick: () -> Unit,
    onScanRealSmsInboxClick: () -> Unit,
    onSelectInboxSmsItem: (RealInboxSmsItem) -> Unit,
    onDialUssdClick: (String) -> Unit,
    onSendSmsInquiryClick: (String, String) -> Unit,
    onPortalQueryClick: () -> Unit,
    onCopyText: (String, String) -> Unit,
    onShareReport: (String) -> Unit,
    onToggleFavorite: (QueryRecordItem) -> Unit,
    onSelectFavoriteRecord: (QueryRecordItem) -> Unit,
    onOtpInputChanged: (String) -> Unit,
    onConfirmOtp: () -> Unit,
    onDismissOtp: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("query_dashboard_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Header & Carrier Switcher
        item {
            LuxuryCarrierHeroBanner(
                selectedCarrier = selectedCarrier,
                autoDetected = autoDetectedCarrier,
                onCarrierSelected = onCarrierSelected
            )
        }

        // 2. Live Incoming SMS Auto-Capture Banner (if a real SMS was just intercepted)
        if (isWaitingForRealSmsReply || !lastAutoReceivedSmsInfo.isNullOrBlank()) {
            item {
                LiveSmsReceiverStatusBanner(
                    isWaiting = isWaitingForRealSmsReply,
                    lastReceivedMessage = lastAutoReceivedSmsInfo,
                    carrierName = selectedCarrier.arabicName
                )
            }
        }

        // 3. Favorite Numbers Quick Row
        if (favoriteRecords.isNotEmpty()) {
            item {
                FavoriteNumbersQuickRow(
                    favorites = favoriteRecords,
                    onSelect = onSelectFavoriteRecord
                )
            }
        }

        // 4. Real SIM Card USSD + Direct SMS Inquiry + Inbox Scanner Card
        item {
            RealSimAndNetworkQueryCard(
                carrier = selectedCarrier,
                selectedSimSlot = selectedSimSlot,
                detectedSimCards = detectedSimCards,
                phoneInput = phoneInput,
                subscriberLabel = subscriberLabelInput,
                customUssdCodeInput = customUssdCodeInput,
                portalBaseUrl = portalBaseUrlInput,
                isLoading = isLoading,
                isWaitingForRealSmsReply = isWaitingForRealSmsReply,
                onSimSlotSelected = onSimSlotSelected,
                onPhoneChanged = onPhoneChanged,
                onLabelChanged = onLabelChanged,
                onCustomUssdCodeChanged = onCustomUssdCodeChanged,
                onPortalBaseUrlChanged = onPortalBaseUrlChanged,
                onLiveSimUssdBalanceClick = { onLiveSimUssdQueryClick(selectedCarrier.balanceUssd) },
                onLiveSimUssdPackagesClick = { onLiveSimUssdQueryClick(selectedCarrier.packagesUssd) },
                onRunCustomUssdClick = {
                    val code = customUssdCodeInput.ifBlank { selectedCarrier.balanceUssd }
                    onLiveSimUssdQueryClick(code)
                },
                onSendDirectRealSmsInquiryClick = onSendDirectRealSmsInquiryClick,
                onScanRealSmsInboxClick = onScanRealSmsInboxClick,
                onDialUssdClick = { onDialUssdClick(selectedCarrier.balanceUssd) },
                onSendSmsInquiryClick = {
                    when (selectedCarrier) {
                        TelecomCarrier.YEMEN_MOBILE -> onSendSmsInquiryClick("181", "رصيد")
                        TelecomCarrier.SABAFON -> onSendSmsInquiryClick("211", "رصيد")
                        TelecomCarrier.YOU -> onSendSmsInquiryClick("111", "رصيد")
                        TelecomCarrier.YEMEN_NET -> onSendSmsInquiryClick("114", "رصيد")
                    }
                },
                onPortalQueryClick = onPortalQueryClick
            )
        }

        // 5. Real Result Card (Shows the real USSD or real SMS balance response)
        latestResult?.let { result ->
            item {
                BalanceResultLuxuryCard(
                    record = result,
                    onToggleFavorite = { onToggleFavorite(result) },
                    onShareReport = {
                        val reportText = buildSingleSubscriberShareText(result)
                        onShareReport(reportText)
                    },
                    onCopyResponse = {
                        onCopyText(result.activePackageName, "تم نسخ رد الشبكة الكامل إلى الحافظة")
                    },
                    onRequery = { onLiveSimUssdQueryClick(selectedCarrier.balanceUssd) }
                )
            }
        }

        // 6. Real SMS Inbox Messages Card (Shows real telecom SMS messages read from the phone's inbox)
        if (inboxCarrierMessages.isNotEmpty()) {
            item {
                RealInboxMessagesCard(
                    messages = inboxCarrierMessages,
                    onSelectMessage = onSelectInboxSmsItem
                )
            }
        }

        // 7. Real SMS / USSD Response Analyzer Card (Paste any carrier SMS from 181/121 to extract & archive)
        item {
            RealSmsParserCard(
                carrier = selectedCarrier,
                rawText = rawSmsOrUssdPasteInput,
                onRawTextChanged = onRawSmsPasteChanged,
                onPasteClipboardClick = onPasteFromClipboard,
                onAnalyzeAndSaveClick = onParseSmsTextClick
            )
        }
    }

    activeOtpSession?.let { session ->
        OtpVerificationModal(
            session = session,
            otpInput = otpDigitsInput,
            remainingSeconds = otpRemainingSeconds,
            isLoading = isLoading,
            errorMessage = errorMessage,
            onOtpChanged = onOtpInputChanged,
            onVerifyClick = onConfirmOtp,
            onDismiss = onDismissOtp
        )
    }
}

@Composable
private fun LiveSmsReceiverStatusBanner(
    isWaiting: Boolean,
    lastReceivedMessage: String?,
    carrierName: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_sms_receiver_banner"),
        shape = RoundedCornerShape(18.dp),
        color = if (isWaiting) RoyalMaroon.copy(alpha = 0.22f) else EmeraldSuccess.copy(alpha = 0.16f),
        border = BorderStroke(
            1.dp,
            if (isWaiting) ImperialGold else EmeraldSuccess
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isWaiting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = ImperialGold,
                    strokeWidth = 2.5.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "مستقبل الرسائل الحقيقي نشط الآن...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SoftChampagneGold
                    )
                    Text(
                        text = "بانتظار وصول رسالة الرد الفعلية من شبكة $carrierName ليتم قراءتها وعرض الرصيد تلقائياً",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.MarkEmailRead,
                    contentDescription = null,
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "استلام حقيقي مباشر من الشبكة",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                    Text(
                        text = lastReceivedMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun LuxuryCarrierHeroBanner(
    selectedCarrier: TelecomCarrier,
    autoDetected: Boolean,
    onCarrierSelected: (TelecomCarrier) -> Unit
) {
    val gradientColors = when (selectedCarrier) {
        TelecomCarrier.YEMEN_MOBILE -> listOf(
            Color(0xFF8B0029),
            Color(0xFF5A001A),
            Color(0xFF2B0410)
        )
        TelecomCarrier.SABAFON -> listOf(
            Color(0xFF0054A6),
            Color(0xFF003366),
            Color(0xFF0A192F)
        )
        TelecomCarrier.YOU -> listOf(
            Color(0xFFB45309),
            Color(0xFF78350F),
            Color(0xFF271206)
        )
        TelecomCarrier.YEMEN_NET -> listOf(
            Color(0xFF0284C7),
            Color(0xFF075985),
            Color(0xFF082F49)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_carrier_banner"),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                drawCircle(
                    color = ImperialGold.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.65f,
                    center = Offset(size.width * 0.15f, size.height * 0.25f),
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = ImperialGold.copy(alpha = 0.12f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.15f, size.height * 0.25f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ImperialGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ImperialGold)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SimCard,
                                contentDescription = selectedCarrier.arabicName,
                                tint = SoftChampagneGold,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "استعلام ${selectedCarrier.arabicName} الحقيقي",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "قراءة حقيقية من الشريحة (${selectedCarrier.balanceUssd}) والرسائل (${selectedCarrier.customerServiceNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftChampagneGold.copy(alpha = 0.9f)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = ImperialGold,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (autoDetected) "تحديد تلقائي" else selectedCarrier.balanceUssd,
                                style = MaterialTheme.typography.labelSmall,
                                color = SoftChampagneGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "اختر شبكة شريحتك الحالية:",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelecomCarrier.entries.forEach { carrier ->
                        val isSelected = carrier == selectedCarrier
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onCarrierSelected(carrier) }
                                .testTag("carrier_chip_${carrier.id}"),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ImperialGold else Color.White.copy(alpha = 0.12f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f)
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = carrier.arabicName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF2B0410) else Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealSimAndNetworkQueryCard(
    carrier: TelecomCarrier,
    selectedSimSlot: Int,
    detectedSimCards: List<RealSimSlotInfo>,
    phoneInput: String,
    subscriberLabel: String,
    customUssdCodeInput: String,
    portalBaseUrl: String,
    isLoading: Boolean,
    isWaitingForRealSmsReply: Boolean,
    onSimSlotSelected: (Int) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onCustomUssdCodeChanged: (String) -> Unit,
    onPortalBaseUrlChanged: (String) -> Unit,
    onLiveSimUssdBalanceClick: () -> Unit,
    onLiveSimUssdPackagesClick: () -> Unit,
    onRunCustomUssdClick: () -> Unit,
    onSendDirectRealSmsInquiryClick: () -> Unit,
    onScanRealSmsInboxClick: () -> Unit,
    onDialUssdClick: () -> Unit,
    onSendSmsInquiryClick: () -> Unit,
    onPortalQueryClick: () -> Unit
) {
    var showAdvancedPortalConfig by remember { mutableStateOf(false) }

    val InquirySmsNumber = when (carrier) {
        TelecomCarrier.YEMEN_MOBILE -> "181"
        TelecomCarrier.SABAFON -> "211"
        TelecomCarrier.YOU -> "111"
        TelecomCarrier.YEMEN_NET -> "114"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_phone_query_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = null,
                        tint = ImperialGold
                    )
                    Text(
                        text = "الاستعلام والاستلام الحقيقي من الشريحة",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Dual SIM Selector (SIM 1 / SIM 2 with real carrier names if detected)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 1).forEach { slot ->
                        val simInfo = detectedSimCards.firstOrNull { it.slotIndex == slot }
                        val chipLabel = if (simInfo != null && simInfo.carrierName.isNotBlank()) {
                            "شريحة ${slot + 1}: ${simInfo.carrierName.take(10)}"
                        } else {
                            "شريحة ${slot + 1}"
                        }
                        FilterChip(
                            selected = selectedSimSlot == slot,
                            onClick = { onSimSlotSelected(slot) },
                            label = { Text(chipLabel, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalMaroon,
                                selectedLabelColor = SoftChampagneGold
                            )
                        )
                    }
                }
            }

            // 1. Primary Real Action: Read live USSD response inside the app via TelephonyManager.sendUssdRequest
            Button(
                onClick = onLiveSimUssdBalanceClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("live_sim_ussd_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalMaroon,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = SoftChampagneGold,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("جاري الاتصال الحقيقي بالشريحة...")
                } else {
                    Icon(
                        imageVector = Icons.Default.SimCard,
                        contentDescription = null,
                        tint = SoftChampagneGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "استعلام حقيقي عبر USSD مباشر (${carrier.balanceUssd})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // 2. Real Direct SMS Inquiry + Real Automatic Incoming SMS Receiver
            Button(
                onClick = onSendDirectRealSmsInquiryClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("real_direct_sms_inquiry_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1F4E3D),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = SoftChampagneGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isWaitingForRealSmsReply) {
                        "بانتظار استلام رد ($InquirySmsNumber) الحقيقي تلقائياً..."
                    } else {
                        "إرسال رسالة رصيد حقيقية إلى ($InquirySmsNumber) واستلام الرد تلقائياً"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // 3. Real SMS Inbox Reader Button (Reads actual carrier SMS messages stored on the device)
            FilledTonalButton(
                onClick = onScanRealSmsInboxClick,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("scan_real_sms_inbox_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = ImperialGold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "قراءة رسائل الرصيد الحقيقية من صندوق رسائل الهاتف (SMS Inbox)",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            // Secondary Real Actions Row: Live Packages USSD + Direct System Dialer + System SMS App
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLiveSimUssdPackagesClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ImperialGold)
                ) {
                    Text(
                        text = "باقات (${carrier.packagesUssd})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ImperialGold,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onDialUssdClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("ussd_direct_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "لوحة ${carrier.balanceUssd}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onSendSmsInquiryClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sms,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "تطبيق الرسائل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                }
            }

            // Custom USSD Live Runner Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customUssdCodeInput,
                    onValueChange = onCustomUssdCodeChanged,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_ussd_input"),
                    label = { Text("تنفيذ أي كود USSD مخصص (مثال: *181# أو *121#)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Button(
                    onClick = onRunCustomUssdClick,
                    enabled = !isLoading,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalMaroon,
                        contentColor = SoftChampagneGold
                    )
                ) {
                    Text("إرسال الكود")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Optional Phone Number & Label for Archiving / Portal OTP
            OutlinedTextField(
                value = phoneInput,
                onValueChange = onPhoneChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_number_input"),
                label = { Text("رقم الهاتف للحفظ في السجل أو استعلام البوابة (${carrier.prefixes.joinToString("/")})") },
                placeholder = { Text("أدخل رقمك الحقيقي (مثال: 77xxxxxxx)") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dialpad,
                        contentDescription = "رقم الهاتف",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp)
            )

            OutlinedTextField(
                value = subscriberLabel,
                onValueChange = onLabelChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subscriber_label_input"),
                label = { Text("اسم المشترك أو ملاحظة السجل (اختياري)") },
                placeholder = { Text("مثال: شريحتي الأساسية") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Portal API section (expandable)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { showAdvancedPortalConfig = !showAdvancedPortalConfig }
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsEthernet,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showAdvancedPortalConfig) "إخفاء إعدادات بوابة API" else "استعلام عبر بوابة الإنترنت (OTP API)",
                        color = ImperialGold
                    )
                }
            }

            AnimatedVisibility(visible = showAdvancedPortalConfig) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = portalBaseUrl,
                        onValueChange = onPortalBaseUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("رابط خادم البوابة (Server API URL)") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    FilledTonalButton(
                        onClick = onPortalQueryClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("portal_query_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إرسال طلب OTP حقيقي إلى الخادم (مع التقاط الرسالة تلقائياً)")
                    }
                }
            }
        }
    }
}

@Composable
private fun RealInboxMessagesCard(
    messages: List<RealInboxSmsItem>,
    onSelectMessage: (RealInboxSmsItem) -> Unit
) {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd • hh:mm a", Locale.US)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("real_inbox_messages_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = ImperialGold
                )
                Column {
                    Text(
                        text = "رسائل الشبكة الحقيقية في هاتفك (${messages.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "اضغط على أي رسالة حقيقية لعرض تفاصيل الرصيد والباقة المستخرجة منها",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            messages.take(6).forEach { msg ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectMessage(msg) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "المرسل: ${msg.sender}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ImperialGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = dateFormat.format(Date(msg.timestampMillis)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = msg.body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (msg.parsed.extractedBalanceRial != null) {
                            Text(
                                text = "الرصيد المستخرج: ${msg.parsed.extractedBalanceRial} ريال يمني",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RealSmsParserCard(
    carrier: TelecomCarrier,
    rawText: String,
    onRawTextChanged: (String) -> Unit,
    onPasteClipboardClick: () -> Unit,
    onAnalyzeAndSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("real_sms_parser_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = ImperialGold
                    )
                    Column {
                        Text(
                            text = "محلل رسائل الرصيد الحقيقية (SMS / USSD)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "الصق نص رسالة الرصيد الواردة من ${carrier.arabicName} لاستخراج الرصيد والصلاحية وأرشفتها",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rawText,
                onValueChange = onRawTextChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sms_paste_input"),
                label = { Text("نص رسالة الشبكة الفعلي") },
                placeholder = { Text("مثال: رصيدك الحالي هو 2450 ريال وينتهي بتاريخ 2026/12/01...") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPasteClipboardClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("لصق من الحافظة")
                }

                Button(
                    onClick = onAnalyzeAndSaveClick,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalMaroon,
                        contentColor = SoftChampagneGold
                    )
                ) {
                    Text("تحليل وحفظ في السجل")
                }
            }
        }
    }
}

@Composable
private fun FavoriteNumbersQuickRow(
    favorites: List<QueryRecordItem>,
    onSelect: (QueryRecordItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = ImperialGold,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "السجلات المفضلة المحفوظة",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(favorites, key = { it.id }) { fav ->
                val carrier = TelecomCarrier.fromId(fav.carrierId)
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(fav) }
                        .testTag("fav_number_chip_${fav.phoneNumber}"),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(carrier.primaryColor)
                        )
                        Column {
                            Text(
                                text = fav.subscriberLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = "${fav.phoneNumber} • ${fav.balanceRial.toInt()} ر.ي",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceResultLuxuryCard(
    record: QueryRecordItem,
    onToggleFavorite: () -> Unit,
    onShareReport: () -> Unit,
    onCopyResponse: () -> Unit,
    onRequery: () -> Unit
) {
    val carrier = TelecomCarrier.fromId(record.carrierId)
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val formattedBalance = numberFormat.format(record.balanceRial)
    val timeFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("balance_result_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, ImperialGold.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = carrier.primaryColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, carrier.primaryColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = carrier.arabicName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Column {
                        Text(
                            text = record.subscriberLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = record.phoneNumber,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = ImperialGold
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("favorite_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (record.isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "حفظ في المفضلة",
                            tint = if (record.isFavorite) ImperialGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onShareReport,
                        modifier = Modifier.testTag("share_result_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "مشاركة النتيجة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(RoyalMaroon, DeepMaroon, Color(0xFF34000F))
                        )
                    )
                    .border(1.dp, ImperialGold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = SoftChampagneGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "نتيجة رد الشبكة الحقيقي",
                                style = MaterialTheme.typography.labelLarge,
                                color = SoftChampagneGold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EmeraldSuccess.copy(alpha = 0.22f),
                            border = BorderStroke(1.dp, EmeraldSuccess)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = record.lineStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    if (record.balanceRial > 0.0) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formattedBalance,
                                style = MaterialTheme.typography.displayMedium.copy(fontSize = 34.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ريال يمني",
                                style = MaterialTheme.typography.titleMedium,
                                color = ImperialGold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }

                    if (record.loanDebtRial > 0.0) {
                        Text(
                            text = "مديونية سلفني المستخرجة: ${record.loanDebtRial.toInt()} ريال",
                            style = MaterialTheme.typography.labelMedium,
                            color = SoftChampagneGold
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    Text(
                        text = "نص رد الشبكة الفعلي الكامل:",
                        style = MaterialTheme.typography.labelSmall,
                        color = SoftChampagneGold
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.activePackageName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = onCopyResponse) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ النص",
                                    tint = SoftChampagneGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (record.remainingDataMb > 0 || record.remainingMinutes > 0 || record.remainingSms > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (record.remainingDataMb > 0) {
                                Text(
                                    text = "الإنترنت: ${formatDataQuota(record.remainingDataMb)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SoftChampagneGold
                                )
                            }
                            if (record.remainingMinutes > 0) {
                                Text(
                                    text = "الدقائق: ${record.remainingMinutes}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SoftChampagneGold
                                )
                            }
                            if (record.remainingSms > 0) {
                                Text(
                                    text = "الرسائل: ${record.remainingSms}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SoftChampagneGold
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "مرجع العملية: ${record.verificationRef}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "وقت الاستعلام: ${timeFormat.format(Date(record.timestampMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = onRequery,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("refresh_query_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة الفحص")
                }
            }
        }
    }
}

@Composable
private fun OtpVerificationModal(
    session: OtpSessionItem,
    otpInput: String,
    remainingSeconds: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onOtpChanged: (String) -> Unit,
    onVerifyClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val carrierAr = TelecomCarrier.fromId(session.carrierId).arabicName
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = RoyalMaroon.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, ImperialGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "استلام رمز التحقق الحقيقي (OTP)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "رقم المشترك: ${session.phoneNumber} ($carrierAr)",
                        style = MaterialTheme.typography.bodySmall,
                        color = ImperialGold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "سيتم التقاط رمز التحقق تلقائياً فور وصول رسالة SMS الحقيقية من $carrierAr إلى هاتفك، أو يمكنك إدخاله يدوياً:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = onOtpChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_code_input"),
                    label = { Text("رمز التحقق الفعلي (OTP)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp)
                )

                Text(
                    text = "الوقت المتبقي للجلسة: $remainingSeconds ثانية",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onVerifyClick,
                enabled = !isLoading && otpInput.length >= 4,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalMaroon,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("otp_confirm_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = SoftChampagneGold,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("تأكيد الرمز")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

private fun formatDataQuota(mb: Int): String {
    return if (mb >= 1024) {
        val gb = mb / 1024.0
        String.format(Locale.US, "%.1f جيجابايت", gb)
    } else {
        "$mb ميجابايت"
    }
}

private fun buildSingleSubscriberShareText(record: QueryRecordItem): String {
    val carrier = TelecomCarrier.fromId(record.carrierId)
    return """
        ━━━ نتيجة استعلام ${carrier.arabicName} الحقيقي ━━━
        • الرقم/الشريحة: ${record.phoneNumber} (${record.subscriberLabel})
        • الرصيد المستخرج: ${record.balanceRial} ريال يمني
        • نص رد الشبكة الفعلي:
        "${record.activePackageName}"
        • الصلاحية: ${record.expiryDate}
        • مرجع العملية: ${record.verificationRef}
    """.trimIndent()
}
