package com.lizaventas.lizachick.activities

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.ProductosCRUDAdapter
import com.lizaventas.lizachick.databinding.ActivityProductosBinding
import com.lizaventas.lizachick.databinding.DialogProductoBinding
import com.lizaventas.lizachick.models.Categoria
import com.lizaventas.lizachick.models.Marca
import com.lizaventas.lizachick.models.ProductoCRUD

class ProductosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductosBinding
    private lateinit var productosAdapter: ProductosCRUDAdapter
    private val db = FirebaseFirestore.getInstance()
    private val productos = mutableListOf<ProductoCRUD>()
    private val productosFiltrados = mutableListOf<ProductoCRUD>()
    private val categorias = mutableListOf<Categoria>()
    private val marcas = mutableListOf<Marca>()

    private var imagenSeleccionada: Uri? = null
    private val PICK_IMAGE_REQUEST = 1001

    companion object {
        private const val TAG = "ProductosActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        setupSearchView()
        loadData()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Gestión de Productos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)

        binding.fabAddProduct.setOnClickListener {
            mostrarDialogoProducto(null)
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadData()
        }
    }

    private fun setupRecyclerView() {
        productosAdapter = ProductosCRUDAdapter(
            productos = productosFiltrados,
            onEditClick = { producto -> mostrarDialogoProducto(producto) },
            onDeleteClick = { producto -> confirmarEliminarProducto(producto) },
            onToggleEstado = { producto -> toggleEstadoProducto(producto) }
        )

        binding.recyclerViewProductos.apply {
            layoutManager = LinearLayoutManager(this@ProductosActivity)
            adapter = productosAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filtrarProductos(newText ?: "")
                return true
            }
        })
    }

    private fun loadData() {
        binding.swipeRefresh.isRefreshing = true

        loadCategorias()
        loadMarcas()
        loadProductos()
    }

    private fun loadCategorias() {
        db.collection("categorias")
            .whereEqualTo("estado", true)
            .orderBy("nombre")
            .get()
            .addOnSuccessListener { documents ->
                categorias.clear()

                try {
                    for (document in documents) {
                        val categoria = document.toObject(Categoria::class.java)
                        categoria.id = document.id
                        categorias.add(categoria)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar categorías: ${e.message}", e)
                    Toast.makeText(this, "Error al procesar categorías: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar categorías", exception)
                Toast.makeText(this, "Error al cargar categorías: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadMarcas() {
        db.collection("marcas")
            .whereEqualTo("estado", true)
            .orderBy("nombre")
            .get()
            .addOnSuccessListener { documents ->
                marcas.clear()

                try {
                    for (document in documents) {
                        val marca = document.toObject(Marca::class.java)
                        marca.id = document.id
                        marcas.add(marca)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar marcas: ${e.message}", e)
                    Toast.makeText(this, "Error al procesar marcas: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar marcas", exception)
                Toast.makeText(this, "Error al cargar marcas: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadProductos() {
        db.collection("productos")
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                productos.clear()

                try {
                    for (document in documents) {
                        val producto = document.toObject(ProductoCRUD::class.java)
                        producto.id = document.id
                        productos.add(producto)
                    }

                    productosFiltrados.clear()
                    productosFiltrados.addAll(productos)
                    productosAdapter.notifyDataSetChanged()

                    actualizarContadores()

                } catch (e: Exception) {
                    Log.e(TAG, "Error al procesar productos: ${e.message}", e)
                    Toast.makeText(this, "Error al procesar productos: ${e.message}", Toast.LENGTH_LONG).show()
                }

                binding.swipeRefresh.isRefreshing = false
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error al cargar productos", exception)
                Toast.makeText(this, "Error al cargar productos: ${exception.message}", Toast.LENGTH_LONG).show()
                binding.swipeRefresh.isRefreshing = false
            }
    }

    private fun filtrarProductos(query: String) {
        productosFiltrados.clear()

        if (query.isEmpty()) {
            productosFiltrados.addAll(productos)
        } else {
            productos.forEach { producto ->
                if (producto.nombre.contains(query, ignoreCase = true) ||
                    producto.categoriaNombre.contains(query, ignoreCase = true) ||
                    producto.marcaNombre.contains(query, ignoreCase = true)) {
                    productosFiltrados.add(producto)
                }
            }
        }

        productosAdapter.notifyDataSetChanged()
        actualizarContadores()
    }

    private fun actualizarContadores() {
        val total = productosFiltrados.size
        val activos = productosFiltrados.count { it.estado }
        val inactivos = total - activos
        val sinStock = productosFiltrados.count { it.stock <= 0 }

        binding.tvTotalProductos.text = "Total: $total"
        binding.tvProductosActivos.text = "Activos: $activos"
        binding.tvProductosInactivos.text = "Inactivos: $inactivos"
        binding.tvProductosSinStock.text = "Sin Stock: $sinStock"
    }

    private fun mostrarDialogoProducto(producto: ProductoCRUD?) {
        if (categorias.isEmpty() || marcas.isEmpty()) {
            Toast.makeText(this, "Cargando datos, intenta nuevamente", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogBinding = DialogProductoBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setView(dialogBinding.root)
            .create()

        // Configurar spinners
        setupSpinners(dialogBinding)

        // Si es edición, llenar los campos
        producto?.let { p ->
            dialogBinding.apply {
                etNombre.setText(p.nombre)
                etDescripcion.setText(p.descripcion)
                etCapacidad.setText(p.capacidad)
                etPrecioCatalogo.setText(p.precioCatalogo.toString())
                etPrecioVenta.setText(p.precioVenta.toString())
                etStock.setText(p.stock.toString())

                // Seleccionar categoría
                val categoriaIndex = categorias.indexOfFirst { it.nombre == p.categoriaNombre }
                if (categoriaIndex >= 0) {
                    spinnerCategoria.setSelection(categoriaIndex)
                }

                // Seleccionar marca
                val marcaIndex = marcas.indexOfFirst { it.nombre == p.marcaNombre }
                if (marcaIndex >= 0) {
                    spinnerMarca.setSelection(marcaIndex)
                }

                // Seleccionar género - manejar valores nulos
                val generos = arrayOf("Sin Genero","Mujer", "Varon", "Unisex","Bebes","Kids","Niñas","Niños")
                val generoParaBuscar = if (p.genero.isNullOrEmpty()) "Sin Genero" else p.genero
                val generoIndex = generos.indexOf(generoParaBuscar)
                if (generoIndex >= 0) {
                    spinnerGenero.setSelection(generoIndex)
                } else {
                    // Si no encuentra el género, seleccionar "Sin Genero"
                    spinnerGenero.setSelection(0)
                }

                // Llenar familia olfativa como texto
                etFamiliaOlfativa.setText(p.familiaOlfativa ?: "")

                // Cargar imagen si existe
                if (p.imagenUrl.isNotEmpty()) {
                    Glide.with(this@ProductosActivity)
                        .load(p.imagenUrl)
                        .placeholder(R.drawable.ic_producto_placeholder)
                        .error(R.drawable.ic_producto_placeholder)
                        .into(ivProductoImagen)
                }

                switchEstado.isChecked = p.estado
            }
        }

        dialogBinding.btnSeleccionarImagen.setOnClickListener {
            seleccionarImagen()
        }

        dialogBinding.btnGuardar.setOnClickListener {
            guardarProducto(dialogBinding, producto, dialog)
        }

        dialogBinding.btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setupSpinners(dialogBinding: DialogProductoBinding) {
        try {
            // Spinner de categorías
            if (categorias.isNotEmpty()) {
                val categoriasNames = categorias.map { it.nombre }
                val categoriaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categoriasNames)
                categoriaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.spinnerCategoria.adapter = categoriaAdapter
            }

            // Spinner de marcas
            if (marcas.isNotEmpty()) {
                val marcasNames = marcas.map { it.nombre }
                val marcaAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, marcasNames)
                marcaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.spinnerMarca.adapter = marcaAdapter
            }

            // Spinner de género
            val generos = arrayOf("Sin Genero","Mujer", "Varon", "Unisex","Bebes","Kids","Niñas","Niños")
            val generoAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, generos)
            generoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogBinding.spinnerGenero.adapter = generoAdapter

        } catch (e: Exception) {
            Log.e(TAG, "Error configurando spinners: ${e.message}", e)
            Toast.makeText(this, "Error configurando formulario", Toast.LENGTH_SHORT).show()
        }
    }

    private fun seleccionarImagen() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            imagenSeleccionada = data.data
        }
    }

    private fun guardarProducto(dialogBinding: DialogProductoBinding, productoExistente: ProductoCRUD?, dialog: AlertDialog) {
        // Validar campos
        if (!validarCampos(dialogBinding)) return

        try {
            val producto = ProductoCRUD().apply {
                id = productoExistente?.id ?: ""
                nombre = dialogBinding.etNombre.text.toString().trim()
                categoriaNombre = if (categorias.isNotEmpty()) categorias[dialogBinding.spinnerCategoria.selectedItemPosition].nombre else ""
                marcaNombre = if (marcas.isNotEmpty()) marcas[dialogBinding.spinnerMarca.selectedItemPosition].nombre else ""
                descripcion = dialogBinding.etDescripcion.text.toString().trim()
                genero = dialogBinding.spinnerGenero.selectedItem.toString()
                familiaOlfativa = dialogBinding.etFamiliaOlfativa.text.toString().trim()
                capacidad = dialogBinding.etCapacidad.text.toString().trim()
                precioCatalogo = dialogBinding.etPrecioCatalogo.text.toString().toDoubleOrNull() ?: 0.0
                precioVenta = dialogBinding.etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
                stock = dialogBinding.etStock.text.toString().toIntOrNull() ?: 0
                imagenUrl = productoExistente?.imagenUrl ?: ""
                estado = dialogBinding.switchEstado.isChecked
                fechaCreacion = productoExistente?.fechaCreacion ?: com.google.firebase.Timestamp.now()
            }

            // Si hay imagen seleccionada, subirla primero
            //if (imagenSeleccionada != null) {
            //    subirImagen(producto, dialog)
            //} else {
            guardarEnFirestore(producto, dialog)
            //}

        } catch (e: Exception) {
            Log.e(TAG, "Error creando producto: ${e.message}", e)
            Toast.makeText(this, "Error al procesar datos del producto", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validarCampos(dialogBinding: DialogProductoBinding): Boolean {
        with(dialogBinding) {
            when {
                etNombre.text.toString().trim().isEmpty() -> {
                    etNombre.error = "Ingrese el nombre del producto"
                    return false
                }
                etCapacidad.text.toString().trim().isEmpty() -> {
                    etCapacidad.error = "Ingrese la capacidad"
                    return false
                }
                etPrecioCatalogo.text.toString().trim().isEmpty() -> {
                    etPrecioCatalogo.error = "Ingrese el precio de catálogo"
                    return false
                }
                etPrecioVenta.text.toString().trim().isEmpty() -> {
                    etPrecioVenta.error = "Ingrese el precio de venta"
                    return false
                }
                etStock.text.toString().trim().isEmpty() -> {
                    etStock.error = "Ingrese el stock"
                    return false
                }
                categorias.isEmpty() -> {
                    Toast.makeText(this@ProductosActivity, "No hay categorías disponibles", Toast.LENGTH_SHORT).show()
                    return false
                }
                marcas.isEmpty() -> {
                    Toast.makeText(this@ProductosActivity, "No hay marcas disponibles", Toast.LENGTH_SHORT).show()
                    return false
                }
                else -> return true
            }
        }
    }

    private fun guardarEnFirestore(producto: ProductoCRUD, dialog: AlertDialog) {
        val collection = db.collection("productos")

        if (producto.id.isEmpty()) {
            // Crear nuevo producto
            collection.add(producto)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "Producto creado con ID: ${documentReference.id}")
                    producto.id = documentReference.id
                    productos.add(0, producto)
                    filtrarProductos(binding.searchView.query.toString())
                    Toast.makeText(this, "Producto creado exitosamente", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error creando producto", exception)
                    Toast.makeText(this, "Error al crear el producto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Actualizar producto existente
            collection.document(producto.id).set(producto)
                .addOnSuccessListener {
                    Log.d(TAG, "Producto actualizado: ${producto.id}")
                    val index = productos.indexOfFirst { it.id == producto.id }
                    if (index != -1) {
                        productos[index] = producto
                        filtrarProductos(binding.searchView.query.toString())
                    }
                    Toast.makeText(this, "Producto actualizado exitosamente", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error actualizando producto", exception)
                    Toast.makeText(this, "Error al actualizar el producto: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun confirmarEliminarProducto(producto: ProductoCRUD) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Eliminar Producto")
            .setMessage("¿Está seguro de que desea eliminar '${producto.nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarProducto(producto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarProducto(producto: ProductoCRUD) {
        db.collection("productos").document(producto.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Producto eliminado: ${producto.id}")
                productos.removeIf { it.id == producto.id }
                filtrarProductos(binding.searchView.query.toString())
                Toast.makeText(this, "Producto eliminado", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error eliminando producto", exception)
                Toast.makeText(this, "Error al eliminar el producto: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleEstadoProducto(producto: ProductoCRUD) {
        val nuevoEstado = !producto.estado

        /* //Comentado para evitar error de cambios de estado sin uso
        db.collection("productos").document(producto.id)
            .update("estado", nuevoEstado)
            .addOnSuccessListener {
                Log.d(TAG, "Estado cambiado para producto: ${producto.id}")
                producto.estado = nuevoEstado
                val index = productos.indexOfFirst { it.id == producto.id }
                if (index != -1) {
                    productos[index] = producto
                }
                productosAdapter.notifyDataSetChanged()
                actualizarContadores()

                val mensaje = if (nuevoEstado) "Producto activado" else "Producto desactivado"
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error cambiando estado", exception)
                Toast.makeText(this, "Error al cambiar el estado: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
         */
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.productos_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.menu_export -> {
                exportarProductos()
                true
            }
            R.id.menu_filter -> {
                mostrarFiltros()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportarProductos() {
        Toast.makeText(this, "Función de exportación en desarrollo", Toast.LENGTH_SHORT).show()
    }

    private fun mostrarFiltros() {
        Toast.makeText(this, "Filtros avanzados en desarrollo", Toast.LENGTH_SHORT).show()
    }
}