package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.notifications.PaymentReminderNotifier
import com.example.ui.ShuttleCourtApp
import com.example.ui.theme.ShuttleCourtTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PaymentReminderNotifier.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            ShuttleCourtTheme {
                ShuttleCourtApp()
            }
        }
    }
}
