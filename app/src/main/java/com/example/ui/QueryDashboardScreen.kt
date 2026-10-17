package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.OtpSessionItem
import com.example.QueryRecordItem
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
    autoDetectedCarrier: Boolean,
    phoneInput: String,
    subscriberLabelInput: String,
    isLoading: Boolean,
    errorMessage: String?,
    latestResult: QueryRecordItem?,
    activeOtpSession: OtpSessionItem?,
    otpDigitsInput: String,
    otpRemainingSeconds: Int,
    favoriteRecords: List<QueryRecordItem>,
    onCarrierSelected: (TelecomCarrier) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onPortalQueryClick: () -> Unit,
    onDialUssdClick: (String) -> Unit,
    onCopyText: (String, String) -> Unit,
    onShareReport: (String) -> Unit,
    onToggleFavorite: (QueryRecordItem) -> Unit,
    onSelectFavoriteRecord: (QueryRecordItem) -> Unit,
    onOtpInputChanged: (String) -> Unit,
    onAutoFillOtp: () -> Unit,
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
        item {
            LuxuryCarrierHeroBanner(
                selectedCarrier = selectedCarrier,
                autoDetected = autoDetectedCarrier,
                onCarrierSelected = onCarrierSelected
            )
        }

        if (favoriteRecords.isNotEmpty()) {
            item {
                FavoriteNumbersQuickRow(
                    favorites = favoriteRecords,
                    onSelect = onSelectFavoriteRecord
                )
            }
        }

        item {
            SmartPhoneQueryCard(
                carrier = selectedCarrier,
                phoneInput = phoneInput,
                subscriberLabel = subscriberLabelInput,
                isLoading = isLoading,
                onPhoneChanged = onPhoneChanged,
                onLabelChanged = onLabelChanged,
                onPortalQueryClick = onPortalQueryClick,
                onDialUssdClick = { onDialUssdClick(selectedCarrier.balanceUssd) }
            )
        }

        latestResult?.let { result ->
            item {
                BalanceResultLuxuryCard(
                    record = result,
                    onToggleFavorite = { onToggleFavorite(result) },
                    onShareReport = {
                        val reportText = buildSingleSubscriberShareText(result)
                        onShareReport(reportText)
                    },
                    onCopyPhone = {
                        onCopyText(result.phoneNumber, "تم نسخ رقم الهاتف ${result.phoneNumber}")
                    },
                    onRequery = onPortalQueryClick
                )
            }
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
            onAutoFillDemo = onAutoFillOtp,
            onVerifyClick = onConfirmOtp,
            onDismiss = onDismissOtp
        )
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
                                text = "بوابة ${selectedCarrier.arabicName}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "البادئات المدعومة: ${selectedCarrier.prefixes.joinToString(" • ")} | ${selectedCarrier.shortTag}",
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
                    text = "اختر الشبكة أو أدخل الرقم للتعرف التلقائي:",
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
                text = "الأرقام المفضلة للاستعلام السريع",
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
private fun SmartPhoneQueryCard(
    carrier: TelecomCarrier,
    phoneInput: String,
    subscriberLabel: String,
    isLoading: Boolean,
    onPhoneChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onPortalQueryClick: () -> Unit,
    onDialUssdClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_phone_query_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
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
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "بيانات الاستعلام المباشر",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "بوابة ${carrier.arabicName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ImperialGold
                )
            }

            OutlinedTextField(
                value = phoneInput,
                onValueChange = onPhoneChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_number_input"),
                label = { Text("رقم المشترك (${carrier.prefixes.joinToString("/")})") },
                placeholder = { Text("مثال: 777889900") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Dialpad,
                        contentDescription = "رقم الهاتف",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (phoneInput.isNotEmpty()) {
                        Text(
                            text = "${phoneInput.length}/9",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            OutlinedTextField(
                value = subscriberLabel,
                onValueChange = onLabelChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("subscriber_label_input"),
                label = { Text("اسم أو ملاحظة للرقم في السجل (اختياري)") },
                placeholder = { Text("مثال: شريحة العمل أو رقم المنزل") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ImperialGold,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPortalQueryClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .weight(1.35f)
                        .height(54.dp)
                        .testTag("portal_query_button"),
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
                    } else {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = SoftChampagneGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "استعلام عبر رمز OTP",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDialUssdClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("ussd_direct_button"),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, ImperialGold)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = ImperialGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "كود ${carrier.balanceUssd}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
    onCopyPhone: () -> Unit,
    onRequery: () -> Unit
) {
    val carrier = TelecomCarrier.fromId(record.carrierId)
    val numberFormat = NumberFormat.getNumberInstance(Locale.US)
    val formattedBalance = numberFormat.format(record.balanceRial.toInt())
    val timeFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.US)

    val dataRatio by animateFloatAsState(
        targetValue = (record.remainingDataMb.toFloat() / record.totalDataMb.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "dataRatio"
    )
    val voiceRatio by animateFloatAsState(
        targetValue = (record.remainingMinutes.toFloat() / record.totalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "voiceRatio"
    )
    val smsRatio by animateFloatAsState(
        targetValue = (record.remainingSms.toFloat() / record.totalSms.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "smsRatio"
    )

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = record.phoneNumber,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = ImperialGold
                            )
                            IconButton(
                                onClick = onCopyPhone,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ الرقم",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
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
                                text = "الرصيد الأساسي المتوفر",
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

                    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "الباقة النشطة حالياً",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = record.activePackageName,
                                style = MaterialTheme.typography.labelLarge,
                                color = SoftChampagneGold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "صلاحية الباقة والخط",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "حتى ${record.expiryDate}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Text(
                text = "تفاصيل الرصيد المتبقي من الباقة",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            QuotaMeterItem(
                icon = Icons.Default.Speed,
                title = "بيانات الإنترنت (4G / ميجابايت)",
                valueText = "${formatDataQuota(record.remainingDataMb)} متبقية من ${formatDataQuota(record.totalDataMb)}",
                progress = dataRatio,
                accentColor = ImperialGold
            )

            QuotaMeterItem(
                icon = Icons.Default.SignalCellularAlt,
                title = "دقائق الاتصال داخل الشبكة",
                valueText = "${record.remainingMinutes} دقيقة متبقية من ${record.totalMinutes}",
                progress = voiceRatio,
                accentColor = MaterialTheme.colorScheme.primary
            )

            QuotaMeterItem(
                icon = Icons.AutoMirrored.Filled.Message,
                title = "الرسائل النصية (SMS)",
                valueText = "${record.remainingSms} رسالة متبقية من ${record.totalSms}",
                progress = smsRatio,
                accentColor = EmeraldSuccess
            )

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
                        text = "آخر تحديث: ${timeFormat.format(Date(record.timestampMillis))}",
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
                    Text("تحديث الآن")
                }
            }
        }
    }
}

@Composable
private fun QuotaMeterItem(
    icon: ImageVector,
    title: String,
    valueText: String,
    progress: Float,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                strokeCap = StrokeCap.Round
            )

            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    onAutoFillDemo: () -> Unit,
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
                        text = "التحقق الذكي عبر الرسائل (OTP)",
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
                    text = "تم إرسال رمز التحقق المكون من 6 أرقام عبر بوابة $carrierAr. يقوم التطبيق بالتقاط الرمز تلقائياً فور وصول الرسالة:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0 until 6) {
                        val charStr = otpInput.getOrNull(i)?.toString() ?: "•"
                        val isFilled = i < otpInput.length
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isFilled) RoyalMaroon.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                width = if (isFilled) 1.5.dp else 1.dp,
                                color = if (isFilled) ImperialGold else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = charStr,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFilled) ImperialGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = otpInput,
                    onValueChange = onOtpChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("otp_code_input"),
                    label = { Text("رمز التحقق (6 أرقام)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "صلاحية الرمز: $remainingSeconds ثانية",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = onAutoFillDemo,
                        modifier = Modifier.testTag("otp_autofill_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ImperialGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "التقاط فوري (${session.generatedOtpCode})",
                            color = ImperialGold
                        )
                    }
                }

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
                    Text("تأكيد وعرض الرصيد")
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
        ━━━ بطاقة رصيد ${carrier.arabicName} ━━━
        • الرقم: ${record.phoneNumber} (${record.subscriberLabel})
        • الرصيد الأساسي: ${record.balanceRial.toInt()} ريال يمني
        • حالة الخط: ${record.lineStatus}
        • الباقة الفعالة: ${record.activePackageName}
        • الإنترنت المتبقي: ${formatDataQuota(record.remainingDataMb)}
        • الدقائق المتبقية: ${record.remainingMinutes} دقيقة
        • الرسائل المتبقية: ${record.remainingSms} رسالة
        • تاريخ انتهاء الصلاحية: ${record.expiryDate}
        • مرجع التحقق: ${record.verificationRef}
    """.trimIndent()
}
