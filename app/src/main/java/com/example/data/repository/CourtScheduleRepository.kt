package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.BookingEntity
import com.example.data.local.BookingHistoryEntity
import com.example.data.local.BookingHistoryStatus
import com.example.data.local.CourtEntity
import com.example.data.local.PaymentStatus
import com.example.data.local.ReminderLogEntity
import com.example.data.local.ReminderLogStatus
import com.example.notifications.PaymentReminderNotifier
import com.example.util.ScheduleFormatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

data class CourtTimeSelection(
    val court: CourtEntity,
    val startHour: Int,
    val durationHours: Int
)

class CourtScheduleRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val courtDao = db.courtDao()
    private val bookingDao = db.bookingDao()
    private val reminderLogDao = db.reminderLogDao()
    private val bookingHistoryDao = db.bookingHistoryDao()

    val courts: Flow<List<CourtEntity>> = courtDao.observeAllCourts()
    val bookings: Flow<List<BookingEntity>> = bookingDao.observeAllBookings()
    val reminderLogs: Flow<List<ReminderLogEntity>> = reminderLogDao.observeAllLogs()
    val bookingHistory: Flow<List<BookingHistoryEntity>> = bookingHistoryDao.observeAllHistory()

    fun ensureNotificationChannel() {
        PaymentReminderNotifier.ensureChannel(context)
    }

    private fun defaultCourts(): List<CourtEntity> = listOf(
        CourtEntity(
            name = "Lapangan A",
            surfaceType = "",
            hourlyRateRegular = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRatePrime = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRateWeekend = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            isActive = true
        ),
        CourtEntity(
            name = "Lapangan B",
            surfaceType = "",
            hourlyRateRegular = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRatePrime = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRateWeekend = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            isActive = true
        )
    )

    suspend fun ensureDefaultCourts(): Result<Unit> {
        return try {
            if (courtDao.countCourts() == 0) {
                courtDao.insertCourts(defaultCourts())
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun calculateBookingPrice(
        court: CourtEntity,
        dayOfWeek: Int,
        startHour: Int,
        durationHours: Int
    ): Long {
        var total = 0L
        for (offset in 0 until durationHours) {
            val hour = startHour + offset
            total += when {
                dayOfWeek >= 6 -> court.hourlyRateWeekend
                hour >= 17 -> court.hourlyRatePrime
                else -> court.hourlyRateRegular
            }
        }
        return total
    }

    fun hasTimeConflict(
        existingBookings: List<BookingEntity>,
        courtId: Int,
        dayOfWeek: Int,
        startHour: Int,
        durationHours: Int,
        excludeBookingId: Long = -1L
    ): Boolean {
        val newEnd = startHour + durationHours
        return existingBookings.any { b ->
            b.id != excludeBookingId &&
                b.courtId == courtId &&
                b.dayOfWeek == dayOfWeek &&
                startHour < (b.startHour + b.durationHours) &&
                newEnd > b.startHour
        }
    }

    suspend fun createManualCourtSelections(
        selections: List<CourtTimeSelection>,
        dayOfWeek: Int,
        customerName: String,
        customerPhone: String,
        paymentStatus: String,
        paidAmount: Long,
        reminderEnabled: Boolean,
        racketCount: Int = 0,
        cockCount: Int = 0,
        notes: String = ""
    ): Result<List<BookingEntity>> {
        val distinctSelections = selections.distinctBy { it.court.id }
        if (distinctSelections.isEmpty()) {
            return Result.failure(IllegalArgumentException("Pilih minimal 1 lapangan."))
        }
        val cleanName = customerName.trim().take(60)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nama penyewa / klub wajib diisi."))
        }
        val inactiveCourts = distinctSelections.filter { !it.court.isActive }
        if (inactiveCourts.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("${inactiveCourts.joinToString(" & ") { it.court.name }} sedang dalam perawatan.")
            )
        }
        val invalidHours = distinctSelections.filter {
            it.startHour < ScheduleFormatters.OPEN_HOUR ||
                it.durationHours < 1 ||
                it.startHour + it.durationHours > ScheduleFormatters.CLOSE_HOUR
        }
        if (invalidHours.isNotEmpty()) {
            return Result.failure(IllegalArgumentException("Jam operasional hanya pukul 08:00 - 00:00."))
        }

        val boundedRackets = racketCount.coerceIn(0, 50)
        val boundedCocks = cockCount.coerceIn(0, 100)
        val cleanPhone = customerPhone.trim().take(30)
        val cleanNotes = notes.trim().take(200)
        val extrasPrice = ScheduleFormatters.calculateExtrasPrice(boundedRackets, boundedCocks)
        val extrasSummary = ScheduleFormatters.formatExtrasSummary(boundedRackets, boundedCocks)
        val combinedFirstNotes = listOf(extrasSummary, cleanNotes)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(250)

        return try {
            val existingBookings = bookingDao.getAllBookingsOnce()
            val conflictingCourts = distinctSelections.filter { sel ->
                hasTimeConflict(existingBookings, sel.court.id, dayOfWeek, sel.startHour, sel.durationHours)
            }
            if (conflictingCourts.isNotEmpty()) {
                val conflictNames = conflictingCourts.joinToString(" & ") { it.court.name }
                return Result.failure(
                    IllegalStateException("$conflictNames sudah dipesan pada jam tersebut.")
                )
            }

            val now = System.currentTimeMillis()
            var remainingDp = paidAmount.coerceAtLeast(0L)
            val savedBookings = mutableListOf<BookingEntity>()

            distinctSelections.forEachIndexed { index, sel ->
                val court = sel.court
                val courtPrice = calculateBookingPrice(court, dayOfWeek, sel.startHour, sel.durationHours)
                val totalPrice = courtPrice + if (index == 0) extrasPrice else 0L
                val effectivePaid = when (paymentStatus) {
                    PaymentStatus.PAID -> totalPrice
                    PaymentStatus.DP -> {
                        val allocated = remainingDp.coerceIn(0L, totalPrice)
                        remainingDp = (remainingDp - allocated).coerceAtLeast(0L)
                        allocated
                    }
                    else -> 0L
                }
                val effectiveStatus = when {
                    paymentStatus == PaymentStatus.PAID || effectivePaid >= totalPrice -> PaymentStatus.PAID
                    paymentStatus == PaymentStatus.DP -> PaymentStatus.DP
                    else -> PaymentStatus.UNPAID
                }
                val itemNotes = (if (index == 0) combinedFirstNotes else cleanNotes).take(250)
                val effectiveReminder = reminderEnabled && effectiveStatus != PaymentStatus.PAID

                val entity = BookingEntity(
                    courtId = court.id,
                    courtName = court.name,
                    dayOfWeek = dayOfWeek.coerceIn(1, 7),
                    startHour = sel.startHour,
                    durationHours = sel.durationHours,
                    customerName = cleanName,
                    customerPhone = cleanPhone,
                    totalPrice = totalPrice,
                    paidAmount = effectivePaid,
                    paymentStatus = effectiveStatus,
                    reminderEnabled = effectiveReminder,
                    reminderSentCount = 0,
                    notes = itemNotes,
                    createdAtMillis = now
                )
                val insertedId = bookingDao.insertBooking(entity)
                val saved = entity.copy(id = insertedId)
                savedBookings.add(saved)

                bookingHistoryDao.insertHistory(
                    BookingHistoryEntity(
                        bookingId = insertedId,
                        courtId = court.id,
                        courtName = court.name,
                        dayOfWeek = dayOfWeek.coerceIn(1, 7),
                        startHour = sel.startHour,
                        durationHours = sel.durationHours,
                        customerName = cleanName,
                        customerPhone = cleanPhone,
                        totalPrice = totalPrice,
                        paidAmount = effectivePaid,
                        paymentStatus = effectiveStatus,
                        bookingStatus = BookingHistoryStatus.AKTIF,
                        notes = itemNotes,
                        createdAtMillis = now
                    )
                )

                notifyBookingConfirmed(saved)
                if (saved.reminderEnabled && saved.paymentStatus != PaymentStatus.PAID) {
                    schedulePaymentReminderAndLog(saved)
                }
            }

            Result.success(savedBookings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markBookingPaid(bookingId: Long): Result<Unit> {
        return try {
            val booking = bookingDao.getBookingById(bookingId)
                ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
            val updated = booking.copy(
                paymentStatus = PaymentStatus.PAID,
                paidAmount = booking.totalPrice,
                reminderEnabled = false
            )
            bookingDao.updateBooking(updated)
            bookingHistoryDao.updatePaymentForBooking(bookingId, PaymentStatus.PAID, booking.totalPrice)
            reminderLogDao.updateStatusForBooking(bookingId, ReminderLogStatus.LUNAS, System.currentTimeMillis())
            PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordDownPayment(bookingId: Long, dpAmount: Long): Result<Unit> {
        return try {
            val booking = bookingDao.getBookingById(bookingId)
                ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
            val newPaid = (booking.paidAmount + dpAmount).coerceIn(0L, booking.totalPrice)
            val newStatus = if (newPaid >= booking.totalPrice) PaymentStatus.PAID else PaymentStatus.DP
            val keepReminder = newStatus != PaymentStatus.PAID && booking.reminderEnabled
            bookingDao.updateBooking(
                booking.copy(
                    paidAmount = newPaid,
                    paymentStatus = newStatus,
                    reminderEnabled = keepReminder
                )
            )
            bookingHistoryDao.updatePaymentForBooking(bookingId, newStatus, newPaid)
            if (newStatus == PaymentStatus.PAID) {
                reminderLogDao.updateStatusForBooking(bookingId, ReminderLogStatus.LUNAS, System.currentTimeMillis())
                PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendImmediateReminder(bookingId: Long): Result<Boolean> {
        return try {
            val booking = bookingDao.getBookingById(bookingId)
                ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
            val amountDue = (booking.totalPrice - booking.paidAmount).coerceAtLeast(0L)
            val scheduleLabel = "${ScheduleFormatters.dayName(booking.dayOfWeek)} ${ScheduleFormatters.formatHourRange(booking.startHour, booking.durationHours)}"
            val shown = PaymentReminderNotifier.showPaymentReminderNotification(
                context = context,
                bookingId = bookingId,
                customerName = booking.customerName,
                courtName = booking.courtName,
                scheduleLabel = scheduleLabel,
                amountDue = amountDue,
                stageLabel = "Pengingat Pembayaran"
            )
            bookingDao.updateBooking(booking.copy(reminderSentCount = booking.reminderSentCount + 1))
            val msg = ScheduleFormatters.buildPaymentReminderMessage(
                customerName = booking.customerName,
                courtName = booking.courtName,
                dayOfWeek = booking.dayOfWeek,
                startHour = booking.startHour,
                durationHours = booking.durationHours,
                amountDue = amountDue
            )
            reminderLogDao.insertLog(
                ReminderLogEntity(
                    bookingId = bookingId,
                    customerName = booking.customerName,
                    courtName = booking.courtName,
                    scheduleLabel = scheduleLabel,
                    stageLabel = "Pengingat Pembayaran",
                    amountDue = amountDue,
                    scheduledForMillis = System.currentTimeMillis(),
                    sentAtMillis = System.currentTimeMillis(),
                    status = ReminderLogStatus.TERKIRIM,
                    message = msg
                )
            )
            Result.success(shown)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(bookingId: Long): Result<Unit> {
        return try {
            PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            bookingDao.deleteBookingById(bookingId)
            bookingHistoryDao.updateBookingStatus(bookingId, BookingHistoryStatus.DIBATALKAN)
            reminderLogDao.deleteLogsForBooking(bookingId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveCourt(court: CourtEntity): Result<Unit> {
        return try {
            val cleanName = court.name.trim().take(50).ifEmpty { "Lapangan" }
            val existingCourts = courtDao.getAllCourtsOnce()
            val oldCourt = existingCourts.find { it.id == court.id }
            val updated = court.copy(
                name = cleanName,
                surfaceType = court.surfaceType.trim().take(80),
                hourlyRateRegular = court.hourlyRateRegular.coerceAtLeast(0L),
                hourlyRatePrime = court.hourlyRatePrime.coerceAtLeast(0L),
                hourlyRateWeekend = court.hourlyRateWeekend.coerceAtLeast(0L)
            )
            if (court.id == 0 || oldCourt == null) {
                courtDao.insertCourt(updated)
            } else {
                courtDao.updateCourt(updated)
                if (oldCourt.name != cleanName) {
                    bookingDao.renameCourtInBookings(oldCourt.name, cleanName)
                    bookingHistoryDao.renameCourtInHistory(oldCourt.name, cleanName)
                    reminderLogDao.renameCourtInLogs(oldCourt.name, cleanName)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleCourtActive(court: CourtEntity): Result<Unit> {
        return saveCourt(court.copy(isActive = !court.isActive))
    }

    private suspend fun notifyBookingConfirmed(booking: BookingEntity) {
        PaymentReminderNotifier.showBookingConfirmedNotification(context, booking)
        val amountDue = (booking.totalPrice - booking.paidAmount).coerceAtLeast(0L)
        val scheduleLabel = "${ScheduleFormatters.dayName(booking.dayOfWeek)} ${ScheduleFormatters.formatHourRange(booking.startHour, booking.durationHours)}"
        reminderLogDao.insertLog(
            ReminderLogEntity(
                bookingId = booking.id,
                customerName = booking.customerName,
                courtName = booking.courtName,
                scheduleLabel = scheduleLabel,
                stageLabel = "Konfirmasi Pemesanan",
                amountDue = amountDue,
                scheduledForMillis = System.currentTimeMillis(),
                sentAtMillis = System.currentTimeMillis(),
                status = ReminderLogStatus.KONFIRMASI,
                message = "Pemesanan ${booking.courtName} ($scheduleLabel) dikonfirmasi."
            )
        )
    }

    private suspend fun schedulePaymentReminderAndLog(booking: BookingEntity) {
        val amountDue = (booking.totalPrice - booking.paidAmount).coerceAtLeast(0L)
        val scheduleLabel = "${ScheduleFormatters.dayName(booking.dayOfWeek)} ${ScheduleFormatters.formatHourRange(booking.startHour, booking.durationHours)}"
        val msg = ScheduleFormatters.buildPaymentReminderMessage(
            customerName = booking.customerName,
            courtName = booking.courtName,
            dayOfWeek = booking.dayOfWeek,
            startHour = booking.startHour,
            durationHours = booking.durationHours,
            amountDue = amountDue
        )
        val triggerAt = PaymentReminderNotifier.schedulePaymentReminder(context, booking) ?: return
        reminderLogDao.insertLog(
            ReminderLogEntity(
                bookingId = booking.id,
                customerName = booking.customerName,
                courtName = booking.courtName,
                scheduleLabel = scheduleLabel,
                stageLabel = "Pengingat Pembayaran",
                amountDue = amountDue,
                scheduledForMillis = triggerAt,
                status = ReminderLogStatus.TERJADWAL,
                message = msg
            )
        )
    }

    companion object {
        const val EDITOR_PIN_DEFAULT = "00000"
    }
}
