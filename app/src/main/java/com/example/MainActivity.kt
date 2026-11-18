package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import com.example.auth.GoogleAuthScreen
import com.example.auth.signOut
import com.example.notifications.PaymentReminderNotifier
import com.example.ui.ShuttleCourtApp
import com.example.ui.theme.ShuttleCourtTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PaymentReminderNotifier.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            ShuttleCourtTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val credentialManager = remember(context) { CredentialManager.create(context) }

                var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose { Firebase.auth.removeAuthStateListener(listener) }
                }

                val user = currentUser
                if (user == null) {
                    GoogleAuthScreen(
                        onAuthSuccess = { currentUser = Firebase.auth.currentUser }
                    )
                } else {
                    key(user.uid) {
                        ShuttleCourtApp(
                            currentUserId = user.uid,
                            userEmail = user.email,
                            onSignOutClick = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onSignOutComplete = { currentUser = null }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
