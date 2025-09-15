package com.lizaventas.lizachick.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.VentasAdapter
import com.lizaventas.lizachick.databinding.ActivityListaVentasBinding
import com.lizaventas.lizachick.models.Venta
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ListaVentasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListaVentasBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var ventasAdapter: VentasAdapter
    private val ventasList = mutableListOf<Venta>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Permisos necesarios para generar PDF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaVentasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        initFirestore()
        setupRecyclerView()
        setupDatePickers()
        setupSearchButton()
        loadAllVentas()
    }

    private fun setupUI() {
        // Configurar ActionBar
        supportActionBar?.title = "Lista de Ventas"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Establecer fecha actual en los campos
        val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        binding.etFechaInicio.setText(currentDate)
        binding.etFechaFin.setText(currentDate)
    }

    private fun initFirestore() {
        firestore = FirebaseFirestore.getInstance()
        // Configurar para el proyecto lizaventas-267bb
        // La configuración del proyecto se hace automáticamente con google-services.json
    }

    private fun setupRecyclerView() {
        ventasAdapter = VentasAdapter(ventasList) { venta ->
            generateAndSharePDF(venta)
        }
        binding.rvVentas.apply {
            layoutManager = LinearLayoutManager(this@ListaVentasActivity)
            adapter = ventasAdapter
        }
    }

    private fun setupDatePickers() {
        binding.etFechaInicio.setOnClickListener {
            showDatePicker { date ->
                binding.etFechaInicio.setText(date)
            }
        }

        binding.etFechaFin.setOnClickListener {
            showDatePicker { date ->
                binding.etFechaFin.setText(date)
            }
        }
    }

    private fun setupSearchButton() {
        binding.btnBuscar.setOnClickListener {
            searchVentasByDateRange()
        }

        binding.btnLimpiar.setOnClickListener {
            loadAllVentas()
            val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            binding.etFechaInicio.setText(currentDate)
            binding.etFechaFin.setText(currentDate)
        }
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
    private fun loadAllVentas() {
        binding.progressBar.visibility = android.view.View.VISIBLE

        firestore.collection("ventas")
            .whereEqualTo("estado", true) // Solo ventas activas
            .orderBy("fechaVenta", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                ventasList.clear()
                for (document in documents) {
                    try {
                        val venta = document.toObject(Venta::class.java).copy(id = document.id)
                        ventasList.add(venta)
                    } catch (e: Exception) {
                        // Log error pero continúa con otros documentos
                        android.util.Log.e("ListaVentas", "Error parsing document: ${document.id}", e)
                    }
                }
                ventasAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvTotalVentas.text = "Total: ${ventasList.size} ventas"
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al cargar ventas: ${exception.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = android.view.View.GONE
                android.util.Log.e("ListaVentas", "Error loading ventas", exception)
            }
    }

    private fun searchVentasByDateRange() {
        val fechaInicioStr = binding.etFechaInicio.text.toString()
        val fechaFinStr = binding.etFechaFin.text.toString()

        if (fechaInicioStr.isEmpty() || fechaFinStr.isEmpty()) {
            Toast.makeText(this, "Seleccione ambas fechas", Toast.LENGTH_SHORT).show()
            return
        }

        val fechaInicio = parseDate(fechaInicioStr)
        val fechaFin = parseDate(fechaFinStr)

        if (fechaInicio == null || fechaFin == null) {
            Toast.makeText(this, "Formato de fecha inválido", Toast.LENGTH_SHORT).show()
            return
        }

        // Ajustar hora para incluir todo el día
        val calendar = Calendar.getInstance()
        calendar.time = fechaFin
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val fechaFinAjustada = calendar.time

        binding.progressBar.visibility = android.view.View.VISIBLE

        firestore.collection("ventas")
            .whereEqualTo("estado", true) // Solo ventas activas
            .whereGreaterThanOrEqualTo("fechaVenta", Timestamp(fechaInicio))
            .whereLessThanOrEqualTo("fechaVenta", Timestamp(fechaFinAjustada))
            .orderBy("fechaVenta", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                ventasList.clear()
                for (document in documents) {
                    try {
                        val venta = document.toObject(Venta::class.java).copy(id = document.id)
                        ventasList.add(venta)
                    } catch (e: Exception) {
                        android.util.Log.e("ListaVentas", "Error parsing document: ${document.id}", e)
                    }
                }
                ventasAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = android.view.View.GONE
                binding.tvTotalVentas.text = "Total: ${ventasList.size} ventas en el rango seleccionado"
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Error al buscar ventas: ${exception.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = android.view.View.GONE
                android.util.Log.e("ListaVentas", "Error searching ventas", exception)
            }
    }

    private fun parseDate(dateStr: String): Date? {
        return try {
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(dateStr)
        } catch (e: Exception) {
            null
        }
    }

    private fun generateAndSharePDF(venta: Venta) {
        // Para Android 10+ no necesitamos permisos de almacenamiento para archivos internos
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            // Solo para Android 9 y anteriores
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
                return
            }
        }

        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(226, 400, 1).create() // 80mm width
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            drawPDFContent(canvas, venta)

            pdfDocument.finishPage(page)

            // Guardar PDF en almacenamiento interno de la app (no requiere permisos)
            val fileName = "Nota_Venta_${venta.numeroFactura.replace("/", "_")}.pdf"
            val file = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+: usar almacenamiento interno
                File(filesDir, fileName)
            } else {
                // Android 9 y anteriores: usar almacenamiento externo
                File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
            }

            file.parentFile?.mkdirs() // Crear directorios si no existen

            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            // Compartir por WhatsApp
            shareViaWhatsApp(file, venta.clienteNombre)

        } catch (e: Exception) {
            Toast.makeText(this, "Error al generar PDF: ${e.message}", Toast.LENGTH_LONG).show()
            android.util.Log.e("ListaVentas", "Error generating PDF", e)
        }
    }

    private fun drawPDFContent(canvas: Canvas, venta: Venta) {
        val mono = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val bold = Paint(mono).apply {
            textSize = 10f
            isFakeBoldText = true
        }

        // Paints con alineación fija (evitamos mutar .textAlign en los mismos objetos)
        val leftPaint = Paint(mono).apply { textAlign = Paint.Align.LEFT }
        val centerPaint = Paint(mono).apply { textAlign = Paint.Align.CENTER }
        val rightPaint = Paint(mono).apply { textAlign = Paint.Align.RIGHT }
        val boldCenter = Paint(bold).apply { textAlign = Paint.Align.CENTER }
        val boldRight = Paint(bold).apply { textAlign = Paint.Align.RIGHT }

        val pageWidth = canvas.width.toFloat()
        val margin = 10f
        var y = 25f
        val lineHeight = mono.fontSpacing // altura de línea recomendada

        // Encabezado (centrado)
        canvas.drawText("LizaChick \uD83E\uDD73 \uD83E\uDD51", pageWidth / 2f, y, boldCenter); y += lineHeight * 0.9f
        canvas.drawText("Av Jorge Chavez 103 Int J-1", pageWidth / 2f, y, centerPaint); y += lineHeight * 0.9f
        canvas.drawText("912558460 – 900316915", pageWidth / 2f, y, centerPaint); y += lineHeight * 1.1f
        canvas.drawText("NOTA DE VENTA", pageWidth / 2f, y, boldCenter); y += lineHeight * 1.3f

        // Datos venta
        canvas.drawText("Factura: ${venta.numeroFactura}", margin, y, leftPaint); y += lineHeight * 0.9f
        canvas.drawText("Cliente: ${venta.clienteNombre}", margin, y, leftPaint); y += lineHeight * 0.9f
        val fechaStr = venta.fechaVenta?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it.toDate())
        } ?: "N/A"
        canvas.drawText("Fecha: $fechaStr", margin, y, leftPaint); y += lineHeight * 0.9f
        canvas.drawText("Pago: ${venta.medioPago}", margin, y, leftPaint); y += lineHeight * 1.2f

        // Calculamos anchos de columnas dinámicamente en base al contenido
        // Primero medimos el ancho máximo del texto "Total" y de las cantidades
        val detalleList = venta.detalles.values.toList()

        val maxTotalTextWidth = (detalleList.map {
            rightPaint.measureText("S/${String.format(Locale.getDefault(), "%.2f", it.subtotal)}")
        }.maxOrNull() ?: rightPaint.measureText("S/0.00")) + 10f // padding

        val maxQtyTextWidth = (detalleList.map {
            centerPaint.measureText(it.cantidad.toString())
        }.maxOrNull() ?: centerPaint.measureText("0")) + 8f

        val gap = 8f
        val totalColWidth = maxOf(maxTotalTextWidth, 60f) // al menos 60 px
        val qtyColWidth = maxOf(maxQtyTextWidth, 28f)
        val productColWidth = pageWidth - margin * 2f - totalColWidth - qtyColWidth - gap * 2f

        val productX = margin
        val qtyCenterX = productX + productColWidth + gap + qtyColWidth / 2f
        val totalRightX = pageWidth - margin

        // Encabezado de tabla
        canvas.drawText("Producto", productX, y, bold)
        canvas.drawText("Cant", qtyCenterX, y, boldCenter)
        canvas.drawText("Total", totalRightX, y, boldRight)
        y += lineHeight * 0.9f
        y += lineHeight * 0.5f

        // Detalle con wrapping del nombre del producto si es necesario
        detalleList.forEach { d ->
            val productName = d.productoNombre ?: ""
            var start = 0
            val end = productName.length
            var firstLineForThisItem = true

            // Si no cabe todo en una línea, breakText nos dice cuántos caracteres entran
            while (start < end) {
                var count = leftPaint.breakText(productName, start, end, true, productColWidth, null)
                if (count <= 0) {
                    // Forzamos al menos 1 char para evitar bucle infinito
                    count = 1
                }
                val part = productName.substring(start, start + count)
                canvas.drawText(part, productX, y, leftPaint)

                if (firstLineForThisItem) {
                    // Dibujamos cantidad y total solo en la primera línea del producto
                    canvas.drawText(d.cantidad.toString(), qtyCenterX, y, centerPaint)
                    canvas.drawText("S/${String.format(Locale.getDefault(), "%.2f", d.subtotal)}", totalRightX, y, rightPaint)
                    firstLineForThisItem = false
                }

                start += count
                // si quedan más caracteres, saltamos a la siguiente línea
                if (start < end) {
                    y += lineHeight * 0.9f
                }
            }

            // espacio entre filas
            y += lineHeight * 1.1f
        }

        // Separador antes de totales
        y += lineHeight * 0.2f
        y += lineHeight * 0.8f

        // Totales (alineados a la derecha)
        val labelOffset = totalColWidth + qtyColWidth + gap * 2f + 20f // lo movemos más a la izquierda

        canvas.drawText("SUBTOTAL", totalRightX - labelOffset, y, leftPaint)
        canvas.drawText("S/${String.format(Locale.getDefault(), "%.2f", venta.subtotal)}", totalRightX, y, rightPaint)
        y += lineHeight * 0.9f

        canvas.drawText("IGV", totalRightX - labelOffset, y, leftPaint)
        canvas.drawText("S/${String.format(Locale.getDefault(), "%.2f", venta.igv)}", totalRightX, y, rightPaint)
        y += lineHeight * 0.9f

        canvas.drawText("TOTAL", totalRightX - labelOffset, y, bold)
        canvas.drawText("S/${String.format(Locale.getDefault(), "%.2f", venta.total)}", totalRightX, y, boldRight)
        y += lineHeight * 1.4f

        // Mensaje final centrado
        canvas.drawText("------------------------------", pageWidth / 2f, y, boldCenter); y += lineHeight * 0.9f
        canvas.drawText("¡Gracias por su compra!", pageWidth / 2f, y, boldCenter); y += lineHeight * 0.9f
        canvas.drawText("------------------------------", pageWidth / 2f, y, boldCenter)
    }

    private fun drawPDFContentAntiguo(canvas: Canvas, venta: Venta) {
        // Fuente monoespaciada
        val monoPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        val boldMono = Paint(monoPaint).apply {
            textSize = 10f
            isFakeBoldText = true
        }

        val rightAlign = Paint(monoPaint).apply { textAlign = Paint.Align.RIGHT }
        val centerAlign = Paint(monoPaint).apply { textAlign = Paint.Align.CENTER }
        val boldRight = Paint(boldMono).apply { textAlign = Paint.Align.RIGHT }

        val pageWidth = canvas.width.toFloat()
        val leftMargin = 10f
        val rightMargin = pageWidth - 10f
        var y = 25f

        // Encabezado
        canvas.drawText("\uD83E\uDD73 LizaChick \uD83E\uDD73", pageWidth / 2, y, boldMono.apply { textAlign = Paint.Align.CENTER })
        y += 12f
        canvas.drawText("Av Jorge Chavez 103 Int J-1", pageWidth / 2, y, monoPaint.apply { textAlign = Paint.Align.CENTER })
        y += 10f
        canvas.drawText("912558460 – 900316915", pageWidth / 2, y, monoPaint)
        y += 18f

        canvas.drawText("NOTA DE VENTA", pageWidth / 2, y, boldMono)
        y += 20f

        // Datos venta
        monoPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Factura: ${venta.numeroFactura}", leftMargin, y, monoPaint); y += 12f
        canvas.drawText("Cliente: ${venta.clienteNombre}", leftMargin, y, monoPaint); y += 12f

        val fechaStr = venta.fechaVenta?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it.toDate())
        } ?: "N/A"
        canvas.drawText("Fecha: $fechaStr", leftMargin, y, monoPaint); y += 12f
        canvas.drawText("Pago: ${venta.medioPago}", leftMargin, y, monoPaint); y += 20f

        // Línea separadora
        canvas.drawLine(leftMargin, y, rightMargin, y, monoPaint); y += 15f

        // Cabecera tabla
        canvas.drawText("Producto", leftMargin, y, boldMono)
        canvas.drawText("Cant", pageWidth / 2, y, boldMono.apply { textAlign = Paint.Align.CENTER })
        canvas.drawText("Total", rightMargin, y, boldMono.apply { textAlign = Paint.Align.RIGHT })
        y += 12f
        canvas.drawLine(leftMargin, y, rightMargin, y, monoPaint); y += 12f

        // Detalle productos
        venta.detalles.values.forEach { d ->
            monoPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(d.productoNombre.take(18), leftMargin, y, monoPaint)

            monoPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("${d.cantidad}", pageWidth / 2, y, monoPaint)

            monoPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("S/${String.format("%.2f", d.subtotal)}", rightMargin, y, monoPaint)

            y += 12f
        }

        y += 5f
        canvas.drawLine(leftMargin, y, rightMargin, y, monoPaint); y += 15f

        // Totales
        canvas.drawText("SUBTOTAL", rightMargin - 70f, y, monoPaint)
        canvas.drawText("S/${String.format("%.2f", venta.subtotal)}", rightMargin, y, rightAlign); y += 12f

        canvas.drawText("IGV", rightMargin - 70f, y, monoPaint)
        canvas.drawText("S/${String.format("%.2f", venta.igv)}", rightMargin, y, rightAlign); y += 12f

        canvas.drawText("TOTAL", rightMargin - 70f, y, boldMono)
        canvas.drawText("S/${String.format("%.2f", venta.total)}", rightMargin, y, boldRight); y += 20f

        // Mensaje final
        canvas.drawText("------------------------------", pageWidth / 2, y, centerAlign); y += 15f
        canvas.drawText("¡Gracias por su compra!", pageWidth / 2, y, centerAlign); y += 15f
        canvas.drawText("------------------------------", pageWidth / 2, y, centerAlign)
    }


    private fun shareViaWhatsApp(file: File, clienteName: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Nota de venta para: $clienteName")
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // WhatsApp no instalado, usar intent genérico
                val genericIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Nota de venta para: $clienteName")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(genericIntent, "Compartir nota de venta"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}