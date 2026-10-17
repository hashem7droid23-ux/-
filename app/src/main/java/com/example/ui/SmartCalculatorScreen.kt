package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.TelecomCarrier
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ImperialGold
import java.util.Locale
import kotlin.math.ceil

@Composable
fun SmartCalculatorScreen(
    uiState: TelecomUiState,
    onAmountChanged: (String) -> Unit,
    onToggleCalcMode: (Boolean) -> Unit,
    onDataGbChanged: (String) -> Unit,
    onDaysChanged: (String) -> Unit,
    onCopySummary: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val carrier = uiState.selectedCarrier
    val taxRate = carrier.defaultTaxRatePercent / 100.0
    val rawAmount = uiState.calcAmountInput.toDoubleOrNull() ?: 0.0

    // Calculate net vs gross
    val requiredGrossPayment: Int
    val netBalanceReceived: Int
    val taxDeducted: Int

    if (uiState.calcIsGrossToNet) {
        // User pays X rials -> how much net credit arrives?
        requiredGrossPayment = rawAmount.toInt()
        netBalanceReceived = (rawAmount / (1.0 + taxRate)).toInt()
        taxDeducted = (requiredGrossPayment - netBalanceReceived).coerceAtLeast(0)
    } else {
        // User needs X net credit for a package -> how much gross rials to recharge?
        netBalanceReceived = rawAmount.toInt()
        requiredGrossPayment = ceil(rawAmount * (1.0 + taxRate)).toInt()
        taxDeducted = (requiredGrossPayment - netBalanceReceived).coerceAtLeast(0)
    }

    val gbInput = uiState.calcDataGbInput.toDoubleOrNull() ?: 0.0
    val daysInput = (uiState.calcDaysInput.toIntOrNull() ?: 30).coerceAtLeast(1)
    val totalMegabytes = (gbInput * 1024).toInt()
    val dailyMegabytesAllowance = totalMegabytes / daysInput

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_calculator_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Tax & Recharge Balance Calculator Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tax_calculator_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, ImperialGold.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = ImperialGold
                        )
                        Column {
                            Text(
                                text = "حاسبة الرصيد والضريبة الذكية",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "نسبة ضريبة المبيعات المعتمدة لشبكة ${carrier.arabicName}: ${carrier.defaultTaxRatePercent}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !uiState.calcIsGrossToNet,
                            onClick = { onToggleCalcMode(false) },
                            label = { Text("كم أشحن لتفعيل باقة؟") },
                            leadingIcon = {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImperialGold.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.calcIsGrossToNet,
                            onClick = { onToggleCalcMode(true) },
                            label = { Text("كم يصلني صافي عند الشحن؟") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ImperialGold.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = uiState.calcAmountInput,
                        onValueChange = onAmountChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("calc_amount_input"),
                        label = {
                            Text(
                                if (uiState.calcIsGrossToNet) "المبلغ المدفوع عند الشحن (ريال)"
                                else "سعر الباقة المطلوب تفعيلها (ريال صافي)"
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Quick Preset Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("1000", "1500", "3000", "4200").forEach { preset ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                onClick = { onAmountChanged(preset) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "$preset ر.ي",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ImperialGold,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, ImperialGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CalcResultRow(
                                label = "إجمالي المبلغ المطلوب دفعه للشحن:",
                                value = "$requiredGrossPayment ريال يمني",
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                            CalcResultRow(
                                label = "الرصيد الصافي الواصل للشريحة:",
                                value = "$netBalanceReceived ريال يمني",
                                valueColor = EmeraldSuccess
                            )
                            CalcResultRow(
                                label = "قيمة الضريبة والرسوم (${carrier.defaultTaxRatePercent}%):",
                                value = "$taxDeducted ريال يمني",
                                valueColor = ImperialGold
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val summary = "حسبة رصيد ${carrier.arabicName}: المبلغ الإجمالي $requiredGrossPayment ريال | الرصيد الصافي $netBalanceReceived ريال | الضريبة $taxDeducted ريال"
                            onCopySummary(summary, "تم نسخ تفاصيل الحسبة إلى الحافظة")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("نسخ نتيجة الحسبة")
                    }
                }
            }
        }

        // 2. Data Quota & Daily Consumption Converter Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_converter_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "محول وحدات الإنترنت وتوزيع الاستهلاك",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "تحويل الجيجابايت إلى ميجابايت ومعرفة المعدل اليومي المثالي",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.calcDataGbInput,
                            onValueChange = onDataGbChanged,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("calc_gb_input"),
                            label = { Text("السعة (جيجابايت GB)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(14.dp)
                        )

                        OutlinedTextField(
                            value = uiState.calcDaysInput,
                            onValueChange = onDaysChanged,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("calc_days_input"),
                            label = { Text("المدة (بالأيام)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    CalcResultRow(
                        label = "إجمالي السعة بالميجابايت (1024 MB):",
                        value = String.format(Locale.US, "%,d ميجابايت", totalMegabytes),
                        valueColor = ImperialGold
                    )
                    CalcResultRow(
                        label = "الاستهلاك اليومي المتوازن لتجنب نفاد الباقة:",
                        value = "$dailyMegabytesAllowance ميجابايت / يومياً",
                        valueColor = EmeraldSuccess
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcResultRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
