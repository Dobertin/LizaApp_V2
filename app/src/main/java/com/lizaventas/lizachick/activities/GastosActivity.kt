package com.lizaventas.lizachick.activities

import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.GastosAdapter
import com.lizaventas.lizachick.databinding.ActivityGastosBinding
import com.lizaventas.lizachick.databinding.DialogGastoBinding
import com.lizaventas.lizachick.models.Gasto
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GastosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGastosBinding
    private lateinit var gastosAdapter: GastosAdapter
    private val db = FirebaseFirestore.getInstance()
    private val gastosList = mutableListOf<Gasto>()

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityGastosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFAB()
        loadGastos()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.gastos_title)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupRecyclerView() {
        gastosAdapter = GastosAdapter(
            gastos = gastosList,
            onEditClick = { gasto -> showGastoDialog(gasto) },
            onDeleteClick = { gasto -> showDeleteConfirmation(gasto) },
            onToggleEstado = { gasto -> toggleEstadoGasto(gasto) }
        )

        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@GastosActivity)
            adapter = gastosAdapter
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupFAB() {
        binding.fabAddGasto.setOnClickListener {
            showGastoDialog()
        }
    }

    private fun loadGastos() {
        binding.progressBar.visibility = View.VISIBLE

        db.collection("gastos")
            .whereEqualTo("estado", true)
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                gastosList.clear()
                for (document in documents) {
                    val gasto = document.toObject(Gasto::class.java)
                    gasto.id = document.id
                    gastosList.add(gasto)
                }
                gastosAdapter.notifyDataSetChanged()
                updateEmptyState()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error al cargar gastos: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showGastoDialog(gasto: Gasto? = null) {
        val dialogBinding = DialogGastoBinding.inflate(layoutInflater)
        val isEditing = gasto != null

        // Configurar campos si es edición
        gasto?.let {
            dialogBinding.etMotivo.setText(it.motivo)
            dialogBinding.etMonto.setText(it.monto.toString())
            dialogBinding.switchEstado.isChecked = it.estadopago

            // Convertir Timestamp a fecha
            val date = it.fecha?.toDate() ?: Date()
            val formattedDate = dateFormatter.format(date)
            dialogBinding.etFecha.setText(formattedDate)
        }

        // Si es nuevo gasto, establecer fecha actual
        if (!isEditing) {
            val currentDate = Date()
            val formattedCurrentDate = dateFormatter.format(currentDate)
            dialogBinding.etFecha.setText(formattedCurrentDate)
        }

        // Configurar selector de fecha
        dialogBinding.etFecha.setOnClickListener {
            showDatePicker { selectedDate ->
                dialogBinding.etFecha.setText(selectedDate)
            }
        }

        val dialog = AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle(if (isEditing) "Editar Gasto" else "Nuevo Gasto")
            .setView(dialogBinding.root)
            .setPositiveButton(if (isEditing) "Actualizar" else "Guardar") { _, _ ->
                saveGasto(dialogBinding, gasto)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (String) -> Unit) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Seleccionar fecha")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            // Crear el formatter con zona horaria UTC para evitar desfases
            val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = Date(selection)
            onDateSelected(formatter.format(date))
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveGasto(dialogBinding: DialogGastoBinding, existingGasto: Gasto?) {
        val motivo = dialogBinding.etMotivo.text.toString().trim()
        val montoText = dialogBinding.etMonto.text.toString().trim()
        val fechaText = dialogBinding.etFecha.text.toString().trim()
        val estado = true
        val estadopago = dialogBinding.switchEstado.isChecked

        // Validaciones
        if (motivo.isEmpty()) {
            Toast.makeText(this, "El motivo es obligatorio", Toast.LENGTH_SHORT).show()
            return
        }

        val monto = montoText.toDoubleOrNull()
        if (monto == null || monto <= 0) {
            Toast.makeText(this, "Ingrese un monto válido", Toast.LENGTH_SHORT).show()
            return
        }

        if (fechaText.isEmpty()) {
            Toast.makeText(this, "Seleccione una fecha", Toast.LENGTH_SHORT).show()
            return
        }

        // Convertir fecha a Timestamp
        val fechaTimestamp = try {
            val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val date = inputFormat.parse(fechaText)
            Timestamp(date!!) // Crear Timestamp desde Date
        } catch (e: Exception) {
            Toast.makeText(this, "Formato de fecha inválido", Toast.LENGTH_SHORT).show()
            return
        }


        val gastoData = hashMapOf(
            "motivo" to motivo,
            "monto" to monto,
            "fecha" to fechaTimestamp, // Ahora es Timestamp
            "estadopago" to estadopago,
            "estado" to estado
        )

        if (existingGasto != null) {
            // Actualizar gasto existente
            db.collection("gastos")
                .document(existingGasto.id)
                .update(gastoData as Map<String, Any>)
                .addOnSuccessListener {
                    Toast.makeText(this, "Gasto actualizado correctamente", Toast.LENGTH_SHORT).show()
                    loadGastos()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error al actualizar: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                }
        } else {
            // Crear nuevo gasto
            db.collection("gastos")
                .add(gastoData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Gasto guardado correctamente", Toast.LENGTH_SHORT).show()
                    loadGastos()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error al guardar: ${exception.message}",
                        Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun showDeleteConfirmation(gasto: Gasto) {
        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Eliminar Gasto")
            .setMessage("¿Está seguro de eliminar el gasto '${gasto.motivo}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteGasto(gasto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteGasto(gasto: Gasto) {
        db.collection("gastos")
            .document(gasto.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Gasto eliminado correctamente", Toast.LENGTH_SHORT).show()
                loadGastos()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al eliminar: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleEstadoGasto(gasto: Gasto) {
        val newEstado = !gasto.estado

        db.collection("gastos")
            .document(gasto.id)
            .update("estadopago", newEstado)
            .addOnSuccessListener {
                val message = if (newEstado) "Gasto marcado como pagado" else "Gasto marcado como pendiente"
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                loadGastos()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al actualizar estado: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEmptyState() {
        if (gastosList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvGastos.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.rvGastos.visibility = View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.gastos_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressed()
            true
        }
        R.id.menu_refresh -> {
            loadGastos()
            true
        }
        R.id.menu_filter -> {
            showFilterDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFilterDialog() {
        val options = arrayOf("Todos", "Pagados", "Pendientes")
        var selectedOption = 0

        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Filtrar gastos")
            .setSingleChoiceItems(options, selectedOption) { _, which ->
                selectedOption = which
            }
            .setPositiveButton("Aplicar") { _, _ ->
                applyFilter(selectedOption)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun applyFilter(filterType: Int) {
        binding.progressBar.visibility = View.VISIBLE

        val query = when (filterType) {
            1 -> db.collection("gastos").whereEqualTo("estadopago", true) // Pagados
            2 -> db.collection("gastos").whereEqualTo("estadopago", false) // Pendientes
            else -> db.collection("gastos") // Todos
        }

        query.orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                gastosList.clear()
                for (document in documents) {
                    val gasto = document.toObject(Gasto::class.java)
                    gasto.id = document.id
                    gastosList.add(gasto)
                }
                gastosAdapter.notifyDataSetChanged()
                updateEmptyState()
                binding.progressBar.visibility = View.GONE
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Error al filtrar: ${exception.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }
}