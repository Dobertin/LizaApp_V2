package com.lizaventas.lizachick.models

import com.google.firebase.Timestamp

data class DetalleVenta(
    val productoNombre: String = "",
    val cantidad: Int = 0,
    val precioUnitario: Double = 0.0,
    val subtotal: Double = 0.0
)

data class Venta(
    val id: String = "",
    val clienteNombre: String = "",
    val usuario: String = "",
    val fechaVenta: Timestamp? = null,
    val numeroFactura: String = "",
    val igv: Double = 0.0,
    val total: Double = 0.0,
    val subtotal: Double = 0.0,
    val medioPago: String = "",
    val estado: Boolean = true,
    val ventaParcial: Boolean = true,
    val delivery: Boolean = false,
    val observaciones: String = "",
    val detalles: Map<String, DetalleVenta> = emptyMap()
)