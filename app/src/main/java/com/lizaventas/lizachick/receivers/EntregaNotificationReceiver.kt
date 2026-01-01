package com.lizaventas.lizachick.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.activities.PedidosActivity

class EntregaNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "EntregaNotification"
        private const val CHANNEL_ID = "entrega_pedidos_channel"
        private const val CHANNEL_NAME = "Entregas de Pedidos"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pedidoId = intent.getStringExtra("pedidoId") ?: return
        val cantidadProductos = intent.getIntExtra("cantidadProductos", 0)
        val fechaEntrega = intent.getStringExtra("fechaEntrega") ?: ""
        val clienteNombre = intent.getStringExtra("clienteNombre") ?: ""
        val tipoNotificacion = intent.getStringExtra("tipoNotificacion") ?: ""

        Log.d(TAG, "Notificación recibida para pedido: $pedidoId, tipo: $tipoNotificacion")

        // Verificar si el pedido ya fue entregado o cancelado
        verificarEstadoPedido(context, pedidoId) { estaActivo, entregado ->
            if (estaActivo && !entregado) {
                mostrarNotificacion(context, pedidoId, cantidadProductos, fechaEntrega, clienteNombre, tipoNotificacion)
            } else {
                Log.d(TAG, "Pedido $pedidoId no activo o ya entregado. No se muestra notificación.")
            }
        }
    }

    private fun verificarEstadoPedido(
        context: Context,
        pedidoId: String,
        callback: (Boolean, Boolean) -> Unit
    ) {
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection("pedidos").document(pedidoId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val estado = document.getBoolean("estado") ?: false
                    val entregado = document.getBoolean("entregado") ?: false
                    callback(estado, entregado)
                } else {
                    callback(false, false)
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al verificar estado del pedido: ${exception.message}")
                callback(false, false)
            }
    }

    private fun mostrarNotificacion(
        context: Context,
        pedidoId: String,
        cantidadProductos: Int,
        fechaEntrega: String,
        clienteNombre: String,
        tipoNotificacion: String
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear canal de notificación para Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de entregas programadas de pedidos"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Intent para abrir la actividad de pedidos al tocar la notificación
        val openIntent = Intent(context, PedidosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            pedidoId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Crear contenido de la notificación
        val titulo = when (tipoNotificacion) {
            "24h" -> "Entrega Mañana"
            "90min" -> "Entrega Próxima"
            else -> "Recordatorio de Entrega"
        }

        val mensaje = "Se tiene programada una entrega de $cantidadProductos productos " +
                "para el $fechaEntrega"

        val mensajeExpandido = "Cliente: $clienteNombre\n" +
                "Productos: $cantidadProductos\n" +
                "Fecha: $fechaEntrega"

        // Construir notificación
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText(mensajeExpandido))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        // Generar ID único para cada notificación
        val notificationId = when (tipoNotificacion) {
            "24h" -> pedidoId.hashCode()
            "90min" -> pedidoId.hashCode() + 1
            else -> pedidoId.hashCode()
        }

        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Notificación mostrada: ID=$notificationId, tipo=$tipoNotificacion")
    }
}