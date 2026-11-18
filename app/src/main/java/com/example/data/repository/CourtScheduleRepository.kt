package com.example.data.repository

import android.content.Context
import com.example.data.local.BookingEntity
import com.example.data.local.BookingHistoryEntity
import com.example.data.local.BookingHistoryStatus
import com.example.data.local.CourtEntity
import com.example.data.local.PaymentStatus
import com.example.data.local.ReminderLogEntity
import com.example.data.local.ReminderLogStatus
import com.example.data.remote.FirestoreCourt
import com.example.data.remote.FirestorePrivateBooking
import com.example.data.remote.FirestoreReminderLog
import com.example.data.remote.FirestoreScheduleSlot
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.notifications.PaymentReminderNotifier
import com.example.util.ScheduleFormatters
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CourtTimeSelection(
    val court: CourtEntity,
    val startHour: Int,
    val durationHours: Int
)

class CourtScheduleRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val currentUserId: String
) {
    private val auth = Firebase.auth
    private val slotDocIdByNumericId = ConcurrentHashMap<Long, String>()
    private val courtDocIdByNumericId = ConcurrentHashMap<Int, String>()

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != currentUserId) {
            throw IllegalStateException("Sesi Google tidak aktif. Silakan masuk ulang.")
        }
        return uid
    }

    private fun stableNumericId(docId: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (ch in docId) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        val positive = hash and Long.MAX_VALUE
        return if (positive == 0L) 1L else positive
    }

    private fun defaultCourts(): List<CourtEntity> = listOf(
        CourtEntity(
            id = 1,
            name = "Lapangan A",
            surfaceType = "",
            hourlyRateRegular = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRatePrime = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRateWeekend = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            isActive = true
        ),
        CourtEntity(
            id = 2,
            name = "Lapangan B",
            surfaceType = "",
            hourlyRateRegular = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRatePrime = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            hourlyRateWeekend = ScheduleFormatters.DEFAULT_HOURLY_RATE,
            isActive = true
        )
    )

    val courts: Flow<List<CourtEntity>> = flow {
        val uid = requireUserId()
        firestore.collection("courts")
            .whereEqualTo("venueId", VENUE_ID)
            .snapshots()
            .map { snapshot ->
                val remoteCourts = snapshot.toObjects(
                    FirestoreCourt::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (remoteCourts.isEmpty()) {
                    courtDocIdByNumericId[1] = "court_1"
                    courtDocIdByNumericId[2] = "court_2"
                    defaultCourts()
                } else {
                    remoteCourts
                        .sortedBy { it.courtNumber }
                        .map { fc ->
                            val idInt = fc.courtNumber.toInt().coerceAtLeast(1)
                            if (fc.id.isNotBlank()) {
                                courtDocIdByNumericId[idInt] = fc.id
                            }
                            CourtEntity(
                                id = idInt,
                                name = fc.name.ifBlank { "Lapangan $idInt" },
                                surfaceType = fc.surfaceType,
                                hourlyRateRegular = fc.hourlyRateRegular,
                                hourlyRatePrime = fc.hourlyRatePrime,
                                hourlyRateWeekend = fc.hourlyRateWeekend,
                                isActive = fc.active
                            )
                        }
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "courts")
                }
                throw error
            }
            .collect { emit(it) }
    }

    private val scheduleSlotsFlow: Flow<List<FirestoreScheduleSlot>> = flow {
        val uid = requireUserId()
        firestore.collection("schedule_slots")
            .whereEqualTo("venueId", VENUE_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreScheduleSlot::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "schedule_slots")
                }
                throw error
            }
            .collect { emit(it) }
    }

    private val privateBookingsFlow: Flow<Map<String, FirestorePrivateBooking>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/bookings"
        firestore.collection("users").document(uid).collection("bookings")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestorePrivateBooking::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).associateBy { it.slotId.ifBlank { it.id } }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    val bookings: Flow<List<BookingEntity>> = combine(
        scheduleSlotsFlow,
        privateBookingsFlow
    ) { slots, privateMap ->
        slots.filter { it.status == BookingHistoryStatus.AKTIF }
            .map { slot ->
                val numericId = stableNumericId(slot.id)
                slotDocIdByNumericId[numericId] = slot.id
                val privateRecord = privateMap[slot.id]
                val createdMs = slot.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                BookingEntity(
                    id = numericId,
                    courtId = slot.courtId.toInt(),
                    courtName = slot.courtName,
                    dayOfWeek = slot.dayOfWeek.toInt().coerceIn(1, 7),
                    startHour = slot.startHour.toInt().coerceIn(ScheduleFormatters.OPEN_HOUR, ScheduleFormatters.CLOSE_HOUR - 1),
                    durationHours = slot.durationHours.toInt().coerceAtLeast(1),
                    customerName = slot.displayName,
                    customerPhone = privateRecord?.customerPhone.orEmpty(),
                    totalPrice = slot.totalPrice,
                    paidAmount = slot.paidAmount,
                    paymentStatus = safePaymentStatus(slot.paymentStatus),
                    reminderEnabled = slot.reminderEnabled,
                    reminderSentCount = slot.reminderSentCount.toInt(),
                    notes = slot.notes,
                    createdAtMillis = createdMs
                )
            }
            .sortedWith(compareBy<BookingEntity> { it.dayOfWeek }.thenBy { it.startHour }.thenBy { it.courtId })
    }

    val bookingHistory: Flow<List<BookingHistoryEntity>> = combine(
        scheduleSlotsFlow,
        privateBookingsFlow
    ) { slots, privateMap ->
        slots.map { slot ->
            val numericId = stableNumericId(slot.id)
            slotDocIdByNumericId[numericId] = slot.id
            val privateRecord = privateMap[slot.id]
            val createdMs = slot.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            BookingHistoryEntity(
                id = numericId,
                bookingId = numericId,
                courtId = slot.courtId.toInt(),
                courtName = slot.courtName,
                dayOfWeek = slot.dayOfWeek.toInt().coerceIn(1, 7),
                startHour = slot.startHour.toInt().coerceIn(ScheduleFormatters.OPEN_HOUR, ScheduleFormatters.CLOSE_HOUR - 1),
                durationHours = slot.durationHours.toInt().coerceAtLeast(1),
                customerName = slot.displayName,
                customerPhone = privateRecord?.customerPhone.orEmpty(),
                totalPrice = slot.totalPrice,
                paidAmount = slot.paidAmount,
                paymentStatus = safePaymentStatus(slot.paymentStatus),
                bookingStatus = safeHistoryStatus(slot.status),
                notes = slot.notes,
                createdAtMillis = createdMs
            )
        }.sortedByDescending { it.createdAtMillis }
    }

    val reminderLogs: Flow<List<ReminderLogEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/reminder_logs"
        firestore.collection("users").document(uid).collection("reminder_logs")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreReminderLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).map { log ->
                    ReminderLogEntity(
                        id = stableNumericId(log.id),
                        bookingId = stableNumericId(log.bookingDocId),
                        customerName = log.customerName,
                        courtName = log.courtName,
                        scheduleLabel = log.scheduleLabel,
                        stageLabel = log.stageLabel,
                        amountDue = log.amountDue,
                        scheduledForMillis = log.scheduledForMillis,
                        sentAtMillis = log.sentAtMillis,
                        status = safeReminderStatus(log.status),
                        message = log.message
                    )
                }.sortedByDescending { it.scheduledForMillis }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
            .collect { emit(it) }
    }

    fun ensureNotificationChannel() {
        PaymentReminderNotifier.ensureChannel(context)
    }

    suspend fun ensureDefaultCourts(): Result<Unit> {
        return try {
            requireUserId()
            val email = auth.currentUser?.email.orEmpty()
            val isVerifiedAdmin = auth.currentUser?.isEmailVerified == true && email == ADMIN_EMAIL
            if (!isVerifiedAdmin) {
                return Result.success(Unit)
            }
            val existing = firestore.collection("courts")
                .whereEqualTo("venueId", VENUE_ID)
                .get()
                .await()
            if (existing.isEmpty) {
                val batch = firestore.batch()
                defaultCourts().forEach { court ->
                    val docRef = firestore.collection("courts").document("court_${court.id}")
                    val payload = mapOf(
                        "venueId" to VENUE_ID,
                        "courtNumber" to court.id,
                        "name" to court.name,
                        "surfaceType" to court.surfaceType,
                        "hourlyRateRegular" to court.hourlyRateRegular,
                        "hourlyRatePrime" to court.hourlyRatePrime,
                        "hourlyRateWeekend" to court.hourlyRateWeekend,
                        "active" to court.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "courts")
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
            val uid = requireUserId()
            val liveSnapshot = firestore.collection("schedule_slots")
                .whereEqualTo("venueId", VENUE_ID)
                .get()
                .await()
            val liveSlots = liveSnapshot.toObjects(
                FirestoreScheduleSlot::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).filter { it.status == BookingHistoryStatus.AKTIF }

            val liveAsBookings = liveSlots.map { s ->
                BookingEntity(
                    id = stableNumericId(s.id),
                    courtId = s.courtId.toInt(),
                    courtName = s.courtName,
                    dayOfWeek = s.dayOfWeek.toInt(),
                    startHour = s.startHour.toInt(),
                    durationHours = s.durationHours.toInt(),
                    customerName = s.displayName,
                    customerPhone = "",
                    totalPrice = s.totalPrice
                )
            }

            val conflictingCourts = distinctSelections.filter { sel ->
                hasTimeConflict(liveAsBookings, sel.court.id, dayOfWeek, sel.startHour, sel.durationHours)
            }
            if (conflictingCourts.isNotEmpty()) {
                val conflictNames = conflictingCourts.joinToString(" & ") { it.court.name }
                return Result.failure(
                    IllegalStateException("$conflictNames sudah dipesan pada jam tersebut (sinkron otomatis).")
                )
            }

            val now = System.currentTimeMillis()
            val bookingDateStr = formatIsoDateForDay(dayOfWeek)
            var remainingDp = paidAmount.coerceAtLeast(0L)
            val batch = firestore.batch()
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
                val slotId = "slot_${now}_${index}_${court.id}"
                val publicRef = firestore.collection("schedule_slots").document(slotId)
                val privateRef = firestore.collection("users").document(uid).collection("bookings").document(slotId)
                val effectiveReminder = reminderEnabled && effectiveStatus != PaymentStatus.PAID

                val publicPayload = mapOf<String, Any>(
                    "venueId" to VENUE_ID,
                    "userId" to uid,
                    "courtId" to court.id,
                    "courtName" to court.name.take(50),
                    "dayOfWeek" to dayOfWeek.coerceIn(1, 7),
                    "bookingDate" to bookingDateStr,
                    "startHour" to sel.startHour,
                    "durationHours" to sel.durationHours,
                    "displayName" to cleanName,
                    "totalPrice" to totalPrice,
                    "paidAmount" to effectivePaid,
                    "paymentStatus" to effectiveStatus,
                    "racketCount" to if (index == 0) boundedRackets else 0,
                    "cockCount" to if (index == 0) boundedCocks else 0,
                    "reminderEnabled" to effectiveReminder,
                    "reminderSentCount" to 0,
                    "notes" to itemNotes,
                    "status" to BookingHistoryStatus.AKTIF,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(publicRef, publicPayload)

                val privatePayload = mapOf<String, Any>(
                    "userId" to uid,
                    "slotId" to slotId,
                    "customerName" to cleanName,
                    "customerPhone" to cleanPhone,
                    "notes" to itemNotes,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(privateRef, privatePayload)

                val numericId = stableNumericId(slotId)
                slotDocIdByNumericId[numericId] = slotId

                val saved = BookingEntity(
                    id = numericId,
                    courtId = court.id,
                    courtName = court.name,
                    dayOfWeek = dayOfWeek,
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
                savedBookings.add(saved)
            }

            batch.commit().await()

            savedBookings.forEach { saved ->
                notifyBookingConfirmed(saved)
                if (saved.reminderEnabled && saved.paymentStatus != PaymentStatus.PAID) {
                    schedulePaymentReminderAndLog(saved)
                }
            }

            Result.success(savedBookings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "schedule_slots")
            Result.failure(e)
        }
    }

    suspend fun markBookingPaid(bookingId: Long): Result<Unit> {
        val docId = slotDocIdByNumericId[bookingId]
            ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
        val docPath = "schedule_slots/$docId"
        return try {
            requireUserId()
            val snap = firestore.document(docPath).get().await()
            val slot = snap.toObject(FirestoreScheduleSlot::class.java)
                ?: return Result.failure(IllegalStateException("Jadwal tidak tersedia di server."))
            firestore.document(docPath).update(
                mapOf(
                    "paymentStatus" to PaymentStatus.PAID,
                    "paidAmount" to slot.totalPrice,
                    "reminderEnabled" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordDownPayment(bookingId: Long, dpAmount: Long): Result<Unit> {
        val docId = slotDocIdByNumericId[bookingId]
            ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
        val docPath = "schedule_slots/$docId"
        return try {
            requireUserId()
            val snap = firestore.document(docPath).get().await()
            val slot = snap.toObject(FirestoreScheduleSlot::class.java)
                ?: return Result.failure(IllegalStateException("Jadwal tidak tersedia di server."))
            val newPaid = (slot.paidAmount + dpAmount).coerceIn(0L, slot.totalPrice)
            val newStatus = if (newPaid >= slot.totalPrice) PaymentStatus.PAID else PaymentStatus.DP
            val keepReminder = newStatus != PaymentStatus.PAID && slot.reminderEnabled
            firestore.document(docPath).update(
                mapOf(
                    "paidAmount" to newPaid,
                    "paymentStatus" to newStatus,
                    "reminderEnabled" to keepReminder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            if (newStatus == PaymentStatus.PAID) {
                PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendImmediateReminder(bookingId: Long): Result<Boolean> {
        val docId = slotDocIdByNumericId[bookingId]
            ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
        val docPath = "schedule_slots/$docId"
        return try {
            val uid = requireUserId()
            val snap = firestore.document(docPath).get().await()
            val slot = snap.toObject(FirestoreScheduleSlot::class.java)
                ?: return Result.failure(IllegalStateException("Jadwal tidak tersedia."))
            val amountDue = (slot.totalPrice - slot.paidAmount).coerceAtLeast(0L)
            val dayInt = slot.dayOfWeek.toInt().coerceIn(1, 7)
            val startInt = slot.startHour.toInt()
            val durInt = slot.durationHours.toInt()
            val scheduleLabel = "${ScheduleFormatters.dayName(dayInt)} ${ScheduleFormatters.formatHourRange(startInt, durInt)}"
            val shown = PaymentReminderNotifier.showPaymentReminderNotification(
                context = context,
                bookingId = bookingId,
                customerName = slot.displayName,
                courtName = slot.courtName,
                scheduleLabel = scheduleLabel,
                amountDue = amountDue,
                stageLabel = "Pengingat Pembayaran"
            )
            val msg = ScheduleFormatters.buildPaymentReminderMessage(
                customerName = slot.displayName,
                courtName = slot.courtName,
                dayOfWeek = dayInt,
                startHour = startInt,
                durationHours = durInt,
                amountDue = amountDue
            ).take(400)

            firestore.document(docPath).update(
                mapOf(
                    "reminderSentCount" to (slot.reminderSentCount + 1L).coerceAtMost(100L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val logId = "log_${System.currentTimeMillis()}_remind"
            firestore.collection("users").document(uid).collection("reminder_logs").document(logId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "bookingDocId" to docId,
                        "customerName" to slot.displayName,
                        "courtName" to slot.courtName,
                        "scheduleLabel" to scheduleLabel,
                        "stageLabel" to "Pengingat Pembayaran",
                        "amountDue" to amountDue,
                        "scheduledForMillis" to System.currentTimeMillis(),
                        "sentAtMillis" to System.currentTimeMillis(),
                        "status" to ReminderLogStatus.TERKIRIM,
                        "message" to msg,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            Result.success(shown)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelBooking(bookingId: Long): Result<Unit> {
        val docId = slotDocIdByNumericId[bookingId]
            ?: return Result.failure(IllegalArgumentException("Data pemesanan tidak ditemukan."))
        val docPath = "schedule_slots/$docId"
        return try {
            requireUserId()
            PaymentReminderNotifier.cancelPaymentReminder(context, bookingId)
            firestore.document(docPath).update(
                mapOf(
                    "status" to BookingHistoryStatus.DIBATALKAN,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveCourt(court: CourtEntity): Result<Unit> {
        return try {
            requireUserId()
            val courtNumber = if (court.id == 0) {
                (courtDocIdByNumericId.keys.maxOrNull() ?: 2) + 1
            } else {
                court.id
            }
            val docId = courtDocIdByNumericId[courtNumber] ?: "court_$courtNumber"
            val docRef = firestore.collection("courts").document(docId)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "name" to court.name.trim().take(50).ifEmpty { "Lapangan $courtNumber" },
                        "surfaceType" to court.surfaceType.trim().take(80),
                        "hourlyRateRegular" to court.hourlyRateRegular.coerceAtLeast(0L),
                        "hourlyRatePrime" to court.hourlyRatePrime.coerceAtLeast(0L),
                        "hourlyRateWeekend" to court.hourlyRateWeekend.coerceAtLeast(0L),
                        "active" to court.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "venueId" to VENUE_ID,
                        "courtNumber" to courtNumber,
                        "name" to court.name.trim().take(50).ifEmpty { "Lapangan $courtNumber" },
                        "surfaceType" to court.surfaceType.trim().take(80),
                        "hourlyRateRegular" to court.hourlyRateRegular.coerceAtLeast(0L),
                        "hourlyRatePrime" to court.hourlyRatePrime.coerceAtLeast(0L),
                        "hourlyRateWeekend" to court.hourlyRateWeekend.coerceAtLeast(0L),
                        "active" to court.isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "courts")
            Result.failure(e)
        }
    }

    suspend fun toggleCourtActive(court: CourtEntity): Result<Unit> {
        return saveCourt(court.copy(isActive = !court.isActive))
    }

    private suspend fun notifyBookingConfirmed(booking: BookingEntity) {
        PaymentReminderNotifier.showBookingConfirmedNotification(context, booking)
        val uid = auth.currentUser?.uid ?: return
        val docId = slotDocIdByNumericId[booking.id] ?: return
        val amountDue = (booking.totalPrice - booking.paidAmount).coerceAtLeast(0L)
        val scheduleLabel = "${ScheduleFormatters.dayName(booking.dayOfWeek)} ${ScheduleFormatters.formatHourRange(booking.startHour, booking.durationHours)}"
        val logId = "log_${System.currentTimeMillis()}_confirm_${booking.courtId}"
        runCatching {
            firestore.collection("users").document(uid).collection("reminder_logs").document(logId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "bookingDocId" to docId,
                        "customerName" to booking.customerName,
                        "courtName" to booking.courtName,
                        "scheduleLabel" to scheduleLabel,
                        "stageLabel" to "Konfirmasi Pemesanan",
                        "amountDue" to amountDue,
                        "scheduledForMillis" to System.currentTimeMillis(),
                        "sentAtMillis" to System.currentTimeMillis(),
                        "status" to ReminderLogStatus.KONFIRMASI,
                        "message" to "Pemesanan ${booking.courtName} ($scheduleLabel) dikonfirmasi.",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
    }

    private suspend fun schedulePaymentReminderAndLog(booking: BookingEntity) {
        val uid = auth.currentUser?.uid ?: return
        val docId = slotDocIdByNumericId[booking.id] ?: return
        val amountDue = (booking.totalPrice - booking.paidAmount).coerceAtLeast(0L)
        val scheduleLabel = "${ScheduleFormatters.dayName(booking.dayOfWeek)} ${ScheduleFormatters.formatHourRange(booking.startHour, booking.durationHours)}"
        val msg = ScheduleFormatters.buildPaymentReminderMessage(
            customerName = booking.customerName,
            courtName = booking.courtName,
            dayOfWeek = booking.dayOfWeek,
            startHour = booking.startHour,
            durationHours = booking.durationHours,
            amountDue = amountDue
        ).take(400)
        val triggerAt = PaymentReminderNotifier.schedulePaymentReminder(context, booking) ?: return
        val logId = "log_${System.currentTimeMillis()}_sched_${booking.courtId}"
        runCatching {
            firestore.collection("users").document(uid).collection("reminder_logs").document(logId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "bookingDocId" to docId,
                        "customerName" to booking.customerName,
                        "courtName" to booking.courtName,
                        "scheduleLabel" to scheduleLabel,
                        "stageLabel" to "Pengingat Pembayaran",
                        "amountDue" to amountDue,
                        "scheduledForMillis" to triggerAt,
                        "status" to ReminderLogStatus.TERJADWAL,
                        "message" to msg,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }
    }

    private fun safePaymentStatus(raw: String): String = when (raw) {
        PaymentStatus.PAID -> PaymentStatus.PAID
        PaymentStatus.DP -> PaymentStatus.DP
        else -> PaymentStatus.UNPAID
    }

    private fun safeHistoryStatus(raw: String): String = when (raw) {
        BookingHistoryStatus.DIBATALKAN -> BookingHistoryStatus.DIBATALKAN
        else -> BookingHistoryStatus.AKTIF
    }

    private fun safeReminderStatus(raw: String): String = when (raw) {
        ReminderLogStatus.KONFIRMASI -> ReminderLogStatus.KONFIRMASI
        ReminderLogStatus.TERKIRIM -> ReminderLogStatus.TERKIRIM
        ReminderLogStatus.LUNAS -> ReminderLogStatus.LUNAS
        else -> ReminderLogStatus.TERJADWAL
    }

    private fun formatIsoDateForDay(dayOfWeek: Int): String {
        val date = ScheduleFormatters.dateForIsoDayInCurrentWeek(dayOfWeek)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        return sdf.format(date)
    }

    companion object {
        const val VENUE_ID = "manisrejo"
        const val ADMIN_EMAIL = "Riskykharis00@gmail.com"
        const val EDITOR_PIN_DEFAULT = "00000"
    }
}
