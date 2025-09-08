package com.lizaventas.lizachick.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import java.text.SimpleDateFormat
import java.util.*

class PedidosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPedidosBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var pedidosAdapter: PedidosAdapter
    private val pedidosList = mutableListOf<Pedido>()
    private var currentUser: String? = null
    private val productosTemporales = mutableListOf<DetallePedido>()
    private var productosEncontrados = mutableListOf<Map<String, Any>>()

    companion object {
        private const val TAG = "PedidosActivity"
    }

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
    }

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Gestión de Pedidos"
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initializeComponents() {
        firestore = Firebase.firestore
        sharedPreferences = getSharedPreferences("TiendaPrefs", MODE_PRIVATE)
        currentUser = sharedPreferences.getString("usuario_activo", "")
    }

    private fun setupRecyclerView() {
        pedidosAdapter = PedidosAdapter(pedidosList) { pedido, action ->
            when (action) {
                "modificar" -> mostrarDialogModificarPedido(pedido)
                "cancelar" -> cancelarPedido(pedido)
                "eliminar" -> confirmarEliminacionPedido(pedido)
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

                    // Manejar fechaPedido como timestamp o string
                    val fechaPedidoValue = document.get("fechaPedido")
                    val fechaPedido = when (fechaPedidoValue) {
                        is Long -> fechaPedidoValue.toString() // Si es timestamp
                        is String -> fechaPedidoValue // Si es string
                        else -> System.currentTimeMillis().toString()
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
                        detalles = detallesMap
                    )
                    pedidosList.add(pedido)
                }

                pedidosAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE

                if (pedidosList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                }
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error al cargar pedidos: ${exception.message}")
                Toast.makeText(this, "Error al cargar pedidos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun descontarStockProductos(detalles: Map<String, DetallePedido>) {
        detalles.values.forEach { detalle ->
            // Buscar producto por nombre exacto
            firestore.collection("productos")
                .whereEqualTo("nombre", detalle.productoNombre)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val document = documents.first()
                        val stockActual = document.getLong("stock") ?: 0
                        val nuevoStock = maxOf(0, stockActual - detalle.cantidad)

                        // Actualizar stock
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
                        // Descontar stock después de crear la venta
                        descontarStockProductos(pedido.detalles)
                        Toast.makeText(this, "Pedido convertido a venta: $numeroFactura", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error al convertir a venta: ${exception.message}")
                    }
            }
        }
    }
    private fun cancelarPedido(pedido: Pedido) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Cancelar Pedido")
            .setMessage("¿Está seguro de que desea cancelar este pedido?")
            .setPositiveButton("Sí") { _, _ ->
                firestore.collection("pedidos").document(pedido.id)
                    .update("estado", false)
                    .addOnSuccessListener {
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

            // Incrementar correlativo
            correlativo++

            // Si el correlativo excede 99999, reiniciar y aumentar corrComprobante
            if (correlativo > 99999) {
                correlativo = 1
                corrComprobante++
            }

            // Actualizar el documento
            transaction.update(comprobanteRef, mapOf(
                "correlativo" to correlativo,
                "corrComprobante" to corrComprobante
            ))

            // Generar número de factura
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

    // Clase para adaptador de productos temporales
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

    private fun mostrarDialogAgregarPedido() {
        productosTemporales.clear()
        val dialogBinding = DialogAgregarProductosBinding.inflate(layoutInflater)

        // Configurar RecyclerView de productos temporales
        val productoTempAdapter = ProductoTempAdapter(productosTemporales) { position ->
            productosTemporales.removeAt(position)
            dialogBinding.recyclerProductosTemp.adapter?.notifyItemRemoved(position)
            actualizarTotalTemporal(dialogBinding)
        }

        dialogBinding.recyclerProductosTemp.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = productoTempAdapter
        }

        // Configurar RecyclerView de sugerencias
        val sugerenciasAdapter = SugerenciasProductosAdapter(productosEncontrados) { productoSeleccionado ->
            dialogBinding.etProductoNombre.setText(productoSeleccionado["nombre"] as String)
            dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
        }

        dialogBinding.recyclerSugerenciasProductos.apply {
            layoutManager = LinearLayoutManager(this@PedidosActivity)
            adapter = sugerenciasAdapter
        }

        // Configurar listener para búsqueda de productos
        dialogBinding.etProductoNombre.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val texto = s.toString().trim()
                Log.e(TAG, "Texto de busqueda: ${texto}")
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

        // Configurar botón agregar producto
        dialogBinding.btnAgregarProducto.setOnClickListener {
            val productoNombre = dialogBinding.etProductoNombre.text.toString().trim()
            val cantidad = dialogBinding.etCantidad.text.toString().toIntOrNull() ?: 0
            val precioUnitario = dialogBinding.etPrecioUnitario.text.toString().toDoubleOrNull() ?: 0.0

            if (validarProducto(productoNombre, cantidad, precioUnitario)) {
                val subtotal = cantidad * precioUnitario
                productosTemporales.add(DetallePedido(productoNombre, cantidad, precioUnitario, subtotal))

                // Limpiar campos
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

    private fun buscarProductosPorNombre(
        nombre: String,
        adapter: SugerenciasProductosAdapter,
        dialogBinding: DialogAgregarProductosBinding
    ) {
        Log.d(TAG, "Buscando productos con nombre: $nombre")

        val nombreLower = nombre.lowercase()

        // Buscar con diferentes variaciones de mayúsculas/minúsculas
        firestore.collection("productos")
            .get()
            .addOnSuccessListener { documents ->
                productosEncontrados.clear()
                for (document in documents) {
                    val nombreProducto = document.getString("nombre") ?: ""

                    // Buscar coincidencia parcial (contiene el texto, sin importar mayúsculas)
                    if (nombreProducto.lowercase().contains(nombreLower)) {
                        val productoData = document.data.toMutableMap()
                        productoData["id"] = document.id
                        productosEncontrados.add(productoData)
                    }
                }

                Log.d(TAG, "Productos encontrados: ${productosEncontrados.size}")

                if (productosEncontrados.isNotEmpty()) {
                    adapter.notifyDataSetChanged()
                    dialogBinding.recyclerSugerenciasProductos.visibility = View.VISIBLE
                    Log.d(TAG, "Mostrando RecyclerView de sugerencias")
                } else {
                    dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
                    Log.d(TAG, "Ocultando RecyclerView - no hay productos")
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al buscar productos: ${exception.message}")
                dialogBinding.recyclerSugerenciasProductos.visibility = View.GONE
            }
    }

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

    private fun actualizarTotalTemporal(dialogBinding: DialogAgregarProductosBinding) {
        val total = productosTemporales.sumOf { it.subtotal }
        dialogBinding.tvTotalTemp.text = "Total: S/.${String.format("%.2f", total)}"
    }

    private fun mostrarDialogDatosCliente(productos: List<DetallePedido>) {
        val dialogBinding = DialogAgregarPedidoBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Datos del Cliente y Pago")
            .setView(dialogBinding.root)
            .setPositiveButton("Guardar Pedido", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val clienteNombre = dialogBinding.etClienteNombre.text.toString().trim()
                val abonado = dialogBinding.etAbonado.text.toString().toDoubleOrNull() ?: 0.0
                val observaciones = dialogBinding.etObservaciones.text.toString().trim()
                val medioPago = dialogBinding.spinnerMedioPago.selectedItem.toString()

                val total = productos.sumOf { it.subtotal }

                if (validarDatosCliente(clienteNombre, abonado, observaciones, total)) {
                    guardarPedidoConProductos(clienteNombre, abonado, observaciones, medioPago, productos)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
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

    private fun guardarPedidoConProductos(
        clienteNombre: String,
        abonado: Double,
        observaciones: String,
        medioPago: String,
        productos: List<DetallePedido>
    ) {
        val pedidoId = "pedido_${System.currentTimeMillis()}"
        val fechaActual = com.google.firebase.Timestamp.now()

        // Crear detalles
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

        val pedidoData = mapOf(
            "clienteNombre" to clienteNombre,
            "usuario" to currentUser!!,
            "fechaPedido" to fechaActual,
            "total" to totalReal,
            "abonado" to abonado,
            "medioPago" to medioPago,
            "estado" to true,
            "delivery" to false,
            "observaciones" to observaciones,
            "detalles" to detallesMap
        )

        firestore.collection("pedidos").document(pedidoId)
            .set(pedidoData)
            .addOnSuccessListener {
                Toast.makeText(this, "Pedido creado exitosamente", Toast.LENGTH_SHORT).show()
                cargarPedidos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al crear pedido: ${exception.message}")
                Toast.makeText(this, "Error al crear pedido", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarDialogModificarPedido(pedido: Pedido) {
        val dialogBinding = DialogModificarPedidoCompletoBinding.inflate(layoutInflater)
        val productosModificables = pedido.detalles.toMutableMap()

        // Mostrar información actual
        dialogBinding.tvClienteNombre.text = "Cliente: ${pedido.clienteNombre}"
        dialogBinding.tvTotalActual.text = "Total: S/.${String.format("%.2f", pedido.total)}"
        dialogBinding.tvAbonadoActual.text = "Abonado: S/.${String.format("%.2f", pedido.abonado)}"
        val pendiente = pedido.total - pedido.abonado
        dialogBinding.tvPendiente.text = "Pendiente: S/.${String.format("%.2f", pendiente)}"

        // Configurar RecyclerView de productos
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

    private fun actualizarTotalModificacion(
        dialogBinding: DialogModificarPedidoCompletoBinding,
        productos: Map<String, DetallePedido>
    ) {
        val nuevoTotal = productos.values.sumOf { it.subtotal }
        dialogBinding.tvTotalActual.text = "Total: S/.${String.format("%.2f", nuevoTotal)}"
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

        // Si el abono total iguala el total, convertir a venta
        if (nuevoAbonoTotal >= nuevoTotal) {
            actualizaciones["estado"] = false
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

    // Adaptador para productos modificables
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
