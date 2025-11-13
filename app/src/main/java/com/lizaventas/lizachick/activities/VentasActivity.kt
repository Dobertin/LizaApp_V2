package com.lizaventas.lizachick.activities

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.ProductosAdapter
import com.lizaventas.lizachick.adapters.CarritoAdapter
import com.lizaventas.lizachick.databinding.ActivityVentasBinding
import com.lizaventas.lizachick.models.Producto
import com.lizaventas.lizachick.models.ItemCarrito

class VentasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVentasBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var productosAdapter: ProductosAdapter
    private lateinit var carritoAdapter: CarritoAdapter
    private lateinit var sharedPreferences: SharedPreferences

    private var todosLosProductos = mutableListOf<Producto>()
    private var productosFiltrados = mutableListOf<Producto>()
    private var carrito = mutableListOf<ItemCarrito>()
    private var categorias = mutableListOf<String>()
    private var marcas = mutableListOf<String>()

    private lateinit var categoriasAdapter: ArrayAdapter<String>
    private lateinit var marcasAdapter: ArrayAdapter<String>

    private var currentUser: String? = null
    private var currentRole: String? = null

    companion object {
        private const val TAG = "VentasActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVentasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences("TiendaPrefs", MODE_PRIVATE)

        if (!getUserData()) return

        setupUI()
        initializeFirestore()
        setupRecyclerViews()
        setupFilters()
        setupCarrito()
        loadProductos()
    }

    private fun getUserData(): Boolean {
        currentUser = intent.getStringExtra("usuario")
            ?: sharedPreferences.getString("usuario_activo", null)
        currentRole = intent.getStringExtra("rol")
            ?: sharedPreferences.getString("rol_activo", null)

        if (currentUser == null || currentRole == null) {
            finish()
            return false
        }
        return true
    }

    private fun setupUI() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "Ventas"
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun initializeFirestore() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupRecyclerViews() {
        productosAdapter = ProductosAdapter(
            productos = productosFiltrados,
            onImageClick = ::mostrarImagenCompleta,
            onNombreClick = ::mostrarDescripcion,
            onAgregarCarrito = ::agregarAlCarrito
        )

        binding.recyclerProductos.apply {
            layoutManager = GridLayoutManager(this@VentasActivity, 2)
            adapter = productosAdapter
        }

        carritoAdapter = CarritoAdapter(
            items = carrito,
            onCantidadChanged = ::actualizarCantidad,
            onPrecioChanged = ::actualizarPrecio,
            onEliminar = ::eliminarDelCarrito
        )

        binding.recyclerCarrito.apply {
            layoutManager = LinearLayoutManager(this@VentasActivity)
            adapter = carritoAdapter
            isNestedScrollingEnabled = true
            setPadding(paddingLeft, paddingTop, paddingRight, 350)
            clipToPadding = false
        }
    }

    private fun setupFilters() {
        categorias.add("Todas las categorías")
        marcas.add("Todas las marcas")

        setupSpinnerAdapters()

        // Filtro por nombre
        binding.etFiltroNombre.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filtrarProductos() }
        })

        // Filtro por familia olfativa (ahora como texto)
        binding.etFiltroFamiliaOlfativa.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filtrarProductos() }
        })

        binding.btnLimpiarFiltros.setOnClickListener { limpiarFiltros() }
    }

    private fun setupSpinnerAdapters() {
        val spinnerItemLayout = R.layout.spinner_item_dark
        val dropdownLayout = android.R.layout.simple_spinner_dropdown_item

        // Adapter de categorías
        categoriasAdapter = createSpinnerAdapter(categorias, spinnerItemLayout)
        categoriasAdapter.setDropDownViewResource(dropdownLayout)
        binding.spinnerCategoria.adapter = categoriasAdapter
        binding.spinnerCategoria.onItemSelectedListener = createSpinnerListener()

        // Adapter de marcas
        marcasAdapter = createSpinnerAdapter(marcas, spinnerItemLayout)
        marcasAdapter.setDropDownViewResource(dropdownLayout)
        binding.spinnerMarca.adapter = marcasAdapter
        binding.spinnerMarca.onItemSelectedListener = createSpinnerListener()
    }

    private fun createSpinnerAdapter(data: List<String>, layout: Int): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, layout, data) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getView(position, convertView, parent) as TextView).apply {
                    setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                }
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                return (super.getDropDownView(position, convertView, parent) as TextView).apply {
                    setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                    setBackgroundColor(ContextCompat.getColor(this@VentasActivity, R.color.card_background))
                }
            }
        }
    }

    private fun createSpinnerListener() = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            filtrarProductos()
        }
        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun setupCarrito() {
        binding.btnToggleCarrito.setOnClickListener { toggleCarritoVisibility() }
        binding.btnProcesarVenta.setOnClickListener { procesarVenta() }
        binding.btnLimpiarCarrito.setOnClickListener { limpiarCarrito() }

        binding.layoutCarrito.visibility = View.GONE
        updateCarritoBadge()
        calcularTotal()
    }

    private fun loadProductos() {
        Log.d(TAG, "Cargando productos desde Firestore...")

        firestore.collection("productos")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Productos obtenidos: ${documents.size()}")

                todosLosProductos.clear()
                val categoriasSet = mutableSetOf<String>()
                val marcasSet = mutableSetOf<String>()

                for (document in documents) {
                    try {
                        val producto = Producto(
                            id = document.id,
                            nombre = document.getString("nombre") ?: "",
                            categoriaNombre = document.getString("categoriaNombre") ?: "",
                            marcaNombre = document.getString("marcaNombre") ?: "",
                            descripcion = document.getString("descripcion") ?: "",
                            genero = document.getString("genero") ?: "",
                            familiaOlfativa = document.getString("familiaOlfativa"),
                            capacidad = document.getString("capacidad"),
                            precioCatalogo = document.getDouble("precioCatalogo") ?: 0.0,
                            precioVenta = document.getDouble("precioVenta") ?: 0.0,
                            stock = document.getLong("stock")?.toInt() ?: 0,
                            imagenUrl = document.getString("imagenUrl") ?: "",
                            estado = document.getBoolean("estado") ?: true
                        )

                        todosLosProductos.add(producto)

                        if (producto.categoriaNombre.isNotEmpty()) categoriasSet.add(producto.categoriaNombre)
                        if (producto.marcaNombre.isNotEmpty()) marcasSet.add(producto.marcaNombre)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar producto ${document.id}: ${e.message}")
                    }
                }

                updateFilterLists(categoriasSet, marcasSet)

                productosFiltrados.clear()
                productosFiltrados.addAll(todosLosProductos)
                productosAdapter.notifyDataSetChanged()

                Log.d(TAG, "Productos cargados exitosamente")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar productos: ${exception.message}")
                Toast.makeText(this, "Error al cargar productos", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateFilterLists(categoriasSet: Set<String>, marcasSet: Set<String>) {
        categorias.clear()
        marcas.clear()

        categorias.add("Todas las categorías")
        marcas.add("Todas las marcas")

        categorias.addAll(categoriasSet.sorted())
        marcas.addAll(marcasSet.sorted())

        runOnUiThread {
            categoriasAdapter.notifyDataSetChanged()
            marcasAdapter.notifyDataSetChanged()
        }

        Log.d(TAG, "Filtros actualizados - Categorías: ${categorias.size}, Marcas: ${marcas.size}")
    }

    private fun filtrarProductos() {
        val filtroNombre = binding.etFiltroNombre.text.toString().lowercase().trim()
        val filtroFamilia = binding.etFiltroFamiliaOlfativa.text.toString().lowercase().trim()
        val categoriaSeleccionada = binding.spinnerCategoria.selectedItem?.toString()
        val marcaSeleccionada = binding.spinnerMarca.selectedItem?.toString()

        productosFiltrados.clear()

        todosLosProductos.forEach { producto ->
            val cumpleNombre = filtroNombre.isEmpty() || producto.nombre.lowercase().contains(filtroNombre)
            val cumpleFamilia = filtroFamilia.isEmpty() ||
                    (producto.familiaOlfativa?.lowercase()?.contains(filtroFamilia) == true)
            val cumpleCategoria = categoriaSeleccionada == "Todas las categorías" ||
                    producto.categoriaNombre == categoriaSeleccionada
            val cumpleMarca = marcaSeleccionada == "Todas las marcas" ||
                    producto.marcaNombre == marcaSeleccionada

            if (cumpleNombre && cumpleFamilia && cumpleCategoria && cumpleMarca) {
                productosFiltrados.add(producto)
            }
        }

        productosAdapter.notifyDataSetChanged()
        Log.d(TAG, "Productos filtrados: ${productosFiltrados.size}")
    }

    private fun limpiarFiltros() {
        binding.etFiltroNombre.setText("")
        binding.etFiltroFamiliaOlfativa.setText("")
        binding.spinnerCategoria.setSelection(0)
        binding.spinnerMarca.setSelection(0)
    }

    private fun mostrarImagenCompleta(producto: Producto) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_imagen_completa, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.ivImagenCompleta)
        val tvNombre = dialogView.findViewById<TextView>(R.id.tvNombreProducto)

        tvNombre.text = producto.nombre

        Glide.with(this)
            .load(producto.imagenUrl)
            .placeholder(R.drawable.ic_producto_placeholder)
            .error(R.drawable.ic_producto_placeholder)
            .into(imageView)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Cerrar") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun mostrarDescripcion(producto: Producto) {
        if (producto.descripcion.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(producto.nombre)
                .setMessage(producto.descripcion)
                .setPositiveButton("Cerrar") { dialog, _ -> dialog.dismiss() }
                .create()
                .show()
        } else {
            Toast.makeText(this, "No hay descripción disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun agregarAlCarrito(producto: Producto) {
        if (producto.stock <= 0) {
            Toast.makeText(this, "Producto sin stock", Toast.LENGTH_SHORT).show()
            return
        }

        val itemExistente = carrito.find { it.producto.id == producto.id }

        if (itemExistente != null) {
            if (itemExistente.cantidad < producto.stock) {
                itemExistente.cantidad++
                carritoAdapter.notifyDataSetChanged()
                updateCarritoBadge()
                calcularTotal()
                Toast.makeText(this, "Cantidad actualizada", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No hay suficiente stock", Toast.LENGTH_SHORT).show()
            }
        } else {
            carrito.add(ItemCarrito(producto, 1, producto.precioVenta))
            carritoAdapter.notifyDataSetChanged()
            updateCarritoBadge()
            calcularTotal()
            Toast.makeText(this, "Producto agregado al carrito", Toast.LENGTH_SHORT).show()
        }
    }

    private fun actualizarCantidad(item: ItemCarrito, nuevaCantidad: Int) {
        if (nuevaCantidad <= 0) {
            eliminarDelCarrito(item)
            return
        }

        if (nuevaCantidad <= item.producto.stock + 2) {
            item.cantidad = nuevaCantidad
            carritoAdapter.notifyDataSetChanged()
            updateCarritoBadge()
            calcularTotal()
        } else {
            Toast.makeText(this, "Stock disponible: ${item.producto.stock}", Toast.LENGTH_SHORT).show()
            carritoAdapter.notifyDataSetChanged()
        }
    }

    private fun actualizarPrecio(item: ItemCarrito, nuevoPrecio: Double) {
        if (nuevoPrecio > 0) {
            item.precioUnitario = nuevoPrecio
            calcularTotal()
            Toast.makeText(this, "Precio actualizado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "El precio debe ser mayor a 0", Toast.LENGTH_SHORT).show()
            carritoAdapter.notifyItemChanged(carrito.indexOf(item))
        }
    }

    private fun eliminarDelCarrito(item: ItemCarrito) {
        carrito.remove(item)
        carritoAdapter.notifyDataSetChanged()
        updateCarritoBadge()
        calcularTotal()
        Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show()
    }

    private fun updateCarritoBadge() {
        val totalItems = carrito.sumOf { it.cantidad }
        binding.tvCarritoBadge.text = totalItems.toString()
        binding.tvCarritoBadge.visibility = if (totalItems > 0) View.VISIBLE else View.GONE
        binding.btnProcesarVenta.visibility = if (totalItems > 0) View.VISIBLE else View.GONE
        binding.btnLimpiarCarrito.visibility = if (totalItems > 0) View.VISIBLE else View.GONE
    }

    private fun calcularTotal() {
        val total = carrito.sumOf { it.cantidad * it.precioUnitario }
        binding.tvTotal.text = "Total: S/. %.2f".format(total)
    }

    private fun toggleCarritoVisibility() {
        if (binding.layoutCarrito.visibility == View.GONE) {
            binding.layoutCarrito.visibility = View.VISIBLE
            binding.btnToggleCarrito.setImageResource(R.drawable.ic_close)
        } else {
            binding.layoutCarrito.visibility = View.GONE
            binding.btnToggleCarrito.setImageResource(R.drawable.ic_shopping_cart)
        }
    }

    private fun limpiarCarrito() {
        AlertDialog.Builder(this)
            .setTitle("Limpiar Carrito")
            .setMessage("¿Estás seguro de eliminar todos los productos?")
            .setPositiveButton("Sí") { _, _ ->
                carrito.clear()
                carritoAdapter.notifyDataSetChanged()
                updateCarritoBadge()
                calcularTotal()
                Toast.makeText(this, "Carrito limpiado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .create()
            .show()
    }

    private fun procesarVenta() {
        if (carrito.isEmpty()) {
            Toast.makeText(this, "El carrito está vacío", Toast.LENGTH_SHORT).show()
            return
        }
        validarStockYProcesar()
    }

    private fun validarStockYProcesar() {
        val productosConStockInsuficiente = carrito
            .filter { it.cantidad > it.producto.stock }
            .map { "${it.producto.nombre} (Disponible: ${it.producto.stock}, Solicitado: ${it.cantidad})" }

        if (productosConStockInsuficiente.isNotEmpty()) {
            val mensaje = "Stock insuficiente:\n\n${productosConStockInsuficiente.joinToString("\n")}\n\n¿Continuar?"

            AlertDialog.Builder(this)
                .setTitle("Stock Insuficiente")
                .setMessage(mensaje)
                .setPositiveButton("Continuar") { _, _ -> mostrarDialogoVenta() }
                .setNegativeButton("Cancelar", null)
                .create()
                .show()
        } else {
            mostrarDialogoVenta()
        }
    }

    private fun mostrarDialogoVenta() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_procesar_venta, null)

        val etNombreCliente = dialogView.findViewById<EditText>(R.id.etNombreCliente)
        val spinnerTipoComprobante = dialogView.findViewById<Spinner>(R.id.spinnerTipoComprobante)
        val spinnerMedioPago = dialogView.findViewById<Spinner>(R.id.spinnerMedioPago)
        val switchDelivery = dialogView.findViewById<Switch>(R.id.switchDelivery)
        val etObservaciones = dialogView.findViewById<EditText>(R.id.etObservaciones)
        val tvTotal = dialogView.findViewById<TextView>(R.id.tvTotalVenta)

        val total = carrito.sumOf { it.cantidad * it.precioUnitario }
        tvTotal.text = "Total: S/. %.2f".format(total)

        val tiposComprobante = arrayOf("Nota de Venta", "Nota de Crédito", "Boleta", "Factura")
        spinnerTipoComprobante.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tiposComprobante)
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        cargarMediosPago(spinnerMedioPago)
        etNombreCliente.selectAll()

        AlertDialog.Builder(this)
            .setTitle("Procesar Venta")
            .setView(dialogView)
            .setPositiveButton("Confirmar") { _, _ ->
                val nombreCliente = etNombreCliente.text.toString().trim().ifEmpty { "Usuario Genérico" }
                val tipoComprobante = spinnerTipoComprobante.selectedItem.toString()
                val medioPago = spinnerMedioPago.selectedItem.toString()
                val esDelivery = switchDelivery.isChecked
                val observaciones = etObservaciones.text.toString().ifEmpty { "Ninguna" }
                val esVentaParcial = tipoComprobante == "Nota de Crédito"

                procesarVentaFinal(nombreCliente, tipoComprobante, medioPago, esDelivery, observaciones, esVentaParcial)
            }
            .setNegativeButton("Cancelar", null)
            .create()
            .show()

        etNombreCliente.requestFocus()
    }

    private fun cargarMediosPago(spinner: Spinner) {
        firestore.collection("mediosPago")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->
                val mediosPago = documents.mapNotNull { it.getString("nombre") }
                    .filter { it.isNotEmpty() }
                    .toMutableList()

                if (mediosPago.isEmpty()) {
                    mediosPago.addAll(listOf("Efectivo", "Yape", "Plin", "Transferencia", "Tarjeta"))
                }

                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mediosPago)
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error al cargar medios de pago: ${it.message}")
                val mediosPagoDefault = arrayOf("Efectivo", "Yape", "Plin", "Transferencia", "Tarjeta")
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mediosPagoDefault)
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            }
    }

    private fun procesarVentaFinal(
        nombreCliente: String,
        tipoComprobante: String,
        medioPago: String,
        esDelivery: Boolean,
        observaciones: String,
        esVentaParcial: Boolean
    ) {
        obtenerSiguienteNumeroFactura(tipoComprobante) { numeroFactura ->
            if (numeroFactura != null) {
                guardarVenta(nombreCliente, numeroFactura, tipoComprobante, medioPago, esDelivery, observaciones, esVentaParcial)
            } else {
                Toast.makeText(this, "Error al generar número de comprobante", Toast.LENGTH_LONG).show()
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

            "$abreviatura${corrComprobante.toString().padStart(3, '0')}-${correlativo.toString().padStart(5, '0')}"

        }.addOnSuccessListener(callback)
            .addOnFailureListener {
                Log.e(TAG, "Error al obtener número de factura: ${it.message}")
                callback(null)
            }
    }

    private fun guardarVenta(
        nombreCliente: String,
        numeroFactura: String,
        tipoComprobante: String,
        medioPago: String,
        esDelivery: Boolean,
        observaciones: String,
        esVentaParcial: Boolean
    ) {
        val total = carrito.sumOf { it.cantidad * it.precioUnitario }
        val igv = total * 0.18
        val subtotal = total - igv

        val detalles = carrito.mapIndexed { index, item ->
            "detalle_${index + 1}" to mapOf(
                "productoNombre" to item.producto.nombre,
                "cantidad" to item.cantidad,
                "precioUnitario" to item.precioUnitario,
                "subtotal" to (item.cantidad * item.precioUnitario)
            )
        }.toMap()

        val venta = mapOf(
            "clienteNombre" to nombreCliente,
            "usuario" to (currentUser ?: "usuario_desconocido"),
            "numeroFactura" to numeroFactura,
            "fechaVenta" to com.google.firebase.Timestamp.now(),
            "subtotal" to subtotal,
            "igv" to igv,
            "total" to total,
            "medioPago" to medioPago,
            "estado" to true,
            "delivery" to esDelivery,
            "observaciones" to observaciones,
            "ventaParcial" to esVentaParcial,
            "detalles" to detalles
        )

        firestore.collection("ventas")
            .add(venta)
            .addOnSuccessListener {
                Log.d(TAG, "Venta guardada con ID: ${it.id}")
                Toast.makeText(this, "Venta procesada\nCliente: $nombreCliente\nNúmero: $numeroFactura", Toast.LENGTH_LONG).show()

                actualizarStockProductos()

                carrito.clear()
                carritoAdapter.notifyDataSetChanged()
                updateCarritoBadge()
                calcularTotal()

                binding.layoutCarrito.visibility = View.GONE
                binding.btnToggleCarrito.setImageResource(R.drawable.ic_shopping_cart)
            }
            .addOnFailureListener {
                Log.e(TAG, "Error al guardar venta: ${it.message}")
                Toast.makeText(this, "Error al procesar venta", Toast.LENGTH_LONG).show()
            }
    }

    private fun actualizarStockProductos() {
        // Usar una transacción para garantizar consistencia
        val batch = firestore.batch()
        var productosVerificados = 0
        val totalProductos = carrito.size

        // Primero obtenemos todos los stocks actuales
        carrito.forEach { item ->
            val productoRef = firestore.collection("productos").document(item.producto.id)

            productoRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val stockActual = document.getLong("stock")?.toInt() ?: 0
                        val nuevoStock = stockActual - item.cantidad

                        if (nuevoStock <= 0) {
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    "Advertencia: Stock negativo para ${item.producto.nombre}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        batch.update(productoRef, "stock", nuevoStock)
                        Log.d(TAG, "${item.producto.nombre}: $stockActual -> $nuevoStock")
                    }

                    productosVerificados++

                    // Solo ejecutamos el batch cuando todos los productos fueron verificados
                    if (productosVerificados == totalProductos) {
                        ejecutarBatchActualizacion(batch)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al obtener producto ${item.producto.id}: ${exception.message}")
                    productosVerificados++

                    if (productosVerificados == totalProductos) {
                        ejecutarBatchActualizacion(batch)
                    }
                }
        }
    }

    private fun ejecutarBatchActualizacion(batch: com.google.firebase.firestore.WriteBatch) {
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Stock actualizado exitosamente")
                runOnUiThread {
                    Toast.makeText(this, "Stock actualizado correctamente", Toast.LENGTH_SHORT).show()
                    loadProductos()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al actualizar stock: ${exception.message}")
                runOnUiThread {
                    Toast.makeText(this, "Error al actualizar stock", Toast.LENGTH_SHORT).show()
                }
            }
    }
}