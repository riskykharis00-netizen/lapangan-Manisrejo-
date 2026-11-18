package com.example.data.remote

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FirestoreCourt(
    @DocumentId val id: String = "",
    val venueId: String = "manisrejo",
    val courtNumber: Long = 1L,
    val name: String = "",
    val surfaceType: String = "",
    val hourlyRateRegular: Long = 15_000L,
    val hourlyRatePrime: Long = 15_000L,
    val hourlyRateWeekend: Long = 15_000L,
    val active: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreScheduleSlot(
    @DocumentId val id: String = "",
    val venueId: String = "manisrejo",
    val userId: String = "",
    val courtId: Long = 1L,
    val courtName: String = "",
    val dayOfWeek: Long = 1L,
    val bookingDate: String = "",
    val startHour: Long = 18L,
    val durationHours: Long = 1L,
    val displayName: String = "",
    val totalPrice: Long = 0L,
    val paidAmount: Long = 0L,
    val paymentStatus: String = "UNPAID",
    val racketCount: Long = 0L,
    val cockCount: Long = 0L,
    val reminderEnabled: Boolean = true,
    val reminderSentCount: Long = 0L,
    val notes: String = "",
    val status: String = "AKTIF",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestorePrivateBooking(
    @DocumentId val id: String = "",
    val userId: String = "",
    val slotId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FirestoreReminderLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val bookingDocId: String = "",
    val customerName: String = "",
    val courtName: String = "",
    val scheduleLabel: String = "",
    val stageLabel: String = "",
    val amountDue: Long = 0L,
    val scheduledForMillis: Long = 0L,
    val sentAtMillis: Long? = null,
    val status: String = "TERJADWAL",
    val message: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
