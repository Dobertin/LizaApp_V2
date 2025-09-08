package com.lizaventas.lizachick.models

data class DetallePedido(
    val productoNombre: String = "",
    val cantidad: Int = 0,
    val precioUnitario: Double = 0.0,
    val subtotal: Double = 0.0
)

data class Pedido(
    val id: String = "",
    val clienteNombre: String = "",
    val usuario: String = "",
    val fechaPedido: String = "",
    val total: Double = 0.0,
    val abonado: Double = 0.0,
    val medioPago: String = "",
    val estado: Boolean = true,
    val delivery: Boolean = false,
    val observaciones: String = "",
    val detalles: Map<String, DetallePedido> = emptyMap()
)
