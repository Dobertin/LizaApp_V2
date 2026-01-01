package com.lizaventas.lizachick.activities

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.PedidosAdapter
import com.lizaventas.lizachick.databinding.ActivityPedidosBinding
import com.lizaventas.lizachick.databinding.DialogAgregarPedidoBinding
import com.lizaventas.lizachick.databinding.DialogAgregarProductosBinding
import com.lizaventas.lizachick.databinding.DialogModificarPedidoCompletoBinding
import com.lizaventas.lizachick.databinding.ItemProductoTempBinding
import com.lizaventas.lizachick.models.DetallePedido
import com.lizaventas.lizachick.models.Pedido
import com.lizaventas.lizachick.receivers.EntregaNotificationReceiver
import java.text.SimpleDateFormat
import java.util.*

class PedidosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPedidosBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pedidosAdapter: PedidosAdapter

    private val pedidosList = mutableListOf<Pedido>()
    private val productosTemporales = mutableListOf<DetallePedido>()
    private var productosEncontrados = mutableListOf<Map<String, Any>>()
    private var currentUser: String? = null
    private var fechaEntregaSeleccionada: Long? = null

    companion object {
        private const val TAG = "PedidosActivity"
        private const val PREFS_NAME = "TiendaPrefs"
        private const val KEY_USUARIO = "usuario_activo"
    }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPedidosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDarkTheme()
        setupToolbar()
        initializeComponents()
        setupRecyclerView()
        setupClickListeners()
        cargarPedidos()

        // Solicitar permiso de notificaciones para Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }

        // Solicitar permiso de alarmas exactas para Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                Intent().also { intent ->
                    intent.action = Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    startActivity(intent)
                }
            }
        }

    }

    // ==================== SETUP ====================

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Gestión de Pedidos"
        }
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initializeComponents() {
        firestore = Firebase.firestore
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        currentUser = sharedPreferences.getString(KEY_USUARIO, "")
    }

    private fun setupRecyclerView() {
        pedidosAdapter = PedidosAdapter(pedidosList) { pedido, action ->
            when (action) {
                "modificar" -> mostrarDialogModificarPedido(pedido)
                "cancelar" -> cancelarPedido(pedido)
                "eliminar" -> confirmarEliminacionPedido(pedido)
                "marcar_entregado" -> marcarComoEntregado(pedido)
            }
        }
        binding.recyclerPedidos.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = pedidosAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAgregarPedido.setOnClickListener {
            mostrarDialogAgregarPedido()
        }
    }

    // ==================== CARGAR PEDIDOS ====================

    private fun cargarPedidos() {
        binding.progressBar.visibility = View.VISIBLE

        firestore.collection("pedidos")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->
                pedidosList.clear()

                for (document in documents) {
                    val detallesMap = mutableMapOf<String, DetallePedido>()
                    val detalles = document.get("detalles") as? Map<String, Any>

                    detalles?.forEach { (key, value) ->
                        val detalleMap = value as Map<String, Any>
                        detallesMap[key] = DetallePedido(
                            productoNombre = detalleMap["productoNombre"] as? String ?: "",
                            cantidad = (detalleMap["cantidad"] as? Long)?.toInt() ?: 0,
                            precioUnitario = detalleMap["precioUnitario"] as? Double ?: 0.0,
                            subtotal = detalleMap["subtotal"] as? Double ?: 0.0
                        )
                    }

                    val fechaPedidoValue = document.get("fechaPedido")
                    val fechaPedido = when (fechaPedidoValue) {
                        is Long -> fechaPedidoValue.toString()
                        is String -> fechaPedidoValue
                        else -> System.currentTimeMillis().toString()
                    }

                    val fechaEntregaValue = document.get("fechaEntrega")
                    val fechaEntrega = when (fechaEntregaValue) {
                        is Long -> fechaEntregaValue
                        else -> null
                    }

                    val pedido = Pedido(
                        id = document.id,
                        clienteNombre = document.getString("clienteNombre") ?: "",
                        usuario = document.getString("usuario") ?: "",
                        fechaPedido = fechaPedido,
                        total = document.getDouble("total") ?: 0.0,
                        abonado = document.getDouble("abonado") ?: 0.0,
                        medioPago = document.getString("medioPago") ?: "",
                        estado = document.getBoolean("estado") ?: true,
                        delivery = document.getBoolean("delivery") ?: false,
                        observaciones = document.getString("observaciones") ?: "",
                        detalles = detallesMap,
                        fechaEntrega = fechaEntrega,
                        entregado = document.getBoolean("entregado") ?: false
                    )
                    pedidosList.add(pedido)
                }

                pedidosAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE

                binding.tvEmptyState.visibility = if (pedidosList.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error al cargar pedidos: ${exception.message}")
                Toast.makeText(this, "Error al cargar pedidos", Toast.LENGTH_SHORT).show()
            }
    }

    // ==================== CREAR PEDIDO ====================

    private fun mostrarDialogAgregarPedido() {
        productosTemporales.clear()
        fechaEntregaSeleccionada = null
        val dialogBinding = DialogAgregarProductosBinding.inflate(layoutInflater)

        val productoTempAdapter = ProductoTempAdapter(productosTemporales) { position ->
            productosTemporales.removeAt(position)
            dialogBinding.recyclerProductosTemp.adapter?.notifyItemRemoved(position)
            actualizarTotalTemporal(dialogBinding)
        }

        dialogBinding.recyclerProductosTemp.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = productoTempAdapter
        }

        val sugerenciasAdapter = SugerenciasProductosAdapter(productosEncontrados) { productoSeleccionado ->
            dialogBinding.etProductoNombre.setText(productoSeleccionado["nombre"] as String)
            dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
        }

        dialogBinding.recyclerSugerenciasProductos.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = sugerenciasAdapter
        }

        dialogBinding.etProductoNombre.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val texto = s.toString().trim()
                if (texto.length >= 3) {
                    buscarProductosPorNombre(texto, sugerenciasAdapter, dialogBinding)
                } else {
                    productosEncontrados.clear()
                    sugerenciasAdapter.notifyDataSetChanged()
                    dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Crear Nuevo Pedido")
            .setView(dialogBinding.root)
            .setPositiveButton("Continuar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialogBinding.btnAgregarProducto.setOnClickListener {
            val productoNombre = dialogBinding.etProductoNombre.text.toString().trim()
            val cantidad = dialogBinding.etCantidad.text.toString().toIntOrNull() ?: 0
            val precioUnitario = dialogBinding.etPrecioUnitario.text.toString().toDoubleOrNull() ?: 0.0

            if (validarProducto(productoNombre, cantidad, precioUnitario)) {
                val subtotal = cantidad * precioUnitario
                productosTemporales.add(DetallePedido(productoNombre, cantidad, precioUnitario, subtotal))

                dialogBinding.etProductoNombre.setText("")
                dialogBinding.etCantidad.setText("")
                dialogBinding.etPrecioUnitario.setText("")

                productoTempAdapter.notifyItemInserted(productosTemporales.size - 1)
                actualizarTotalTemporal(dialogBinding)
            }
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                if (productosTemporales.isEmpty()) {
                    Toast.makeText(this, "Debe agregar al menos un producto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                mostrarDialogDatosCliente(productosTemporales.toList())
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun mostrarDialogDatosCliente(productos: List<DetallePedido>) {
        val dialogBinding = DialogAgregarPedidoBinding.inflate(layoutInflater)

        // Obtener el LinearLayout dentro del ScrollView
        val linearLayout = dialogBinding.root.getChildAt(0) as LinearLayout

        // Agregar CheckBox y botón para fecha de entrega
        val checkBoxFechaEntrega = CheckBox(this).apply {
            text = "Programar fecha de entrega"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
        }

        val btnSeleccionarFecha = com.google.android.material.button.MaterialButton(this).apply {
            text = "Seleccionar Fecha y Hora"
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }

        val tvFechaSeleccionada = TextView(this).apply {
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8
            }
        }

        checkBoxFechaEntrega.setOnCheckedChangeListener { _, isChecked ->
            btnSeleccionarFecha.isEnabled = isChecked
            if (!isChecked) {
                fechaEntregaSeleccionada = null
                tvFechaSeleccionada.visibility = View.GONE
            }
        }

        // Agregar vistas al LinearLayout (NO directamente al ScrollView)
        linearLayout.addView(checkBoxFechaEntrega)
        linearLayout.addView(btnSeleccionarFecha)
        linearLayout.addView(tvFechaSeleccionada)

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Datos del Cliente y Pago")
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar Pedido", null)
            .setNegativeButton("Cancelar", null)
            .create()

        btnSeleccionarFecha.setOnClickListener {
            seleccionarFechaEntrega { fechaFormateada ->
                tvFechaSeleccionada.text = "Entrega: $fechaFormateada"
                tvFechaSeleccionada.visibility = View.VISIBLE
            }
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val clienteNombre = dialogBinding.etClienteNombre.text.toString().trim()
                val abonado = dialogBinding.etAbonado.text.toString().toDoubleOrNull() ?: 0.0
                val observaciones = dialogBinding.etObservaciones.text.toString().trim()
                val medioPago = dialogBinding.spinnerMedioPago.selectedItem.toString()
                val total = productos.sumOf { it.subtotal }

                if (validarDatosCliente(clienteNombre, abonado, observaciones, total)) {
                    if (checkBoxFechaEntrega.isChecked && fechaEntregaSeleccionada == null) {
                        Toast.makeText(this, "Debe seleccionar una fecha de entrega", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    guardarPedidoConProductos(clienteNombre, abonado, observaciones, medioPago, productos)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun guardarPedidoConProductos(
        clienteNombre: String,
        abonado: Double,
        observaciones: String,
        medioPago: String,
        productos: List<DetallePedido>
    ) {
        val pedidoId = "pedido_${System.currentTimeMillis()}"
        val fechaActual = com.google.firebase.Timestamp.now()

        val detallesMap = mutableMapOf<String, Map<String, Any>>()
        var totalReal = 0.0

        productos.forEachIndexed { index, producto ->
            val detalleId = "detalle_${index + 1}"
            detallesMap[detalleId] = mapOf(
                "productoNombre" to producto.productoNombre,
                "cantidad" to producto.cantidad,
                "precioUnitario" to producto.precioUnitario,
                "subtotal" to producto.subtotal
            )
            totalReal += producto.subtotal
        }

        val pedidoData = mutableMapOf<String, Any>(
            "clienteNombre" to clienteNombre,
            "usuario" to currentUser!!,
            "fechaPedido" to fechaActual,
            "total" to totalReal,
            "abonado" to abonado,
            "medioPago" to medioPago,
            "estado" to true,
            "delivery" to false,
            "observaciones" to observaciones,
            "detalles" to detallesMap,
            "entregado" to false
        )

        fechaEntregaSeleccionada?.let {
            pedidoData["fechaEntrega"] = it
        }

        firestore.collection("pedidos").document(pedidoId)
            .set(pedidoData)
            .addOnSuccessListener {
                Toast.makeText(this, "Pedido creado exitosamente", Toast.LENGTH_SHORT).show()

                fechaEntregaSeleccionada?.let { fechaEntrega ->
                    val cantidadProductos = productos.size
                    programarNotificacionesEntrega(pedidoId, fechaEntrega, cantidadProductos, clienteNombre)
                }

                cargarPedidos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al crear pedido: ${exception.message}")
                Toast.makeText(this, "Error al crear pedido", Toast.LENGTH_SHORT).show()
            }
    }

    // ==================== MODIFICAR PEDIDO ====================

    private fun mostrarDialogModificarPedido(pedido: Pedido) {
        val dialogBinding = DialogModificarPedidoCompletoBinding.inflate(layoutInflater)
        val productosModificables = pedido.detalles.toMutableMap()

        dialogBinding.tvClienteNombre.text = "Cliente: ${pedido.clienteNombre}"
        dialogBinding.tvTotalActual.text = "Total: S/.${String.format("%.2f", pedido.total)}"
        dialogBinding.tvAbonadoActual.text = "Abonado: S/.${String.format("%.2f", pedido.abonado)}"
        val pendiente = pedido.total - pedido.abonado
        dialogBinding.tvPendiente.text = "Pendiente: S/.${String.format("%.2f", pendiente)}"

        val productosAdapter = ProductosModificablesAdapter(productosModificables) { detalleId ->
            if (productosModificables.size > 1) {
                productosModificables.remove(detalleId)
                dialogBinding.recyclerProductosPedido.adapter?.notifyDataSetChanged()
                actualizarTotalModificacion(dialogBinding, productosModificables)
            } else {
                Toast.makeText(this, "Debe mantener al menos un producto", Toast.LENGTH_SHORT).show()
            }
        }

        dialogBinding.recyclerProductosPedido.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = productosAdapter
        }

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Modificar Pedido")
            .setView(dialogBinding.root)
            .setPositiveButton("Actualizar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val nuevoAbono = dialogBinding.etNuevoAbono.text.toString().toDoubleOrNull() ?: 0.0

                if (nuevoAbono <= 0) {
                    Toast.makeText(this, "Ingrese un abono válido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val nuevoTotal = productosModificables.values.sumOf { it.subtotal }
                val totalAbono = pedido.abonado + nuevoAbono

                if (totalAbono > nuevoTotal) {
                    Toast.makeText(this, "El abono total no puede superar el total del pedido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                actualizarPedidoCompleto(pedido, productosModificables, totalAbono, nuevoTotal)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun actualizarPedidoCompleto(
        pedido: Pedido,
        productosModificados: Map<String, DetallePedido>,
        nuevoAbonoTotal: Double,
        nuevoTotal: Double
    ) {
        val actualizaciones = mutableMapOf<String, Any>(
            "abonado" to nuevoAbonoTotal,
            "total" to nuevoTotal,
            "detalles" to productosModificados
        )

        if (nuevoAbonoTotal >= nuevoTotal) {
            actualizaciones["estado"] = false
            actualizaciones["entregado"] = true
            val pedidoActualizado = pedido.copy(
                total = nuevoTotal,
                abonado = nuevoAbonoTotal,
                detalles = productosModificados
            )
            convertirPedidoAVenta(pedidoActualizado, nuevoAbonoTotal)
        }

        firestore.collection("pedidos").document(pedido.id)
            .update(actualizaciones)
            .addOnSuccessListener {
                Toast.makeText(this, "Pedido actualizado exitosamente", Toast.LENGTH_SHORT).show()
                cargarPedidos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al actualizar pedido: ${exception.message}")
                Toast.makeText(this, "Error al actualizar pedido", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarTotalModificacion(
        dialogBinding: DialogModificarPedidoCompletoBinding,
        productos: Map<String, DetallePedido>
    ) {
        val nuevoTotal = productos.values.sumOf { it.subtotal }
        dialogBinding.tvTotalActual.text = "Total: S/.${String.format("%.2f", nuevoTotal)}"
    }

    // ==================== ACCIONES DE PEDIDO ====================

    private fun cancelarPedido(pedido: Pedido) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Cancelar Pedido")
            .setMessage("¿Está seguro de que desea cancelar este pedido?")
            .setPositiveButton("Sí") { _, _ ->
                firestore.collection("pedidos").document(pedido.id)
                    .update("estado", false)
                    .addOnSuccessListener {
                        cancelarNotificacionesPedido(pedido.id)
                        Toast.makeText(this, "Pedido cancelado", Toast.LENGTH_SHORT).show()
                        cargarPedidos()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al cancelar pedido: ${exception.message}")
                        Toast.makeText(this, "Error al cancelar pedido", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun confirmarEliminacionPedido(pedido: Pedido) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Eliminar Pedido")
            .setMessage("¿Está seguro de que desea eliminar este pedido permanentemente?")
            .setPositiveButton("Eliminar") { _, _ ->
                firestore.collection("pedidos").document(pedido.id)
                    .delete()
                    .addOnSuccessListener {
                        cancelarNotificacionesPedido(pedido.id)
                        Toast.makeText(this, "Pedido eliminado", Toast.LENGTH_SHORT).show()
                        cargarPedidos()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al eliminar pedido: ${exception.message}")
                        Toast.makeText(this, "Error al eliminar pedido", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun marcarComoEntregado(pedido: Pedido) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Marcar como Entregado")
            .setMessage("¿Confirma que este pedido ha sido entregado?")
            .setPositiveButton("Sí") { _, _ ->
                firestore.collection("pedidos").document(pedido.id)
                    .update("entregado", true)
                    .addOnSuccessListener {
                        cancelarNotificacionesPedido(pedido.id)
                        Toast.makeText(this, "Pedido marcado como entregado", Toast.LENGTH_SHORT).show()
                        cargarPedidos()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al marcar como entregado: ${exception.message}")
                        Toast.makeText(this, "Error al actualizar pedido", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("No", null)
            .show()
    }

    // ==================== NOTIFICACIONES ====================

    private fun programarNotificacionesEntrega(
        pedidoId: String,
        fechaEntrega: Long,
        cantidadProductos: Int,
        clienteNombre: String
    ) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dateFormat = SimpleDateFormat("dd/MM 'a las' h:mm a", Locale.getDefault())
        val fechaFormateada = dateFormat.format(Date(fechaEntrega))

        // Notificación 24 horas antes
        val tiempo24Horas = fechaEntrega - (24 * 60 * 60 * 1000)
        if (tiempo24Horas > System.currentTimeMillis()) {
            val intent24h = Intent(this, EntregaNotificationReceiver::class.java).apply {
                putExtra("pedidoId", pedidoId)
                putExtra("cantidadProductos", cantidadProductos)
                putExtra("fechaEntrega", fechaFormateada)
                putExtra("clienteNombre", clienteNombre)
                putExtra("tipoNotificacion", "24h")
            }

            val pendingIntent24h = PendingIntent.getBroadcast(
                this,
                pedidoId.hashCode(),
                intent24h,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        tiempo24Horas,
                        pendingIntent24h
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    tiempo24Horas,
                    pendingIntent24h
                )
            }
        }

        // Notificación 90 minutos antes
        val tiempo90Min = fechaEntrega - (90 * 60 * 1000)
        if (tiempo90Min > System.currentTimeMillis()) {
            val intent90min = Intent(this, EntregaNotificationReceiver::class.java).apply {
                putExtra("pedidoId", pedidoId)
                putExtra("cantidadProductos", cantidadProductos)
                putExtra("fechaEntrega", fechaFormateada)
                putExtra("clienteNombre", clienteNombre)
                putExtra("tipoNotificacion", "90min")
            }

            val pendingIntent90min = PendingIntent.getBroadcast(
                this,
                (pedidoId.hashCode() + 1),
                intent90min,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        tiempo90Min,
                        pendingIntent90min
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    tiempo90Min,
                    pendingIntent90min
                )
            }
        }

        Log.d(TAG, "Notificaciones programadas para pedido: $pedidoId")
    }

    private fun cancelarNotificacionesPedido(pedidoId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent24h = Intent(this, EntregaNotificationReceiver::class.java)
        val pendingIntent24h = PendingIntent.getBroadcast(
            this,
            pedidoId.hashCode(),
            intent24h,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent24h)

        val intent90min = Intent(this, EntregaNotificationReceiver::class.java)
        val pendingIntent90min = PendingIntent.getBroadcast(
            this,
            (pedidoId.hashCode() + 1),
            intent90min,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent90min)

        Log.d(TAG, "Notificaciones canceladas para pedido: $pedidoId")
    }

    // ==================== CONVERTIR A VENTA ====================

    private fun convertirPedidoAVenta(pedido: Pedido, abonoFinal: Double) {
        obtenerSiguienteNumeroFactura("Nota de Venta") { numeroFactura ->
            if (numeroFactura != null) {
                val fechaActual = com.google.firebase.Timestamp.now()
                val total = pedido.total
                val igv = total * 0.18
                val subtotal = total - igv

                val ventaData = mapOf(
                    "clienteNombre" to pedido.clienteNombre,
                    "usuario" to pedido.usuario,
                    "numeroFactura" to numeroFactura,
                    "fechaVenta" to fechaActual,
                    "subtotal" to subtotal,
                    "descuento" to 0.0,
                    "igv" to igv,
                    "total" to total,
                    "medioPago" to pedido.medioPago,
                    "estado" to true,
                    "delivery" to pedido.delivery,
                    "observaciones" to pedido.observaciones,
                    "ventaParcial" to false,
                    "detalles" to pedido.detalles
                )

                val ventaId = "venta_${System.currentTimeMillis()}"

                firestore.collection("ventas").document(ventaId)
                    .set(ventaData)
                    .addOnSuccessListener {
                        descontarStockProductos(pedido.detalles)
                        Toast.makeText(this, "Pedido convertido a venta: $numeroFactura", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al convertir a venta: ${exception.message}")
                    }
            }
        }
    }

    private fun descontarStockProductos(detalles: Map<String, DetallePedido>) {
        detalles.values.forEach { detalle ->
            firestore.collection("productos")
                .whereEqualTo("nombre", detalle.productoNombre)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val document = documents.first()
                        val stockActual = document.getLong("stock") ?: 0
                        val nuevoStock = maxOf(0, stockActual - detalle.cantidad)

                        firestore.collection("productos").document(document.id)
                            .update("stock", nuevoStock)
                            .addOnSuccessListener {
                                Log.d(TAG, "Stock actualizado para ${detalle.productoNombre}: $nuevoStock")
                            }
                            .addOnFailureListener { exception ->
                                Log.e(TAG, "Error al actualizar stock de ${detalle.productoNombre}: ${exception.message}")
                            }
                    } else {
                        Log.d(TAG, "Producto no encontrado para descuento de stock: ${detalle.productoNombre}")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al buscar producto ${detalle.productoNombre}: ${exception.message}")
                }
        }
    }

    private fun obtenerSiguienteNumeroFactura(tipoComprobante: String, callback: (String?) -> Unit) {
        val comprobanteId = when (tipoComprobante) {
            "Boleta" -> "comprobante_boleta"
            "Factura" -> "comprobante_Factura"
            "Nota de Venta" -> "comprobante_NotaVenta"
            "Nota de Crédito" -> "comprobante_NotaCredito"
            else -> "comprobante_NotaVenta"
        }

        val comprobanteRef = firestore.collection("comprobante").document(comprobanteId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(comprobanteRef)

            val abreviatura = snapshot.getString("abreviatura") ?: "NV"
            var corrComprobante = snapshot.getLong("corrComprobante")?.toInt() ?: 1
            var correlativo = snapshot.getLong("correlativo")?.toInt() ?: 0

            correlativo++

            if (correlativo > 99999) {
                correlativo = 1
                corrComprobante++
            }

            transaction.update(comprobanteRef, mapOf(
                "correlativo" to correlativo,
                "corrComprobante" to corrComprobante
            ))

            val corrComprobanteFormateado = corrComprobante.toString().padStart(3, '0')
            val correlativoFormateado = correlativo.toString().padStart(5, '0')

            "$abreviatura$corrComprobanteFormateado-$correlativoFormateado"

        }.addOnSuccessListener { numeroFactura ->
            callback(numeroFactura)
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Error al obtener número de factura: ${exception.message}")
            callback(null)
        }
    }

    // ==================== UTILIDADES ====================

    private fun buscarProductosPorNombre(
        nombre: String,
        adapter: SugerenciasProductosAdapter,
        dialogBinding: DialogAgregarProductosBinding
    ) {
        val nombreLower = nombre.lowercase()

        firestore.collection("productos")
            .get()
            .addOnSuccessListener { documents ->
                productosEncontrados.clear()
                for (document in documents) {
                    val nombreProducto = document.getString("nombre") ?: ""

                    if (nombreProducto.lowercase().contains(nombreLower)) {
                        val productoData = document.data.toMutableMap()
                        productoData["id"] = document.id
                        productosEncontrados.add(productoData)
                    }
                }

                if (productosEncontrados.isNotEmpty()) {
                    adapter.notifyDataSetChanged()
                    dialogBinding.recyclerSugerenciasProductos.visibility = View.VISIBLE
                } else {
                    dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al buscar productos: ${exception.message}")
                dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
            }
    }

    private fun seleccionarFechaEntrega(callback: ((String) -> Unit)? = null) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(this, R.style.AlertDialogDark, { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

            TimePickerDialog(this, R.style.AlertDialogDark, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)

                fechaEntregaSeleccionada = calendar.timeInMillis

                val dateFormat = SimpleDateFormat("dd/MM/yyyy 'a las' h:mm a", Locale.getDefault())
                val fechaFormateada = dateFormat.format(calendar.time)

                callback?.invoke(fechaFormateada)
                Toast.makeText(this, "Fecha de entrega: $fechaFormateada", Toast.LENGTH_SHORT).show()

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = System.currentTimeMillis()
        }.show()
    }

    private fun actualizarTotalTemporal(dialogBinding: DialogAgregarProductosBinding) {
        val total = productosTemporales.sumOf { it.subtotal }
        dialogBinding.tvTotalTemp.text = "Total: S/.${String.format("%.2f", total)}"
    }

    // ==================== VALIDACIONES ====================

    private fun validarProducto(nombre: String, cantidad: Int, precio: Double): Boolean {
        when {
            nombre.isEmpty() -> {
                Toast.makeText(this, "Ingrese el nombre del producto", Toast.LENGTH_SHORT).show()
                return false
            }
            cantidad <= 0 -> {
                Toast.makeText(this, "La cantidad debe ser mayor a 0", Toast.LENGTH_SHORT).show()
                return false
            }
            precio <= 0 -> {
                Toast.makeText(this, "El precio debe ser mayor a 0", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun validarDatosCliente(clienteNombre: String, abonado: Double, observaciones: String, total: Double): Boolean {
        when {
            clienteNombre.isEmpty() -> {
                Toast.makeText(this, "Ingrese el nombre del cliente", Toast.LENGTH_SHORT).show()
                return false
            }
            observaciones.isEmpty() -> {
                Toast.makeText(this, "Ingrese las observaciones", Toast.LENGTH_SHORT).show()
                return false
            }
            abonado < 0 -> {
                Toast.makeText(this, "El abono no puede ser negativo", Toast.LENGTH_SHORT).show()
                return false
            }
            abonado > total -> {
                Toast.makeText(this, "El abono no puede ser mayor al total", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    // ==================== ADAPTERS INTERNOS ====================

    inner class SugerenciasProductosAdapter(
        private val productos: List<Map<String, Any>>,
        private val onProductoSeleccionado: (Map<String, Any>) -> Unit
    ) : RecyclerView.Adapter<SugerenciasProductosAdapter.SugerenciaViewHolder>() {

        inner class SugerenciaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvNombreProducto: TextView = itemView.findViewById(R.id.tvNombreProducto)
            private val tvStockDisponible: TextView = itemView.findViewById(R.id.tvStockDisponible)

            fun bind(producto: Map<String, Any>) {
                tvNombreProducto.text = producto["nombre"] as? String ?: ""
                val stock = producto["stock"] as? Long ?: 0
                tvStockDisponible.text = "Stock: $stock"

                itemView.setOnClickListener {
                    onProductoSeleccionado(producto)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SugerenciaViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_sugerencia_producto, parent, false)
            return SugerenciaViewHolder(view)
        }

        override fun onBindViewHolder(holder: SugerenciaViewHolder, position: Int) {
            holder.bind(productos[position])
        }

        override fun getItemCount(): Int = productos.size
    }

    class ProductoTempAdapter(
        private val productos: MutableList<DetallePedido>,
        private val onEliminar: (Int) -> Unit
    ) : RecyclerView.Adapter<ProductoTempAdapter.ProductoViewHolder>() {

        inner class ProductoViewHolder(private val binding: ItemProductoTempBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(producto: DetallePedido, position: Int) {
                binding.apply {
                    tvProductoNombre.text = producto.productoNombre
                    tvDetalles.text = "Cant: ${producto.cantidad} x S/.${String.format("%.2f", producto.precioUnitario)}"
                    tvSubtotal.text = "S/.${String.format("%.2f", producto.subtotal)}"

                    btnEliminarProducto.setOnClickListener {
                        onEliminar(position)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
            val binding = ItemProductoTempBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ProductoViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
            holder.bind(productos[position], position)
        }

        override fun getItemCount(): Int = productos.size
    }

    class ProductosModificablesAdapter(
        private val productos: MutableMap<String, DetallePedido>,
        private val onEliminar: (String) -> Unit
    ) : RecyclerView.Adapter<ProductosModificablesAdapter.ProductoViewHolder>() {

        inner class ProductoViewHolder(private val binding: ItemProductoTempBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(entry: Map.Entry<String, DetallePedido>) {
                val (detalleId, producto) = entry
                binding.apply {
                    tvProductoNombre.text = producto.productoNombre
                    tvDetalles.text = "Cant: ${producto.cantidad} x S/.${String.format("%.2f", producto.precioUnitario)}"
                    tvSubtotal.text = "S/.${String.format("%.2f", producto.subtotal)}"

                    btnEliminarProducto.setOnClickListener {
                        onEliminar(detalleId)
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
            val binding = ItemProductoTempBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ProductoViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
            val entry = productos.entries.toList()[position]
            holder.bind(entry)
        }

        override fun getItemCount(): Int = productos.size
    }
}