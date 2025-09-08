package com.lizaventas.lizachick.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*

fun Timestamp?.formatOrNull(pattern: String = "dd/MM/yyyy HH:mm"): String {
    return this?.toDate()?.let {
        SimpleDateFormat(pattern, Locale.getDefault()).format(it)
    } ?: "No definida"
}

fun Timestamp?.isExpired(): Boolean {
    return this?.toDate()?.let { Date().after(it) } ?: false
}
