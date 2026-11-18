package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
): Nothing? {
    val user = runCatching { Firebase.auth.currentUser }.getOrNull()
    val payload = JSONObject().apply {
        put("error", error.message ?: error.toString())
        put("operationType", operationType.value)
        put("path", path ?: JSONObject.NULL)
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", user?.uid ?: JSONObject.NULL)
                put("email", user?.email ?: JSONObject.NULL)
                put("emailVerified", user?.isEmailVerified ?: false)
                put("isAnonymous", user?.isAnonymous ?: false)
                put("tenantId", user?.tenantId ?: JSONObject.NULL)
            }
        )
    }
    Log.e("FirestoreError", "Firestore Error: $payload", error)
    return null
}
