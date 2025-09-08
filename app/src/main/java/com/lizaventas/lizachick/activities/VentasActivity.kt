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

    private var todosLosProductos = mutableListOf<Producto>()
    private var productosFiltrados = mutableListOf<Producto>()
    private var carrito = mutableListOf<ItemCarrito>()

    private var categorias = mutableListOf<String>()
    private var marcas = mutableListOf<String>()
    private var familiasOlfativas = mutableListOf<String>()

    // Adapters para los spinners
    private lateinit var categoriasAdapter: ArrayAdapter<String>
    private lateinit var marcasAdapter: ArrayAdapter<String>
    private lateinit var familiasAdapter: ArrayAdapter<String>

    // Nuevas variables para la venta
    private var currentUser: String? = null
    private var currentRole: String? = null
    private lateinit var sharedPreferences: SharedPreferences

    // Agregar después de las otras variables
    private fun getUserData() {
        // Primero intentar obtener desde Intent
        currentUser = intent.getStringExtra("usuario")
        currentRole = intent.getStringExtra("rol")
        // Si no están en el Intent, obtener desde SharedPreferences
        if (currentUser == null || currentRole == null) {
            currentUser = sharedPreferences.getString("usuario_activo", null)
            currentRole = sharedPreferences.getString("rol_activo", null)
        }
        // Si aún no hay datos, regresar al login
        if (currentUser == null || currentRole == null) {
            returnToLogin()
        }
    }

    private fun returnToLogin() {
        // Implementar según tu lógica de navegación
        finish()
    }

    companion object {
        private const val TAG = "VentasActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVentasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("TiendaPrefs", MODE_PRIVATE)

        // Obtener datos del usuario
        getUserData()
        // Configurar tema dark
        setupDarkTheme()
        // Configurar toolbar inmediatamente
        setupToolbar()
        initializeFirestore()
        setupRecyclerViews()
        setupFilters()
        setupCarrito()
        loadProductos()
    }
    private fun setupDarkTheme() {
        // Solo configurar el color de la status bar
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun setupToolbar() {
        try {
            Log.d(TAG, "Configurando toolbar...")

            // Configuración simple del toolbar sin modificar padding ni altura
            setSupportActionBar(binding.toolbar)

            supportActionBar?.let { actionBar ->
                actionBar.title = "Ventas"
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.setDisplayShowHomeEnabled(true)
                Log.d(TAG, "ActionBar configurado exitosamente")
            } ?: run {
                Log.e(TAG, "ActionBar es null después de setSupportActionBar")
                binding.toolbar.title = "Ventas"
            }

            binding.toolbar.setNavigationOnClickListener {
                Log.d(TAG, "Navigation click - cerrando actividad")
                onBackPressed()
            }

            Log.d(TAG, "Toolbar configurado correctamente")

        } catch (e: Exception) {
            Log.e(TAG, "Error configurando toolbar: ${e.message}", e)
            binding.toolbar.title = "Ventas"
            binding.toolbar.setNavigationOnClickListener {
                onBackPressed()
            }
        }
    }

    private fun initializeFirestore() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupRecyclerViews() {
        // RecyclerView para productos
        productosAdapter = ProductosAdapter(
            productos = productosFiltrados,
            onImageClick = { producto -> mostrarImagenCompleta(producto) },
            onNombreClick = { producto -> mostrarDescripcion(producto) },
            onAgregarCarrito = { producto -> agregarAlCarrito(producto) }
        )

        binding.recyclerProductos.apply {
            layoutManager = GridLayoutManager(this@VentasActivity, 2)
            adapter = productosAdapter
        }

        // RecyclerView para carrito
        carritoAdapter = CarritoAdapter(
            items = carrito,
            onCantidadChanged = { item, cantidad -> actualizarCantidad(item, cantidad) },
            onPrecioChanged = { item, precio -> actualizarPrecio(item, precio) },
            onEliminar = { item -> eliminarDelCarrito(item) }
        )

        binding.recyclerCarrito.apply {
            layoutManager = LinearLayoutManager(this@VentasActivity)
            adapter = carritoAdapter
        }
    }

    private fun setupFilters() {
        // Inicializar listas con valores por defecto
        categorias.add("Todas las categorías")
        marcas.add("Todas las marcas")
        familiasOlfativas.add("Todas las familias")

        // Configurar adapters iniciales (vacíos)
        setupSpinnerAdapters()

        // Filtro por nombre
        binding.etFiltroNombre.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrarProductos()
            }
        })

        // Botón limpiar filtros
        binding.btnLimpiarFiltros.setOnClickListener {
            limpiarFiltros()
        }
    }

    private fun setupSpinnerAdapters() {
        // Adapter de categorías
        categoriasAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_dark, categorias) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                view.setBackgroundColor(ContextCompat.getColor(this@VentasActivity, R.color.card_background))
                return view
            }
        }
        categoriasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategoria.adapter = categoriasAdapter
        binding.spinnerCategoria.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filtrarProductos()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Adapter de marcas
        marcasAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_dark, marcas) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                view.setBackgroundColor(ContextCompat.getColor(this@VentasActivity, R.color.card_background))
                return view
            }
        }
        marcasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMarca.adapter = marcasAdapter
        binding.spinnerMarca.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filtrarProductos()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Adapter de familias olfativas
        familiasAdapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_dark, familiasOlfativas) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@VentasActivity, android.R.color.white))
                view.setBackgroundColor(ContextCompat.getColor(this@VentasActivity, R.color.card_background))
                return view
            }
        }
        familiasAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFamiliaOlfativa.adapter = familiasAdapter
        binding.spinnerFamiliaOlfativa.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filtrarProductos()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCarrito() {
        // Botón para mostrar/ocultar carrito
        binding.btnToggleCarrito.setOnClickListener {
            toggleCarritoVisibility()
        }

        // Botón para procesar venta
        binding.btnProcesarVenta.setOnClickListener {
            procesarVenta()
        }

        // Botón para limpiar carrito
        binding.btnLimpiarCarrito.setOnClickListener {
            limpiarCarrito()
        }

        // Configurar RecyclerView del carrito para scroll suave
        binding.recyclerCarrito.apply {
            isNestedScrollingEnabled = true
            // Agregar padding bottom extra para que el último item sea visible cuando aparece el teclado
            setPadding(paddingLeft, paddingTop, paddingRight, 350)
            clipToPadding = false
        }

        // Inicialmente ocultar carrito
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
                val familiasSet = mutableSetOf<String>()

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

                        // Recopilar valores únicos para filtros
                        if (producto.categoriaNombre.isNotEmpty()) {
                            categoriasSet.add(producto.categoriaNombre)
                        }
                        if (producto.marcaNombre.isNotEmpty()) {
                            marcasSet.add(producto.marcaNombre)
                        }
                        producto.familiaOlfativa?.takeIf { it.isNotEmpty() }?.let {
                            familiasSet.add(it)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar producto ${document.id}: ${e.message}")
                    }
                }

                // Actualizar listas de filtros
                updateFilterLists(categoriasSet, marcasSet, familiasSet)

                // Mostrar todos los productos inicialmente
                productosFiltrados.clear()
                productosFiltrados.addAll(todosLosProductos)
                productosAdapter.notifyDataSetChanged()

                Log.d(TAG, "Productos cargados exitosamente")

            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar productos: ${exception.message}")
                Toast.makeText(this, "Error al cargar productos: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateFilterLists(categoriasSet: Set<String>, marcasSet: Set<String>, familiasSet: Set<String>) {
        // Limpiar listas manteniendo el primer elemento (opción "Todas")
        categorias.clear()
        marcas.clear()
        familiasOlfativas.clear()

        // Agregar opciones "Todas"
        categorias.add("Todas las categorías")
        marcas.add("Todas las marcas")
        familiasOlfativas.add("Todas las familias")

        // Agregar valores únicos ordenados
        categorias.addAll(categoriasSet.sorted())
        marcas.addAll(marcasSet.sorted())
        familiasOlfativas.addAll(familiasSet.sorted())

        // Notificar a los adapters de los cambios
        runOnUiThread {
            categoriasAdapter.notifyDataSetChanged()
            marcasAdapter.notifyDataSetChanged()
            familiasAdapter.notifyDataSetChanged()
        }

        Log.d(TAG, "Filtros actualizados - Categorías: ${categorias.size}, Marcas: ${marcas.size}, Familias: ${familiasOlfativas.size}")
    }

    private fun filtrarProductos() {
        val filtroNombre = binding.etFiltroNombre.text.toString().lowercase().trim()
        val categoriaSeleccionada = binding.spinnerCategoria.selectedItem?.toString()
        val marcaSeleccionada = binding.spinnerMarca.selectedItem?.toString()
        val familiaSeleccionada = binding.spinnerFamiliaOlfativa.selectedItem?.toString()

        productosFiltrados.clear()

        for (producto in todosLosProductos) {
            var cumpleFiltros = true

            // Filtro por nombre
            if (filtroNombre.isNotEmpty()) {
                if (!producto.nombre.lowercase().contains(filtroNombre)) {
                    cumpleFiltros = false
                }
            }

            // Filtro por categoría
            if (categoriaSeleccionada != null && categoriaSeleccionada != "Todas las categorías") {
                if (producto.categoriaNombre != categoriaSeleccionada) {
                    cumpleFiltros = false
                }
            }

            // Filtro por marca
            if (marcaSeleccionada != null && marcaSeleccionada != "Todas las marcas") {
                if (producto.marcaNombre != marcaSeleccionada) {
                    cumpleFiltros = false
                }
            }

            // Filtro por familia olfativa
            if (familiaSeleccionada != null && familiaSeleccionada != "Todas las familias") {
                if (producto.familiaOlfativa != familiaSeleccionada) {
                    cumpleFiltros = false
                }
            }

            if (cumpleFiltros) {
                productosFiltrados.add(producto)
            }
        }

        productosAdapter.notifyDataSetChanged()
        Log.d(TAG, "Productos filtrados: ${productosFiltrados.size}")
    }

    private fun limpiarFiltros() {
        binding.etFiltroNombre.setText("")
        binding.spinnerCategoria.setSelection(0)
        binding.spinnerMarca.setSelection(0)
        binding.spinnerFamiliaOlfativa.setSelection(0)
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

        // Verificar si el producto ya está en el carrito
        val itemExistente = carrito.find { it.producto.id == producto.id }

        if (itemExistente != null) {
            // Incrementar cantidad si ya existe
            if (itemExistente.cantidad < producto.stock) {
                itemExistente.cantidad++
                carritoAdapter.notifyDataSetChanged()
                updateCarritoBadge()
                calcularTotal()
                Toast.makeText(this, "Cantidad actualizada en el carrito", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No hay suficiente stock", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Agregar nuevo item al carrito
            val nuevoItem = ItemCarrito(
                producto = producto,
                cantidad = 1,
                precioUnitario = producto.precioVenta
            )
            carrito.add(nuevoItem)
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
            Toast.makeText(this, "No hay suficiente stock. Disponible: ${item.producto.stock}", Toast.LENGTH_SHORT).show()
            // Restaurar el campo de cantidad al valor anterior
            carritoAdapter.notifyDataSetChanged()
        }
    }

    private fun actualizarPrecio(item: ItemCarrito, nuevoPrecio: Double) {
        if (nuevoPrecio > 0) {
            item.precioUnitario = nuevoPrecio
            // Eliminar esta línea: carritoAdapter.notifyDataSetChanged()
            calcularTotal()
            Toast.makeText(this, "Precio actualizado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "El precio debe ser mayor a 0", Toast.LENGTH_SHORT).show()
            // Aquí sí necesitas restaurar el valor visualmente
            carritoAdapter.notifyItemChanged(carrito.indexOf(item))
        }
    }

    private fun eliminarDelCarrito(item: ItemCarrito) {
        carrito.remove(item)
        carritoAdapter.notifyDataSetChanged()
        updateCarritoBadge()
        calcularTotal()
        Toast.makeText(this, "Producto eliminado del carrito", Toast.LENGTH_SHORT).show()
    }

    private fun updateCarritoBadge() {
        val totalItems = carrito.sumOf { it.cantidad }
        binding.tvCarritoBadge.text = totalItems.toString()
        binding.tvCarritoBadge.visibility = if (totalItems > 0) View.VISIBLE else View.GONE

        // Mostrar/ocultar botones del carrito según si hay items
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
            .setMessage("¿Estás seguro de que deseas eliminar todos los productos del carrito?")
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
        // Validar stock antes de procesar
        if (validarStockAntesDeProcesar()) {
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

        // Configurar total
        val total = carrito.sumOf { it.cantidad * it.precioUnitario }
        tvTotal.text = "Total: S/. %.2f".format(total)

        // Configurar spinner de tipo de comprobante
        val tiposComprobante = arrayOf("Nota de Venta", "Nota de Crédito", "Boleta", "Factura")
        val tipoComprobanteAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tiposComprobante)
        tipoComprobanteAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTipoComprobante.adapter = tipoComprobanteAdapter

        // Cargar medios de pago desde Firestore
        cargarMediosPago(spinnerMedioPago)

        // Seleccionar el texto del nombre del cliente para facilitar la edición
        etNombreCliente.selectAll()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Procesar Venta")
            .setView(dialogView)
            .setPositiveButton("Confirmar") { _, _ ->
                val nombreCliente = etNombreCliente.text.toString().trim().ifEmpty { "Usuario Genérico" }
                val tipoComprobanteSeleccionado = spinnerTipoComprobante.selectedItem.toString()
                val medioPagoSeleccionado = spinnerMedioPago.selectedItem.toString()
                val esDelivery = switchDelivery.isChecked
                val observaciones = etObservaciones.text.toString().ifEmpty { "Ninguna" }
                val esVentaParcial = tipoComprobanteSeleccionado == "Nota de Crédito"

                procesarVentaFinal(nombreCliente, tipoComprobanteSeleccionado, medioPagoSeleccionado, esDelivery, observaciones, esVentaParcial)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        // Opcional: Enfocar el campo del nombre del cliente
        etNombreCliente.requestFocus()
    }

    private fun cargarMediosPago(spinner: Spinner) {
        firestore.collection("mediosPago")
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->
                val mediosPago = mutableListOf<String>()

                for (document in documents) {
                    val nombre = document.getString("nombre") ?: ""
                    if (nombre.isNotEmpty()) {
                        mediosPago.add(nombre)
                    }
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mediosPago)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar medios de pago: ${exception.message}")
                // Usar medios de pago por defecto
                val mediosPagoDefault = arrayOf("Efectivo", "Yape", "Plin", "Transferencia", "Tarjeta")
                val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mediosPagoDefault)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
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
        // Obtener el siguiente número de factura
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

        // Calcular IGV y subtotal
        val igv = total * 0.18
        val subtotal = total - igv

        // Crear detalles de la venta
        val detalles = mutableMapOf<String, Any>()
        carrito.forEachIndexed { index, item ->
            val detalleId = "detalle_${index + 1}"
            detalles[detalleId] = mapOf(
                "productoNombre" to item.producto.nombre,
                "cantidad" to item.cantidad,
                "precioUnitario" to item.precioUnitario,
                "subtotal" to (item.cantidad * item.precioUnitario)
            )
        }

        // Crear documento de venta
        val venta = mapOf(
            "clienteNombre" to nombreCliente, // Usar el nombre ingresado por el usuario
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

        // Guardar en Firestore
        firestore.collection("ventas")
            .add(venta)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "Venta guardada con ID: ${documentReference.id}")

                Toast.makeText(this, "Venta procesada exitosamente\nCliente: $nombreCliente\nNúmero: $numeroFactura", Toast.LENGTH_LONG).show()

                // Actualizar stock de productos
                actualizarStockProductos()

                // Limpiar el carrito después de procesar la venta
                carrito.clear()
                carritoAdapter.notifyDataSetChanged()
                updateCarritoBadge()
                calcularTotal()

                // Ocultar el carrito
                binding.layoutCarrito.visibility = View.GONE
                binding.btnToggleCarrito.setImageResource(R.drawable.ic_shopping_cart)

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar venta: ${e.message}")
                Toast.makeText(this, "Error al procesar la venta: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun actualizarStockProductos() {
        val batch = firestore.batch()
        var operacionesCompletadas = 0
        val totalOperaciones = carrito.size

        carrito.forEach { item ->
            val productoRef = firestore.collection("productos").document(item.producto.id)

            // Obtener el documento actual para verificar el stock
            productoRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val stockActual = document.getLong("stock")?.toInt() ?: 0
                        val nuevoStock = stockActual - item.cantidad

                        // Verificar que el stock no sea negativo
                        // Mostrar advertencia pero continuar con la venta
                        if (nuevoStock <= 0) {
                            Toast.makeText(
                                this,
                                "Advertencia: Stock insuficiente para ${item.producto.nombre} Regularice en inventario.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        // Actualizar el stock usando batch para mayor eficiencia
                        batch.update(productoRef, "stock", nuevoStock)

                        Log.d(TAG, "Producto ${item.producto.nombre}: Stock actual: $stockActual, Vendido: ${item.cantidad}, Nuevo stock: $nuevoStock")

                        operacionesCompletadas++

                        // Si es la última operación, ejecutar el batch
                        if (operacionesCompletadas == totalOperaciones) {
                            ejecutarBatchActualizacion(batch)
                        }
                    } else {
                        Log.e(TAG, "Error: Producto ${item.producto.id} no encontrado")
                        operacionesCompletadas++

                        if (operacionesCompletadas == totalOperaciones) {
                            ejecutarBatchActualizacion(batch)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error al obtener producto ${item.producto.id}: ${exception.message}")
                    operacionesCompletadas++

                    if (operacionesCompletadas == totalOperaciones) {
                        ejecutarBatchActualizacion(batch)
                    }
                }
        }
    }

    private fun ejecutarBatchActualizacion(batch: com.google.firebase.firestore.WriteBatch) {
        batch.commit()
            .addOnSuccessListener {
                Log.d(TAG, "Stock de productos actualizado exitosamente")
                Toast.makeText(this, "Stock actualizado correctamente", Toast.LENGTH_SHORT).show()

                // Recargar los productos para mostrar el stock actualizado
                loadProductos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al actualizar stock de productos: ${exception.message}")
                Toast.makeText(this, "Error al actualizar stock: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Función auxiliar para validar stock antes de procesar la venta
    private fun validarStockAntesDeProcesar(): Boolean {
        var stockSuficiente = true
        val productosConStockInsuficiente = mutableListOf<String>()

        carrito.forEach { item ->
            if (item.cantidad > item.producto.stock) {
                stockSuficiente = false
                productosConStockInsuficiente.add("${item.producto.nombre} (Disponible: ${item.producto.stock}, Solicitado: ${item.cantidad})")
            }
        }

        if (!stockSuficiente) {
            val mensaje = "Stock insuficiente para los siguientes productos:\n\n" +
                    productosConStockInsuficiente.joinToString("\n") +
                    "\n\n¿Desea continuar con la venta de todas formas?"

            AlertDialog.Builder(this)
                .setTitle("Stock Insuficiente")
                .setMessage(mensaje)
                .setPositiveButton("Continuar") { _, _ ->
                    // Continuar con la venta aunque haya stock insuficiente
                    mostrarDialogoVenta()
                }
                .setNegativeButton("Cancelar") { _, _ ->
                    // No hacer nada, mantener el carrito
                }
                .create()
                .show()

            return false
        }

        return true
    }
}