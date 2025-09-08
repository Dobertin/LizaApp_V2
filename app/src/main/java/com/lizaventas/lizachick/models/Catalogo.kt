package com.lizaventas.lizachick.models

import com.google.firebase.Timestamp

data class Catalogo(
    val id: String = "",
    val nombre: String = "",
    val estado: Boolean = true,
    val vigencia: Timestamp? = null,
    val rutaUrl: String = ""
)