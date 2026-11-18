package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CourtEntity
import com.example.data.local.PaymentStatus
import com.example.data.repository.CourtTimeSelection
import com.example.ui.BookingConfirmationPrompt
import com.example.ui.BookingDraft
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.StatusPaidBg
import com.example.ui.theme.StatusPaidFg
import com.example.ui.theme.StatusPendingBg
import com.example.ui.theme.StatusPendingFg
import com.example.util.ScheduleFormatters

fun openWhatsAppConfirmation(context: Context, message: String) {
    val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ScheduleFormatters.buildWhatsAppUrl(message)))
    runCatching { context.startActivity(waIntent) }.onFailure {
        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ScheduleFormatters.VENUE_PHONE}"))
        context.startActivity(dialIntent)
    }
}

@Composable
fun BookingSuccessWhatsAppDialog(
    prompt: BookingConfirmationPrompt,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = StatusPaidFg,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("Pemesanan Berhasil!")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = prompt.customerName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = prompt.scheduleSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Total: ${ScheduleFormatters.formatRupiah(prompt.totalPrice)}",
                            style = MonoPriceStyle,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = "Konfirmasi langsung dengan menghubungi WhatsApp pengelola di nomor ${ScheduleFormatters.VENUE_PHONE} agar jadwal segera diproses.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    openWhatsAppConfirmation(context, prompt.whatsAppMessage)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_whatsapp_dialog_button")
            ) {
                Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("WhatsApp ${ScheduleFormatters.VENUE_PHONE}")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Nanti Saja")
            }
        }
    )
}

@Composable
fun VenueContactMapsCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lapangan Bulutangkis Manisrejo",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Konfirmasi cepat via WhatsApp & petunjuk arah",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "08:00–00:00",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        openWhatsAppConfirmation(
                            context,
                            "Halo Admin Lapangan Bulutangkis Manisrejo (${ScheduleFormatters.VENUE_PHONE}), saya ingin konfirmasi jadwal sewa lapangan."
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier
                        .weight(1.25f)
                        .heightIn(min = 48.dp)
                        .testTag("venue_whatsapp_button")
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = "WhatsApp pengelola", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "WA ${ScheduleFormatters.VENUE_PHONE}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${ScheduleFormatters.VENUE_PHONE}"))
                        context.startActivity(dialIntent)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("venue_phone_button")
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Telepon pengelola", modifier = Modifier.size(18.dp))
                }

                Button(
                    onClick = {
                        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ScheduleFormatters.VENUE_MAPS_URL))
                        context.startActivity(mapsIntent)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .heightIn(min = 48.dp)
                        .testTag("venue_maps_button")
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Buka Google Maps", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Maps", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun DayOfWeekSelectorStrip(
    selectedDay: Int,
    bookingCounts: Map<Int, Int>,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ScheduleFormatters.daysOfWeek.forEach { (dayInt, _) ->
            val isSelected = selectedDay == dayInt
            val count = bookingCounts[dayInt] ?: 0
            val containerColor by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                label = "day_chip_bg"
            )
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = containerColor,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                ),
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelectDay(dayInt) }
                    .testTag("day_chip_$dayInt")
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = ScheduleFormatters.shortDayName(dayInt),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = ScheduleFormatters.formatShortDateForDay(dayInt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 22.dp, height = 16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.secondary
                                    count > 0 -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onSecondary
                                count > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentStatusPill(
    status: String
) {
    val (bg, fg, label) = when (status) {
        PaymentStatus.PAID -> Triple(StatusPaidBg, StatusPaidFg, "Lunas")
        PaymentStatus.DP -> Triple(StatusPendingBg, StatusPendingFg, "DP Sebagian")
        else -> Triple(StatusPendingBg, StatusPendingFg, "Belum Bayar")
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingFormBottomSheet(
    draft: BookingDraft,
    courts: List<CourtEntity>,
    isEditorMode: Boolean = false,
    calculateSelectionsPrice: (List<CourtTimeSelection>, Int, Int, Int) -> Long,
    onDismiss: () -> Unit,
    onConfirm: (
        selections: List<CourtTimeSelection>,
        dayOfWeek: Int,
        customerName: String,
        customerPhone: String,
        paymentStatus: String,
        paidAmount: Long,
        reminderEnabled: Boolean,
        racketCount: Int,
        cockCount: Int,
        notes: String
    ) -> Unit
) {
    val activeCourts = courts.filter { it.isActive }.ifEmpty { courts }
    val initialStart = draft.startHour.coerceIn(
        ScheduleFormatters.OPEN_HOUR,
        ScheduleFormatters.CLOSE_HOUR - 1
    )
    val initialEnd = (initialStart + 1).coerceAtMost(ScheduleFormatters.CLOSE_HOUR)
    val defaultCourt = activeCourts.firstOrNull { it.id == draft.courtId } ?: activeCourts.first()

    var selectedCourts by remember(draft, activeCourts) {
        mutableStateOf(listOf(defaultCourt))
    }
    var selectedDay by remember(draft) { mutableIntStateOf(draft.dayOfWeek) }
    var courtHourTexts by remember(draft, activeCourts) {
        mutableStateOf(
            activeCourts.associate { court ->
                court.id to (
                    ScheduleFormatters.formatHour(initialStart) to
                        ScheduleFormatters.formatHour(initialEnd)
                    )
            }
        )
    }
    var customerName by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }
    var paymentStatus by remember { mutableStateOf(PaymentStatus.UNPAID) }
    var dpAmountText by remember { mutableStateOf("10000") }
    var reminderEnabled by remember { mutableStateOf(true) }
    var racketCount by remember { mutableIntStateOf(0) }
    var cockCount by remember { mutableIntStateOf(0) }

    val parsedCourtStates = selectedCourts.map { court ->
        val (startRaw, endRaw) = courtHourTexts[court.id]
            ?: (ScheduleFormatters.formatHour(initialStart) to ScheduleFormatters.formatHour(initialEnd))
        val parsedStart = ScheduleFormatters.parseManualHourInput(startRaw, treatZeroAsMidnight = false)
        val parsedEnd = ScheduleFormatters.parseManualHourInput(endRaw, treatZeroAsMidnight = true)
        val isStartValid = parsedStart != null &&
            parsedStart in ScheduleFormatters.OPEN_HOUR until ScheduleFormatters.CLOSE_HOUR
        val isEndValid = parsedEnd != null &&
            parsedEnd in (ScheduleFormatters.OPEN_HOUR + 1)..ScheduleFormatters.CLOSE_HOUR &&
            (parsedStart == null || parsedEnd > parsedStart)
        val selection = if (isStartValid && isEndValid) {
            CourtTimeSelection(
                court = court,
                startHour = parsedStart!!,
                durationHours = parsedEnd!! - parsedStart
            )
        } else {
            null
        }
        Triple(court, Triple(startRaw, endRaw, isStartValid to isEndValid), selection)
    }

    val validSelections = parsedCourtStates.mapNotNull { it.third }
    val allTimeRangesValid = selectedCourts.isNotEmpty() && validSelections.size == selectedCourts.size
    val totalPrice = if (allTimeRangesValid) {
        calculateSelectionsPrice(validSelections, selectedDay, racketCount, cockCount)
    } else {
        0L
    }
    val extrasSummary = ScheduleFormatters.formatExtrasSummary(racketCount, cockCount)
    val totalHoursAllCourts = validSelections.sumOf { it.durationHours }
    val twoCourtsOption = remember(activeCourts) { activeCourts.take(2) }
    val isTwoCourtsSelected = twoCourtsOption.size >= 2 &&
        selectedCourts.map { it.id }.toSet() == twoCourtsOption.map { it.id }.toSet()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Pesan Lapangan",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Tarif Rp 15.000/jam • Buka 08:00 – 00:00",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Pilih Lapangan (1 ketuk: A, B, atau A & B)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeCourts.forEach { court ->
                    val isSingleSelected = selectedCourts.size == 1 && selectedCourts.first().id == court.id
                    FilterChip(
                        selected = isSingleSelected,
                        onClick = { selectedCourts = listOf(court) },
                        label = { Text(court.name) },
                        modifier = Modifier.testTag("booking_court_${court.id}")
                    )
                }
                if (twoCourtsOption.size >= 2) {
                    FilterChip(
                        selected = isTwoCourtsSelected,
                        onClick = { selectedCourts = twoCourtsOption },
                        label = { Text("A & B (2 Lapangan)") },
                        modifier = Modifier.testTag("booking_two_courts_chip")
                    )
                }
            }

            // 2. Pilih Hari & Tanggal (1 baris horizontal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScheduleFormatters.daysOfWeek.forEach { (dayNum, dayLabel) ->
                    FilterChip(
                        selected = selectedDay == dayNum,
                        onClick = { selectedDay = dayNum },
                        label = { Text("$dayLabel, ${ScheduleFormatters.formatShortDateForDay(dayNum)}") }
                    )
                }
            }

            // Per-court manual Start Hour & End Hour typed by user (08:00 - 00:00)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Jam Main Manual per Lapangan (08:00 – 00:00)",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (selectedCourts.size > 1) {
                    TextButton(
                        onClick = {
                            val firstHours = courtHourTexts[selectedCourts.first().id] ?: return@TextButton
                            courtHourTexts = courtHourTexts.toMutableMap().apply {
                                selectedCourts.forEach { put(it.id, firstHours) }
                            }
                        }
                    ) {
                        Text("Samakan Jam")
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useColumns = parsedCourtStates.size > 1 && maxWidth >= 520.dp
                if (useColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        parsedCourtStates.forEach { (court, rawAndValidity, _) ->
                            val (startRaw, endRaw, validity) = rawAndValidity
                            val (isStartValid, isEndValid) = validity
                            CourtManualHoursColumnCard(
                                court = court,
                                startRaw = startRaw,
                                endRaw = endRaw,
                                isStartValid = isStartValid,
                                isEndValid = isEndValid,
                                onStartChange = { filtered ->
                                    courtHourTexts = courtHourTexts.toMutableMap().apply {
                                        put(court.id, filtered to endRaw)
                                    }
                                },
                                onEndChange = { filtered ->
                                    courtHourTexts = courtHourTexts.toMutableMap().apply {
                                        put(court.id, startRaw to filtered)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        parsedCourtStates.forEach { (court, rawAndValidity, _) ->
                            val (startRaw, endRaw, validity) = rawAndValidity
                            val (isStartValid, isEndValid) = validity
                            CourtManualHoursColumnCard(
                                court = court,
                                startRaw = startRaw,
                                endRaw = endRaw,
                                isStartValid = isStartValid,
                                isEndValid = isEndValid,
                                onStartChange = { filtered ->
                                    courtHourTexts = courtHourTexts.toMutableMap().apply {
                                        put(court.id, filtered to endRaw)
                                    }
                                },
                                onEndChange = { filtered ->
                                    courtHourTexts = courtHourTexts.toMutableMap().apply {
                                        put(court.id, startRaw to filtered)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Tambahan Sewa Raket (Rp 10.000) & Beli Cock (Rp 12.000)
            Text("Tambahan (opsional)", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExtraAddonStepperCard(
                    title = "Sewa Raket",
                    priceLabel = "Rp 10.000",
                    count = racketCount,
                    onDecrement = { if (racketCount > 0) racketCount -= 1 },
                    onIncrement = { racketCount += 1 },
                    minusTag = "racket_minus_button",
                    plusTag = "racket_plus_button",
                    modifier = Modifier.weight(1f)
                )
                ExtraAddonStepperCard(
                    title = "Beli Cock",
                    priceLabel = "Rp 12.000",
                    count = cockCount,
                    onDecrement = { if (cockCount > 0) cockCount -= 1 },
                    onIncrement = { cockCount += 1 },
                    minusTag = "cock_minus_button",
                    plusTag = "cock_plus_button",
                    modifier = Modifier.weight(1f)
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (allTimeRangesValid) {
                                validSelections.joinToString(" & ") {
                                    "${it.court.name} (${ScheduleFormatters.formatHourRange(it.startHour, it.durationHours)})"
                                } + " • ${ScheduleFormatters.formatFullDayAndDate(selectedDay)}"
                            } else {
                                "Jam belum valid (08:00 – 00:00)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (allTimeRangesValid) {
                                buildString {
                                    append("${validSelections.size} lapangan • $totalHoursAllCourts jam")
                                    if (extrasSummary.isNotBlank()) append(" • $extrasSummary")
                                }
                            } else {
                                "Isi jam mulai (08–23) dan jam selesai (09–00:00)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Text(
                        text = if (allTimeRangesValid) ScheduleFormatters.formatRupiah(totalPrice) else "-",
                        style = MonoPriceStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            OutlinedTextField(
                value = customerName,
                onValueChange = { customerName = it },
                label = { Text("Nama Penyewa / Klub *") },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_customer_name")
            )

            OutlinedTextField(
                value = customerPhone,
                onValueChange = { customerPhone = it },
                label = { Text("No. WhatsApp (opsional)") },
                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("booking_customer_phone")
            )

            if (isEditorMode) {
                Text("Status Pembayaran", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PaymentStatus.UNPAID to "Belum Bayar",
                        PaymentStatus.DP to "DP",
                        PaymentStatus.PAID to "Lunas"
                    ).forEach { (code, label) ->
                        FilterChip(
                            selected = paymentStatus == code,
                            onClick = { paymentStatus = code },
                            label = { Text(label) }
                        )
                    }
                }

                if (paymentStatus == PaymentStatus.DP) {
                    OutlinedTextField(
                        value = dpAmountText,
                        onValueChange = { dpAmountText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Nominal DP (Rp)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (paymentStatus != PaymentStatus.PAID) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("Pengingat Bayar Otomatis", style = MaterialTheme.typography.titleSmall)
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it },
                            modifier = Modifier.testTag("booking_reminder_switch")
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Text("Batal")
                }
                Button(
                    onClick = {
                        val dp = dpAmountText.toLongOrNull() ?: 0L
                        onConfirm(
                            validSelections,
                            selectedDay,
                            customerName,
                            customerPhone,
                            paymentStatus,
                            dp,
                            reminderEnabled,
                            racketCount,
                            cockCount,
                            ""
                        )
                    },
                    enabled = validSelections.isNotEmpty() && customerName.trim().isNotEmpty() && allTimeRangesValid,
                    modifier = Modifier
                        .weight(1.5f)
                        .heightIn(min = 48.dp)
                        .testTag("confirm_booking_button")
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Pesan Sekarang", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CourtManualHoursColumnCard(
    court: CourtEntity,
    startRaw: String,
    endRaw: String,
    isStartValid: Boolean,
    isEndValid: Boolean,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Kolom ${court.name}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = startRaw,
                    onValueChange = { raw ->
                        onStartChange(raw.filter { ch -> ch.isDigit() || ch == ':' || ch == '.' }.take(5))
                    },
                    label = { Text("Mulai") },
                    placeholder = { Text("08:00") },
                    isError = startRaw.isNotBlank() && !isStartValid,
                    supportingText = if (startRaw.isNotBlank() && !isStartValid) {
                        { Text("Isi 08:00 – 23:00") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("booking_start_hour_input_${court.id}")
                )

                OutlinedTextField(
                    value = endRaw,
                    onValueChange = { raw ->
                        onEndChange(raw.filter { ch -> ch.isDigit() || ch == ':' || ch == '.' }.take(5))
                    },
                    label = { Text("Selesai") },
                    placeholder = { Text("00:00") },
                    isError = endRaw.isNotBlank() && !isEndValid,
                    supportingText = if (endRaw.isNotBlank() && !isEndValid) {
                        { Text("Harus > mulai, maks 00:00") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("booking_end_hour_input_${court.id}")
                )
            }
        }
    }
}

@Composable
private fun ExtraAddonStepperCard(
    title: String,
    priceLabel: String,
    count: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    minusTag: String,
    plusTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = priceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = count > 0, onClick = onDecrement)
                        .testTag(minusTag)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onIncrement)
                        .testTag(plusTag)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WebsiteFooterCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF062E22),
        modifier = modifier
            .fillMaxWidth()
            .testTag("website_footer")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Lapangan Bulutangkis Manisrejo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Buka Setiap Hari Senin – Minggu • 08:00 – 00:00 • Rp 15.000/jam",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        openWhatsAppConfirmation(
                            context,
                            "Halo Admin Lapangan Bulutangkis Manisrejo (${ScheduleFormatters.VENUE_PHONE}), saya ingin info jadwal lapangan."
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.Chat, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        val mapsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(ScheduleFormatters.VENUE_MAPS_URL))
                        context.startActivity(mapsIntent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Color(0xFFA6E22E), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Google Maps", maxLines = 1)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

            Text(
                text = "© 2026 Lapangan Bulutangkis Manisrejo • WA ${ScheduleFormatters.VENUE_PHONE}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.68f)
            )
        }
    }
}
