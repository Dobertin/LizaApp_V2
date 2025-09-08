package com.lizaventas.lizachick.models

import com.google.firebase.Timestamp

data class Gasto(
    var id: String = "",
    var motivo: String = "",
    var fecha: Timestamp? = null,
    var monto: Double = 0.0,
    var estadopago: Boolean = false, // false = pendiente, true = pagado
    var estado: Boolean = true
)