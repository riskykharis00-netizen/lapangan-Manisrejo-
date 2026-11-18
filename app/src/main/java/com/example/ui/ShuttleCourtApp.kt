package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SportsTennis
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.repository.CourtScheduleRepository
import com.example.notifications.PaymentReminderNotifier
import com.example.ui.components.BookingFormBottomSheet
import com.example.ui.components.BookingSuccessWhatsAppDialog
import com.example.ui.screens.CourtsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PaymentsScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.theme.CourtEmerald
import com.example.ui.theme.CourtEmeraldDark
import com.example.ui.theme.ShuttleLimeBright
import com.example.ui.theme.StatusPaidBg
import com.example.ui.theme.StatusPaidFg
import com.example.util.ScheduleFormatters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun ShuttleCourtApp(
    currentUserId: String,
    userEmail: String? = null,
    onSignOutClick: () -> Unit = {}
) {
    val viewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val repository = CourtScheduleRepository(app.applicationContext, firestore, currentUserId)
                MainViewModel(repository, currentUserId, userEmail)
            }
        }
    )

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val bookingDraft by viewModel.bookingDraft.collectAsStateWithLifecycle()
    val confirmationPrompt by viewModel.confirmationPrompt.collectAsStateWithLifecycle()
    val showEditorPinDialog by viewModel.showEditorPinDialog.collectAsStateWithLifecycle()

    var hasNotificationPermission by remember {
        mutableStateOf(PaymentReminderNotifier.hasNotificationPermission(context))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        val msg = if (granted) {
            "Izin notifikasi diaktifkan. Konfirmasi & pengingat pembayaran siap dikirim."
        } else {
            "Izin notifikasi ditolak. Anda masih dapat memantau tagihan di tab Tagihan."
        }
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BackHandler(enabled = activeTab != AppTab.SCHEDULE) {
        viewModel.selectTab(AppTab.SCHEDULE)
    }

    val isEditorMode = uiState.userRole == UserRole.EDITOR

    val navItems = remember(isEditorMode) {
        buildList {
            add(Triple(AppTab.SCHEDULE, context.getString(R.string.nav_schedule), Icons.Filled.CalendarMonth to Icons.Outlined.CalendarMonth))
            add(Triple(AppTab.HISTORY, context.getString(R.string.nav_history), Icons.Filled.History to Icons.Outlined.History))
            if (isEditorMode) {
                add(Triple(AppTab.PAYMENTS, context.getString(R.string.nav_payments), Icons.Filled.NotificationsActive to Icons.Outlined.Notifications))
                add(Triple(AppTab.COURTS, context.getString(R.string.nav_courts), Icons.Filled.SportsTennis to Icons.Outlined.SportsTennis))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideWeb = maxWidth >= 720.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 960.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    colors = listOf(CourtEmerald, CourtEmeraldDark)
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.SportsTennis,
                                            contentDescription = null,
                                            tint = ShuttleLimeBright,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Lapangan Bulutangkis Manisrejo",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (isEditorMode) {
                                                    "Editor Website • ${ScheduleFormatters.formatCurrentDateFull()}"
                                                } else {
                                                    "Portal Penyewa • ${ScheduleFormatters.formatCurrentDateFull()}"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Surface(
                                                color = StatusPaidBg,
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(StatusPaidFg)
                                                    )
                                                    Text(
                                                        text = "Sinkron",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = StatusPaidFg
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isWideWeb) {
                                        WebNavMenuRow(
                                            navItems = navItems,
                                            activeTab = activeTab,
                                            pendingCount = uiState.pendingBookings.size,
                                            onSelectTab = viewModel::selectTab
                                        )
                                    }
                                    RoleSwitcherPill(
                                        userRole = uiState.userRole,
                                        onSelectRole = viewModel::selectUserRole
                                    )
                                    IconButton(
                                        onClick = onSignOutClick,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("sign_out_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Logout,
                                            contentDescription = "Keluar akun Google",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (!isWideWeb) {
                                WebNavMenuRow(
                                    navItems = navItems,
                                    activeTab = activeTab,
                                    pendingCount = uiState.pendingBookings.size,
                                    onSelectTab = viewModel::selectTab,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                val contentModifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)

                Column(modifier = contentModifier) {
                    uiState.syncError?.let { errorMsg ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Info sinkronisasi: $errorMsg",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (activeTab) {
                            AppTab.SCHEDULE -> ScheduleScreen(
                                uiState = uiState,
                                onSelectDay = viewModel::selectDay,
                                onSelectCourtFilter = viewModel::selectCourtFilter,
                                onSelectSlotFilter = viewModel::selectSlotFilter,
                                onSlotBookClick = viewModel::openBookingDialog,
                                onMarkPaid = viewModel::markBookingPaid,
                                onSendReminder = viewModel::sendImmediateReminder,
                                onCancelBooking = viewModel::cancelBooking,
                                onQuickBookClick = {
                                    val activeCourts = uiState.courts.filter { it.isActive }
                                    if (activeCourts.isNotEmpty()) {
                                        val defaultCourtId = if (uiState.selectedCourtId != 0) {
                                            uiState.selectedCourtId
                                        } else {
                                            activeCourts.first().id
                                        }
                                        viewModel.openBookingDialog(defaultCourtId, uiState.selectedDay, 18)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )

                            AppTab.HISTORY -> HistoryScreen(
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize()
                            )

                            AppTab.PAYMENTS -> PaymentsScreen(
                                uiState = uiState,
                                hasNotificationPermission = hasNotificationPermission,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onMarkPaid = viewModel::markBookingPaid,
                                onRecordDp = viewModel::recordDownPayment,
                                onSendReminderNow = viewModel::sendImmediateReminder,
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                modifier = Modifier.fillMaxSize()
                            )

                            AppTab.COURTS -> CourtsScreen(
                                uiState = uiState,
                                onSaveCourt = viewModel::saveCourt,
                                onToggleCourtActive = viewModel::toggleCourtActive,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditorPinDialog) {
        EditorPinDialog(
            onDismiss = viewModel::dismissEditorPinDialog,
            onVerifyPin = viewModel::verifyEditorPinAndSwitch
        )
    }

    bookingDraft?.let { draft ->
        if (uiState.courts.isNotEmpty()) {
            BookingFormBottomSheet(
                draft = draft,
                courts = uiState.courts,
                isEditorMode = isEditorMode,
                calculateSelectionsPrice = viewModel::calculateSelectionsPrice,
                onDismiss = viewModel::dismissBookingDialog,
                onConfirm = viewModel::createManualCourtBookings
            )
        }
    }

    confirmationPrompt?.let { prompt ->
        BookingSuccessWhatsAppDialog(
            prompt = prompt,
            onDismiss = viewModel::dismissConfirmationPrompt
        )
    }
}

@Composable
private fun EditorPinDialog(
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Masuk Mode Editor Pengelola") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Masukkan PIN Pengelola untuk mengakses tagihan, pembatalan jadwal, dan pengaturan lapangan.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it.filter { ch -> ch.isDigit() }.take(10)
                        isError = false
                    },
                    label = { Text("PIN Pengelola") },
                    placeholder = { Text("00000") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("PIN salah. Coba lagi.") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("editor_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val ok = onVerifyPin(pinInput)
                    if (!ok) isError = true
                },
                modifier = Modifier.testTag("editor_pin_confirm_button")
            ) {
                Text("Buka Editor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}

@Composable
private fun WebNavMenuRow(
    navItems: List<Triple<AppTab, String, Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.vector.ImageVector>>>,
    activeTab: AppTab,
    pendingCount: Int,
    onSelectTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        navItems.forEach { (tab, label, icons) ->
            val selected = activeTab == tab
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTab(tab) }
                    .testTag("nav_${tab.name.lowercase()}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tab == AppTab.PAYMENTS && pendingCount > 0) {
                        BadgedBox(
                            badge = { Badge { Text(pendingCount.toString()) } }
                        ) {
                            Icon(
                                imageVector = if (selected) icons.first else icons.second,
                                contentDescription = label,
                                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = if (selected) icons.first else icons.second,
                            contentDescription = label,
                            tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleSwitcherPill(
    userRole: UserRole,
    onSelectRole: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                UserRole.RENTER to "Penyewa",
                UserRole.EDITOR to "Editor"
            ).forEach { (role, label) ->
                val selected = userRole == role
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) {
                        if (role == UserRole.EDITOR) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    },
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectRole(role) }
                        .testTag("role_${role.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) {
                                if (role == UserRole.EDITOR) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
