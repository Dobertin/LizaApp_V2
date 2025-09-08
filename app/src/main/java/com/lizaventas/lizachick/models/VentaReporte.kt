package com.lizaventas.lizachick.models

data class VentaReporte(
    val id: String = "",
    val clienteNombre: String = "",
    val fechaVenta: Long = 0L,
    val total: Double = 0.0,
    val medioPago: String = "",
    val cantidadProductos: Int = 0,
    val detalles: List<Map<String, Any>> = emptyList()
)