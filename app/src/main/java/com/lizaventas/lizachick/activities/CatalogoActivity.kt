package com.lizaventas.lizachick.activities

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.CatalogosAdapter
import com.lizaventas.lizachick.databinding.ActivityCatalogoBinding
import com.lizaventas.lizachick.models.Catalogo
import java.text.SimpleDateFormat
import java.util.*

class CatalogoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CatalogoActivity"
    }

    // UI Components
    private lateinit var binding: ActivityCatalogoBinding

    // Firebase
    private lateinit var firestore: FirebaseFirestore

    // Adapter
    private lateinit var catalogosAdapter: CatalogosAdapter

    // Data
    private val catalogosList = mutableListOf<Catalogo>()

    // Date Formatters
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    // Flag para controlar la carga de datos
    private var isDataLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCatalogoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        loadCatalogos()
    }

    // ================== INITIALIZATION METHODS ==================

    private fun initializeComponents() {
        setupToolbar()
        setupDarkTheme()
        setupRecyclerView()
        setupFirestore()
        setupFab()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Catálogos"
        }
    }

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun setupRecyclerView() {
        catalogosAdapter = CatalogosAdapter(
            catalogosList,
            onEditClick = { catalogo -> showEditDialog(catalogo) },
            onViewPdfClick = { catalogo -> openPdf(catalogo.rutaUrl) },
            onDeleteClick = { catalogo -> showDeleteConfirmation(catalogo) }
        )

        binding.recyclerViewCatalogos.apply {
            layoutManager = LinearLayoutManager(this@CatalogoActivity)
            adapter = catalogosAdapter
        }
    }

    private fun setupFirestore() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupFab() {
        binding.fabAddCatalogo.setOnClickListener {
            showAddDialog()
        }
    }

    // ================== DATA LOADING ==================

    private fun loadCatalogos() {
        binding.progressBar.visibility = View.VISIBLE
        catalogosList.clear() // Limpiar la lista antes de cargar

        firestore.collection("catalogos")
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Catálogos encontrados: ${documents.size()}")

                for (document in documents) {
                    try {
                        val catalogo = Catalogo(
                            id = document.id,
                            nombre = document.id, // El ID del documento es el nombre
                            estado = document.getBoolean("estado") ?: true,
                            vigencia = document.getTimestamp("vigencia"),
                            rutaUrl = document.getString("rutaurl") ?: ""
                        )
                        catalogosList.add(catalogo)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar catálogo ${document.id}: ${e.message}")
                    }
                }

                catalogosList.sortBy { it.nombre }
                catalogosAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
                isDataLoaded = true

                // Mostrar mensaje si no hay catálogos
                if (catalogosList.isEmpty()) {
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvEmptyState.visibility = View.GONE
                }

            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading catálogos", exception)
                Toast.makeText(this, "Error al cargar catálogos: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ================== DIALOG METHODS ==================

    private fun showAddDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_catalogo, null)
        val etNombre = dialogView.findViewById<android.widget.EditText>(R.id.etNombre)
        val etRutaUrl = dialogView.findViewById<android.widget.EditText>(R.id.etRutaUrl)
        val btnSelectDate = dialogView.findViewById<android.widget.Button>(R.id.btnSelectDate)
        val tvSelectedDate = dialogView.findViewById<android.widget.TextView>(R.id.tvSelectedDate)

        var selectedDate = Calendar.getInstance()

        btnSelectDate.setOnClickListener {
            showDateTimePicker { calendar ->
                selectedDate = calendar
                tvSelectedDate.text = "Vigencia: ${dateTimeFormat.format(selectedDate.time)}"
                tvSelectedDate.visibility = View.VISIBLE
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Agregar Catálogo")
            .setView(dialogView)
            .setPositiveButton("Agregar") { _, _ ->
                val nombre = etNombre.text.toString().trim()
                val rutaUrl = etRutaUrl.text.toString().trim()

                if (nombre.isNotEmpty() && rutaUrl.isNotEmpty()) {
                    addCatalogo(nombre, rutaUrl, selectedDate.time)
                } else {
                    Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditDialog(catalogo: Catalogo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_catalogo, null)
        val etNombre = dialogView.findViewById<android.widget.EditText>(R.id.etNombre)
        val etRutaUrl = dialogView.findViewById<android.widget.EditText>(R.id.etRutaUrl)
        val btnSelectDate = dialogView.findViewById<android.widget.Button>(R.id.btnSelectDate)
        val tvSelectedDate = dialogView.findViewById<android.widget.TextView>(R.id.tvSelectedDate)
        val switchEstado = dialogView.findViewById<android.widget.Switch>(R.id.switchEstado)

        // Prellenar datos
        etNombre.setText(catalogo.nombre)
        etRutaUrl.setText(catalogo.rutaUrl)
        switchEstado.isChecked = catalogo.estado

        val selectedDate = Calendar.getInstance()
        catalogo.vigencia?.let { ts ->
            val date = ts.toDate()
            selectedDate.time = date
            tvSelectedDate.text = "Vigencia: ${dateTimeFormat.format(date)}"
            tvSelectedDate.visibility = View.VISIBLE
        }

        // Deshabilitar edición del nombre (es el ID del documento)
        etNombre.isEnabled = false

        btnSelectDate.setOnClickListener {
            showDateTimePicker { calendar ->
                selectedDate.timeInMillis = calendar.timeInMillis
                tvSelectedDate.text = "Vigencia: ${dateTimeFormat.format(selectedDate.time)}"
            }
        }

        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Editar Catálogo")
            .setView(dialogView)
            .setPositiveButton("Actualizar") { _, _ ->
                val rutaUrl = etRutaUrl.text.toString().trim()
                val estado = switchEstado.isChecked

                if (rutaUrl.isNotEmpty()) {
                    updateCatalogo(catalogo.id, rutaUrl, selectedDate.time, estado)
                } else {
                    Toast.makeText(this, "La URL no puede estar vacía", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("Ver PDF") { _, _ ->
                openPdf(catalogo.rutaUrl)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDateTimePicker(onDateTimeSelected: (Calendar) -> Unit) {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            R.style.DatePickerDialogDark,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                TimePickerDialog(
                    this,
                    R.style.TimePickerDialogDark,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        onDateTimeSelected(calendar)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showDeleteConfirmation(catalogo: Catalogo) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Eliminar Catálogo")
            .setMessage("¿Está seguro de que desea eliminar el catálogo '${catalogo.nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteCatalogo(catalogo.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ================== FIRESTORE OPERATIONS ==================

    private fun addCatalogo(nombre: String, rutaUrl: String, vigencia: Date) {
        val catalogoData = hashMapOf(
            "estado" to true,
            "vigencia" to Timestamp(vigencia),
            "rutaurl" to rutaUrl
        )

        firestore.collection("catalogos")
            .document(nombre)
            .set(catalogoData)
            .addOnSuccessListener {
                Toast.makeText(this, "Catálogo agregado exitosamente", Toast.LENGTH_SHORT).show()
                loadCatalogos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error adding catalogo", exception)
                Toast.makeText(this, "Error al agregar catálogo: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateCatalogo(catalogoId: String, rutaUrl: String, vigencia: Date, estado: Boolean) {
        val updates = hashMapOf<String, Any>(
            "rutaurl" to rutaUrl,
            "vigencia" to Timestamp(vigencia),
            "estado" to estado
        )

        firestore.collection("catalogos")
            .document(catalogoId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Catálogo actualizado exitosamente", Toast.LENGTH_SHORT).show()
                loadCatalogos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error updating catalogo", exception)
                Toast.makeText(this, "Error al actualizar catálogo: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteCatalogo(catalogoId: String) {
        firestore.collection("catalogos")
            .document(catalogoId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Catálogo eliminado exitosamente", Toast.LENGTH_SHORT).show()
                loadCatalogos()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error deleting catalogo", exception)
                Toast.makeText(this, "Error al eliminar catálogo: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ================== PDF VIEWER ==================

    private fun openPdf(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "URL del PDF no disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Forzar apertura en navegador web
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                // Estas banderas fuerzan que se abra en el navegador
                addCategory(Intent.CATEGORY_BROWSABLE)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Especificar explícitamente que queremos un navegador
                setPackage(getDefaultBrowserPackage())
            }

            startActivity(browserIntent)

        } catch (e: Exception) {
            // Si falla, intentar sin especificar el paquete del navegador
            try {
                Log.w(TAG, "Error con navegador específico, intentando con navegador por defecto", e)
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(fallbackIntent)

            } catch (ex: Exception) {
                Log.e(TAG, "Error opening PDF in browser", ex)
                Toast.makeText(this, "Error al abrir el PDF en el navegador: ${ex.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getDefaultBrowserPackage(): String? {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://"))
        val resolveInfo = packageManager.resolveActivity(browserIntent, 0)
        return resolveInfo?.activityInfo?.packageName
    }

    // ================== MENU HANDLING ==================

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ================== LIFECYCLE ==================

    override fun onResume() {
        super.onResume()
        // Solo recargar si ya se habían cargado datos anteriormente
        // y no estamos en la primera carga
        if (isDataLoaded) {
            loadCatalogos()
        }
    }
}