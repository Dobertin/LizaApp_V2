package com.lizaventas.lizachick.models

import com.google.protobuf.Internal

data class GastoReporte(
    val id: String = "",
    val motivo: String = "",
    val fecha: Long = 0L,
    val monto: Double = 0.0,
    val estado: Boolean = true,
    val estadopago: Boolean = true
)