package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BookingEntity
import com.example.data.local.BookingHistoryEntity
import com.example.data.local.CourtEntity
import com.example.data.local.PaymentStatus
import com.example.data.local.ReminderLogEntity
import com.example.data.repository.CourtScheduleRepository
import com.example.data.repository.CourtTimeSelection
import com.example.util.ScheduleFormatters
import java.util.Calendar
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class UserRole {
    RENTER,
    EDITOR
}

enum class AppTab {
    SCHEDULE,
    HISTORY,
    PAYMENTS,
    COURTS
}

enum class SlotFilter {
    ALL,
    AVAILABLE,
    BOOKED
}

data class BookingDraft(
    val courtId: Int,
    val dayOfWeek: Int,
    val startHour: Int
)

data class BookingConfirmationPrompt(
    val customerName: String,
    val scheduleSummary: String,
    val totalPrice: Long,
    val whatsAppMessage: String
)

data class ShuttleUiState(
    val isLoading: Boolean = true,
    val syncError: String? = null,
    val lastSyncedMillis: Long = System.currentTimeMillis(),
    val userRole: UserRole = UserRole.RENTER,
    val isEditorUnlocked: Boolean = false,
    val selectedDay: Int = 1,
    val selectedCourtId: Int = 0, // 0 = Semua Lapangan
    val slotFilter: SlotFilter = SlotFilter.ALL,
    val courts: List<CourtEntity> = emptyList(),
    val bookings: List<BookingEntity> = emptyList(),
    val bookingHistory: List<BookingHistoryEntity> = emptyList(),
    val reminderLogs: List<ReminderLogEntity> = emptyList()
) {
    val totalPaidRevenue: Long
        get() = bookings.sumOf { it.paidAmount }

    val totalPendingAmount: Long
        get() = bookings.filter { it.paymentStatus != PaymentStatus.PAID }
            .sumOf { (it.totalPrice - it.paidAmount).coerceAtLeast(0L) }

    val pendingBookings: List<BookingEntity>
        get() = bookings.filter { it.paymentStatus != PaymentStatus.PAID }
            .sortedWith(compareBy<BookingEntity> { it.dayOfWeek }.thenBy { it.startHour })

    val paidBookings: List<BookingEntity>
        get() = bookings.filter { it.paymentStatus == PaymentStatus.PAID }
            .sortedByDescending { it.createdAtMillis }

    val activeReminderCount: Int
        get() = bookings.count { it.paymentStatus != PaymentStatus.PAID && it.reminderEnabled }

    fun bookingsCountForDay(day: Int): Int =
        bookings.count { it.dayOfWeek == day && (selectedCourtId == 0 || it.courtId == selectedCourtId) }
}

class MainViewModel(
    private val repository: CourtScheduleRepository
) : ViewModel() {

    private val _userRole = MutableStateFlow(UserRole.RENTER)
    private val _isEditorUnlocked = MutableStateFlow(false)
    private val _selectedDay = MutableStateFlow(currentIsoDayOfWeek())
    private val _selectedCourtId = MutableStateFlow(0)
    private val _slotFilter = MutableStateFlow(SlotFilter.ALL)
    private val _syncError = MutableStateFlow<String?>(null)

    private val _activeTab = MutableStateFlow(AppTab.SCHEDULE)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _bookingDraft = MutableStateFlow<BookingDraft?>(null)
    val bookingDraft: StateFlow<BookingDraft?> = _bookingDraft.asStateFlow()

    private val _confirmationPrompt = MutableStateFlow<BookingConfirmationPrompt?>(null)
    val confirmationPrompt: StateFlow<BookingConfirmationPrompt?> = _confirmationPrompt.asStateFlow()

    private val _showEditorPinDialog = MutableStateFlow(false)
    val showEditorPinDialog: StateFlow<Boolean> = _showEditorPinDialog.asStateFlow()

    private val _snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarMessages: SharedFlow<String> = _snackbarMessages.asSharedFlow()

    private val safeCourtsFlow = repository.courts.catch { error ->
        _syncError.value = error.localizedMessage ?: "Gagal memuat data lapangan."
        emit(emptyList())
    }

    private val safeBookingsFlow = repository.bookings.catch { error ->
        _syncError.value = error.localizedMessage ?: "Gagal memuat jadwal."
        emit(emptyList())
    }

    private val safeHistoryFlow = repository.bookingHistory.catch { error ->
        _syncError.value = error.localizedMessage ?: "Gagal memuat riwayat."
        emit(emptyList())
    }

    private val safeRemindersFlow = repository.reminderLogs.catch { error ->
        _syncError.value = error.localizedMessage ?: "Gagal memuat log pengingat."
        emit(emptyList())
    }

    val uiState: StateFlow<ShuttleUiState> = combine(
        safeCourtsFlow,
        safeBookingsFlow,
        safeHistoryFlow,
        safeRemindersFlow,
        _selectedDay,
        _selectedCourtId,
        _slotFilter,
        _userRole,
        _isEditorUnlocked,
        _syncError
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val courts = values[0] as List<CourtEntity>
        @Suppress("UNCHECKED_CAST")
        val bookings = values[1] as List<BookingEntity>
        @Suppress("UNCHECKED_CAST")
        val bookingHistory = values[2] as List<BookingHistoryEntity>
        @Suppress("UNCHECKED_CAST")
        val reminderLogs = values[3] as List<ReminderLogEntity>
        val selectedDay = values[4] as Int
        val selectedCourtId = values[5] as Int
        val slotFilter = values[6] as SlotFilter
        val userRole = values[7] as UserRole
        val isEditorUnlocked = values[8] as Boolean
        val syncError = values[9] as String?

        ShuttleUiState(
            isLoading = false,
            syncError = syncError,
            lastSyncedMillis = System.currentTimeMillis(),
            userRole = userRole,
            isEditorUnlocked = isEditorUnlocked,
            selectedDay = selectedDay,
            selectedCourtId = selectedCourtId,
            slotFilter = slotFilter,
            courts = courts,
            bookings = bookings,
            bookingHistory = bookingHistory,
            reminderLogs = reminderLogs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ShuttleUiState(selectedDay = currentIsoDayOfWeek())
    )

    init {
        repository.ensureNotificationChannel()
        viewModelScope.launch {
            repository.ensureDefaultCourts()
        }
    }

    fun selectUserRole(role: UserRole) {
        if (role == UserRole.EDITOR && !_isEditorUnlocked.value) {
            _showEditorPinDialog.value = true
            return
        }
        applyUserRole(role)
    }

    fun dismissEditorPinDialog() {
        _showEditorPinDialog.value = false
    }

    fun verifyEditorPinAndSwitch(pin: String): Boolean {
        return if (pin.trim() == CourtScheduleRepository.EDITOR_PIN_DEFAULT) {
            _isEditorUnlocked.value = true
            _showEditorPinDialog.value = false
            applyUserRole(UserRole.EDITOR)
            true
        } else {
            _snackbarMessages.tryEmit("PIN Pengelola salah. Masukkan PIN: ${CourtScheduleRepository.EDITOR_PIN_DEFAULT}")
            false
        }
    }

    private fun applyUserRole(role: UserRole) {
        if (_userRole.value == role) return
        _userRole.value = role
        if (role == UserRole.RENTER && (_activeTab.value == AppTab.PAYMENTS || _activeTab.value == AppTab.COURTS)) {
            _activeTab.value = AppTab.SCHEDULE
        }
        val msg = if (role == UserRole.RENTER) {
            "Mode Penyewa aktif: jadwal tersinkron otomatis."
        } else {
            "Mode Editor Website aktif: kelola tagihan, pembatalan, dan lapangan."
        }
        _snackbarMessages.tryEmit(msg)
    }

    fun selectTab(tab: AppTab) {
        if (_userRole.value == UserRole.RENTER && (tab == AppTab.PAYMENTS || tab == AppTab.COURTS)) {
            _activeTab.value = AppTab.SCHEDULE
            return
        }
        _activeTab.value = tab
    }

    fun selectDay(day: Int) {
        _selectedDay.value = day.coerceIn(1, 7)
    }

    fun selectCourtFilter(courtId: Int) {
        _selectedCourtId.value = courtId
    }

    fun selectSlotFilter(filter: SlotFilter) {
        _slotFilter.value = filter
    }

    fun openBookingDialog(courtId: Int, dayOfWeek: Int, startHour: Int) {
        _bookingDraft.value = BookingDraft(courtId, dayOfWeek, startHour)
    }

    fun dismissBookingDialog() {
        _bookingDraft.value = null
    }

    fun dismissConfirmationPrompt() {
        _confirmationPrompt.value = null
    }

    fun calculateSelectionsPrice(
        selections: List<CourtTimeSelection>,
        dayOfWeek: Int,
        racketCount: Int = 0,
        cockCount: Int = 0
    ): Long {
        val courtTotal = selections.sumOf {
            repository.calculateBookingPrice(it.court, dayOfWeek, it.startHour, it.durationHours)
        }
        return courtTotal + ScheduleFormatters.calculateExtrasPrice(racketCount, cockCount)
    }

    fun createManualCourtBookings(
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
    ) {
        viewModelScope.launch {
            val result = repository.createManualCourtSelections(
                selections = selections,
                dayOfWeek = dayOfWeek,
                customerName = customerName,
                customerPhone = customerPhone,
                paymentStatus = paymentStatus,
                paidAmount = paidAmount,
                reminderEnabled = reminderEnabled,
                racketCount = racketCount,
                cockCount = cockCount,
                notes = notes
            )
            result.onSuccess { savedBookings ->
                _bookingDraft.value = null
                _syncError.value = null
                val first = savedBookings.first()
                val courtNames = savedBookings.joinToString(" & ") { it.courtName }
                val totalCombinedPrice = savedBookings.sumOf { it.totalPrice }
                val schedulePerCourt = savedBookings.joinToString(" & ") {
                    "${it.courtName} (${ScheduleFormatters.formatHourRange(it.startHour, it.durationHours)})"
                }
                val extrasSummary = ScheduleFormatters.formatExtrasSummary(racketCount, cockCount)
                val baseSummary = "$schedulePerCourt • ${ScheduleFormatters.formatFullDayAndDate(first.dayOfWeek)}"
                val summary = if (extrasSummary.isNotBlank()) "$baseSummary\nTambahan: $extrasSummary" else baseSummary
                val waMsg = ScheduleFormatters.buildBookingConfirmWhatsAppMessage(
                    customerName = first.customerName,
                    scheduleSummary = summary,
                    totalPrice = totalCombinedPrice
                )
                _confirmationPrompt.value = BookingConfirmationPrompt(
                    customerName = first.customerName,
                    scheduleSummary = summary,
                    totalPrice = totalCombinedPrice,
                    whatsAppMessage = waMsg
                )
                _snackbarMessages.tryEmit("Jadwal $courtNames tersimpan & disinkronkan otomatis!")
            }.onFailure { err ->
                _snackbarMessages.tryEmit(err.message ?: "Gagal membuat pemesanan.")
            }
        }
    }

    fun markBookingPaid(bookingId: Long) {
        viewModelScope.launch {
            repository.markBookingPaid(bookingId)
                .onSuccess {
                    _snackbarMessages.tryEmit("Pembayaran ditandai Lunas & disinkronkan.")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Gagal menandai lunas. Gunakan akun pengelola.")
                }
        }
    }

    fun recordDownPayment(bookingId: Long, dpAmount: Long) {
        viewModelScope.launch {
            repository.recordDownPayment(bookingId, dpAmount)
                .onSuccess {
                    _snackbarMessages.tryEmit("Catatan DP berhasil diperbarui & disinkronkan.")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Gagal memperbarui DP.")
                }
        }
    }

    fun sendImmediateReminder(bookingId: Long) {
        viewModelScope.launch {
            repository.sendImmediateReminder(bookingId)
                .onSuccess { shown ->
                    val msg = if (shown) {
                        "Notifikasi pengingat pembayaran dikirim ke sistem."
                    } else {
                        "Pengingat dicatat. Aktifkan izin notifikasi agar tampil di status bar."
                    }
                    _snackbarMessages.tryEmit(msg)
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Gagal mengirim pengingat.")
                }
        }
    }

    fun cancelBooking(bookingId: Long) {
        viewModelScope.launch {
            repository.cancelBooking(bookingId)
                .onSuccess {
                    _snackbarMessages.tryEmit("Pemesanan dibatalkan & jadwal tersinkron otomatis.")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Hanya pembuat atau pengelola yang bisa membatalkan jadwal ini.")
                }
        }
    }

    fun saveCourt(court: CourtEntity) {
        viewModelScope.launch {
            repository.saveCourt(court)
                .onSuccess {
                    _snackbarMessages.tryEmit("Data ${court.name} tersimpan & disinkronkan.")
                }
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Gagal menyimpan perubahan lapangan.")
                }
        }
    }

    fun toggleCourtActive(court: CourtEntity) {
        viewModelScope.launch {
            repository.toggleCourtActive(court)
                .onFailure { err ->
                    _snackbarMessages.tryEmit(err.localizedMessage ?: "Gagal mengubah status lapangan.")
                }
        }
    }

    private fun currentIsoDayOfWeek(): Int {
        val cal = Calendar.getInstance()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }
}
