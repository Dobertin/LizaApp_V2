package com.lizaventas.lizachick.activities

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.adapters.VentasReportesAdapter
import com.lizaventas.lizachick.adapters.GastosReportesAdapter
import com.lizaventas.lizachick.databinding.ActivityReportesBinding
import com.lizaventas.lizachick.models.VentaReporte
import com.lizaventas.lizachick.models.GastoReporte
import java.text.SimpleDateFormat
import java.util.*

class ReportesActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReportesActivity"
    }

    // UI Components
    private lateinit var binding: ActivityReportesBinding

    // Firebase
    private lateinit var firestore: FirebaseFirestore

    // Adapters
    private lateinit var ventasAdapter: VentasReportesAdapter
    private lateinit var gastosAdapter: GastosReportesAdapter

    // Data Lists
    private val ventasList = mutableListOf<VentaReporte>()
    private val gastosList = mutableListOf<GastoReporte>()

    // Date Formatters
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    // Totals
    private var totalVentas = 0.0
    private var totalGastos = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeComponents()
        loadDailyData()
    }

    // ================== INITIALIZATION METHODS ==================

    private fun initializeComponents() {
        setupToolbar()
        setupDarkTheme()
        setupRecyclerView()
        setupFirestore()
        setupFilterButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Reportes"
        }
    }

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun setupRecyclerView() {
        // Ventas RecyclerView
        ventasAdapter = VentasReportesAdapter(ventasList) { venta ->
            showVentaDetail(venta)
        }
        binding.recyclerViewVentas.apply {
            layoutManager = LinearLayoutManager(this@ReportesActivity)
            adapter = ventasAdapter
        }

        // Gastos RecyclerView
        gastosAdapter = GastosReportesAdapter(gastosList) { gasto ->
            showGastoDetail(gasto)
        }
        binding.recyclerViewGastos.apply {
            layoutManager = LinearLayoutManager(this@ReportesActivity)
            adapter = gastosAdapter
        }
    }

    private fun setupFirestore() {
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupFilterButtons() {
        binding.btnDiario.setOnClickListener {
            selectButton(binding.btnDiario)
            loadDailyData()
        }

        binding.btnSemanal.setOnClickListener {
            selectButton(binding.btnSemanal)
            loadWeeklyData()
        }

        binding.btnMensual.setOnClickListener {
            selectButton(binding.btnMensual)
            loadMonthlyData()
        }

        binding.btnAnual.setOnClickListener {
            selectButton(binding.btnAnual)
            loadYearlyData()
        }
    }

    private fun selectButton(selectedButton: View) {
        resetButtonStates()
        selectedButton.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_color))
    }

    private fun resetButtonStates() {
        val defaultColor = ContextCompat.getColor(this, R.color.button_secondary)
        listOf(binding.btnDiario, binding.btnSemanal, binding.btnMensual, binding.btnAnual)
            .forEach { it.setBackgroundColor(defaultColor) }
    }

    // ================== DATA LOADING METHODS ==================

    private fun loadDailyData() {
        val (startDate, endDate) = getDayRange()
        loadAllData(startDate, endDate, "daily")
    }

    private fun loadWeeklyData() {
        val (startDate, endDate) = getWeekRange()
        loadAllData(startDate, endDate, "weekly")
    }

    private fun loadMonthlyData() {
        val (startDate, endDate) = getMonthRange()
        loadAllData(startDate, endDate, "monthly")
    }

    private fun loadYearlyData() {
        val (startDate, endDate) = getYearRange()
        loadAllData(startDate, endDate, "yearly")
    }

    private fun loadAllData(startDate: Date, endDate: Date, period: String) {
        loadVentasData(startDate, endDate)
        loadGastosData(startDate, endDate)
        loadChartsData(period, startDate, endDate)
        loadTotalesData(startDate, endDate)
    }

    private fun loadVentasData(startDate: Date, endDate: Date) {
        binding.progressBar.visibility = View.VISIBLE
        ventasList.clear()

        Log.d(TAG, "Cargando ventas desde ${dateFormat.format(startDate)} hasta ${dateFormat.format(endDate)}")

        firestore.collection("ventas")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(endDate))
            .orderBy("fechaVenta", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Ventas encontradas: ${documents.size()}")

                for (document in documents) {
                    try {
                        val fechaVenta = document.getTimestamp("fechaVenta")?.toDate()?.time ?: 0L
                        val detalles = document.get("detalles") as? Map<String, Any> ?: emptyMap()

                        val venta = VentaReporte(
                            id = document.id,
                            clienteNombre = document.getString("clienteNombre") ?: "Cliente Genérico",
                            fechaVenta = fechaVenta,
                            total = document.getDouble("total") ?: 0.0,
                            medioPago = document.getString("medioPago") ?: "No especificado",
                            cantidadProductos = getCantidadProductosFromDetalles(detalles),
                            detalles = getDetallesProductosFromMap(detalles)
                        )
                        ventasList.add(venta)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar venta ${document.id}: ${e.message}")
                    }
                }

                ventasAdapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
                Log.d(TAG, "Ventas cargadas: ${ventasList.size}")
            }
            .addOnFailureListener { exception ->
                binding.progressBar.visibility = View.GONE
                Log.e(TAG, "Error loading ventas", exception)
            }
    }

    private fun loadGastosData(startDate: Date, endDate: Date) {
        gastosList.clear()

        Log.d(TAG, "Cargando gastos desde ${dateFormat.format(startDate)} hasta ${dateFormat.format(endDate)}")

        firestore.collection("gastos")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fecha", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fecha", com.google.firebase.Timestamp(endDate))
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Gastos encontrados: ${documents.size()}")

                for (document in documents) {
                    try {
                        val fechaGasto = document.getTimestamp("fecha")?.toDate()?.time ?: 0L

                        val gasto = GastoReporte(
                            id = document.id,
                            motivo = document.getString("motivo") ?: "Gasto sin motivo",
                            fecha = fechaGasto,
                            monto = document.getDouble("monto") ?: 0.0,
                            estado = document.getBoolean("estado") ?: true
                        )
                        gastosList.add(gasto)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar gasto ${document.id}: ${e.message}")
                    }
                }

                gastosAdapter.notifyDataSetChanged()
                Log.d(TAG, "Gastos cargados: ${gastosList.size}")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading gastos", exception)
            }
    }

    private fun loadTotalesData(startDate: Date, endDate: Date) {
        var ventasProcessed = false
        var gastosProcessed = false
        totalVentas = 0.0
        totalGastos = 0.0

        // Cargar totales de ventas
        firestore.collection("ventas")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    totalVentas += document.getDouble("total") ?: 0.0
                }
                ventasProcessed = true
                if (gastosProcessed) {
                    updateTotalsDisplay()
                }
            }
            .addOnFailureListener {
                ventasProcessed = true
                if (gastosProcessed) {
                    updateTotalsDisplay()
                }
            }

        // Cargar totales de gastos
        firestore.collection("gastos")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fecha", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fecha", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    totalGastos += document.getDouble("monto") ?: 0.0
                }
                gastosProcessed = true
                if (ventasProcessed) {
                    updateTotalsDisplay()
                }
            }
            .addOnFailureListener {
                gastosProcessed = true
                if (ventasProcessed) {
                    updateTotalsDisplay()
                }
            }
    }

    private fun updateTotalsDisplay() {
        val totalNeto = totalVentas - totalGastos

        binding.txtTotalVentas.text = "Total Ventas: S/ ${String.format("%.2f", totalVentas)}"
        binding.txtTotalGastos.text = "Total Gastos: S/ ${String.format("%.2f", totalGastos)}"
        binding.txtTotalNeto.text = "Total Neto: S/ ${String.format("%.2f", totalNeto)}"

        // Cambiar color del total neto según si es positivo o negativo
        binding.txtTotalNeto.setTextColor(
            if (totalNeto >= 0)
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            else
                ContextCompat.getColor(this, android.R.color.holo_red_light)
        )
    }

    // ================== CHART METHODS ==================

    private fun loadChartsData(period: String, startDate: Date, endDate: Date) {
        loadPieChartData(startDate, endDate)
        loadBarChartData(period)
    }

    private fun loadPieChartData(startDate: Date, endDate: Date) {
        var totalVentasChart = 0.0
        var totalPedidos = 0.0
        var totalGastosChart = 0.0
        var ventasProcessed = false
        var pedidosProcessed = false
        var gastosProcessed = false

        // Cargar ventas para pie chart
        firestore.collection("ventas")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    totalVentasChart += document.getDouble("total") ?: 0.0
                }
                ventasProcessed = true
                if (pedidosProcessed && gastosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
                Log.d(TAG, "Total ventas para pie chart: $totalVentasChart")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading ventas for chart", exception)
                ventasProcessed = true
                if (pedidosProcessed && gastosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
            }

        // Cargar pedidos para pie chart
        firestore.collection("pedidos")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fechaPedido", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fechaPedido", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    totalPedidos += document.getDouble("total") ?: 0.0
                }
                pedidosProcessed = true
                if (ventasProcessed && gastosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
                Log.d(TAG, "Total pedidos para pie chart: $totalPedidos")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading pedidos for chart", exception)
                pedidosProcessed = true
                if (ventasProcessed && gastosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
            }

        // Cargar gastos para pie chart
        firestore.collection("gastos")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fecha", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fecha", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    totalGastosChart += document.getDouble("monto") ?: 0.0
                }
                gastosProcessed = true
                if (ventasProcessed && pedidosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
                Log.d(TAG, "Total gastos para pie chart: $totalGastosChart")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading gastos for chart", exception)
                gastosProcessed = true
                if (ventasProcessed && pedidosProcessed) {
                    setupPieChart(totalVentasChart, totalPedidos, totalGastosChart)
                }
            }
    }

    private fun setupPieChart(totalVentas: Double, totalPedidos: Double, totalGastos: Double) {
        val entries = mutableListOf<PieEntry>()

        if (totalVentas > 0) entries.add(PieEntry(totalVentas.toFloat(), "Ventas"))
        if (totalPedidos > 0) entries.add(PieEntry(totalPedidos.toFloat(), "Pedidos"))
        if (totalGastos > 0) entries.add(PieEntry(totalGastos.toFloat(), "Gastos"))

        if (entries.isEmpty()) {
            binding.pieChart.visibility = View.GONE
            return
        }

        val dataSet = PieDataSet(entries, "Ventas vs Pedidos vs Gastos").apply {
            colors = listOf(
                ContextCompat.getColor(this@ReportesActivity, R.color.primary_color),
                ContextCompat.getColor(this@ReportesActivity, R.color.secondary_color),
                ContextCompat.getColor(this@ReportesActivity, android.R.color.holo_red_light)
            )
            valueTextColor = Color.WHITE
            valueTextSize = 12f
        }

        val data = PieData(dataSet).apply {
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "S/ ${String.format("%.2f", value)}"
                }
            })
        }

        binding.pieChart.apply {
            this.data = data
            setUsePercentValues(false)
            description = Description().apply { text = "" }
            setEntryLabelColor(Color.WHITE)
            legend.textColor = Color.WHITE
            animateY(1400, Easing.EaseInOutQuad)
            invalidate()
            visibility = View.VISIBLE
        }
    }

    private fun loadBarChartData(period: String) {
        when (period) {
            "daily" -> loadLast5DaysData()
            "weekly" -> loadLast3WeeksData()
            "monthly" -> loadLast3MonthsData()
            else -> loadLast5DaysData()
        }
    }

    private fun loadLast5DaysData() {
        val dailyVentas = mutableMapOf<String, Float>()
        val dailyGastos = mutableMapOf<String, Float>()
        val labels = mutableListOf<String>()

        // Generar etiquetas para los últimos 5 días INCLUYENDO HOY
        val calendar = Calendar.getInstance()
        for (i in 4 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayLabel = SimpleDateFormat("dd/MM", Locale.getDefault()).format(calendar.time)
            labels.add(dayLabel)
            dailyVentas[dayLabel] = 0f
            dailyGastos[dayLabel] = 0f
        }

        val (startDate, endDate) = getLast5DaysRange()
        loadBarChartDataForPeriod(startDate, endDate, labels, dailyVentas, dailyGastos, "dd/MM", "Últimos 5 días")
    }

    private fun loadLast3WeeksData() {
        val weeklyVentas = mutableMapOf<String, Float>()
        val weeklyGastos = mutableMapOf<String, Float>()
        val labels = mutableListOf<String>()

        val calendar = Calendar.getInstance()
        for (i in 2 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.WEEK_OF_YEAR, -i)
            val weekLabel = "Sem ${calendar.get(Calendar.WEEK_OF_YEAR)}"
            labels.add(weekLabel)
            weeklyVentas[weekLabel] = 0f
            weeklyGastos[weekLabel] = 0f
        }

        val (startDate, endDate) = getLast3WeeksRange()
        loadBarChartDataForPeriod(startDate, endDate, labels, weeklyVentas, weeklyGastos, "week", "Últimas 3 semanas")
    }

    private fun loadLast3MonthsData() {
        val monthlyVentas = mutableMapOf<String, Float>()
        val monthlyGastos = mutableMapOf<String, Float>()
        val labels = mutableListOf<String>()

        val calendar = Calendar.getInstance()
        for (i in 2 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)
            val monthLabel = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)
            labels.add(monthLabel)
            monthlyVentas[monthLabel] = 0f
            monthlyGastos[monthLabel] = 0f
        }

        val (startDate, endDate) = getLast3MonthsRange()
        loadBarChartDataForPeriod(startDate, endDate, labels, monthlyVentas, monthlyGastos, "MMM", "Últimos 3 meses")
    }

    private fun loadBarChartDataForPeriod(
        startDate: Date,
        endDate: Date,
        labels: List<String>,
        ventasMap: MutableMap<String, Float>,
        gastosMap: MutableMap<String, Float>,
        datePattern: String,
        title: String
    ) {
        var ventasLoaded = false
        var gastosLoaded = false

        // Cargar ventas
        firestore.collection("ventas")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fechaVenta", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                processVentasForBarChart(documents, ventasMap, datePattern)
                ventasLoaded = true
                if (gastosLoaded) {
                    setupBarChartWithGastos(labels, ventasMap, gastosMap, title)
                }
            }
            .addOnFailureListener {
                ventasLoaded = true
                if (gastosLoaded) {
                    setupBarChartWithGastos(labels, ventasMap, gastosMap, title)
                }
            }

        // Cargar gastos
        firestore.collection("gastos")
            .whereEqualTo("estado", true)
            .whereGreaterThanOrEqualTo("fecha", com.google.firebase.Timestamp(startDate))
            .whereLessThanOrEqualTo("fecha", com.google.firebase.Timestamp(endDate))
            .get()
            .addOnSuccessListener { documents ->
                processGastosForBarChart(documents, gastosMap, datePattern)
                gastosLoaded = true
                if (ventasLoaded) {
                    setupBarChartWithGastos(labels, ventasMap, gastosMap, title)
                }
            }
            .addOnFailureListener {
                gastosLoaded = true
                if (ventasLoaded) {
                    setupBarChartWithGastos(labels, ventasMap, gastosMap, title)
                }
            }
    }

    private fun processVentasForBarChart(
        documents: com.google.firebase.firestore.QuerySnapshot,
        ventasMap: MutableMap<String, Float>,
        datePattern: String
    ) {
        for (document in documents) {
            val fechaVenta = document.getTimestamp("fechaVenta")?.toDate()
            val total = document.getDouble("total") ?: 0.0

            fechaVenta?.let { fecha ->
                val label = when (datePattern) {
                    "week" -> {
                        val cal = Calendar.getInstance()
                        cal.time = fecha
                        "Sem ${cal.get(Calendar.WEEK_OF_YEAR)}"
                    }
                    else -> SimpleDateFormat(datePattern, Locale.getDefault()).format(fecha)
                }

                if (ventasMap.containsKey(label)) {
                    ventasMap[label] = ventasMap[label]!! + total.toFloat()
                }
            }
        }
    }

    private fun processGastosForBarChart(
        documents: com.google.firebase.firestore.QuerySnapshot,
        gastosMap: MutableMap<String, Float>,
        datePattern: String
    ) {
        for (document in documents) {
            val fechaGasto = document.getTimestamp("fecha")?.toDate()
            val monto = document.getDouble("monto") ?: 0.0

            fechaGasto?.let { fecha ->
                val label = when (datePattern) {
                    "week" -> {
                        val cal = Calendar.getInstance()
                        cal.time = fecha
                        "Sem ${cal.get(Calendar.WEEK_OF_YEAR)}"
                    }
                    else -> SimpleDateFormat(datePattern, Locale.getDefault()).format(fecha)
                }

                if (gastosMap.containsKey(label)) {
                    gastosMap[label] = gastosMap[label]!! + monto.toFloat()
                }
            }
        }
    }

    private fun setupBarChartWithGastos(
        labels: List<String>,
        ventasMap: Map<String, Float>,
        gastosMap: Map<String, Float>,
        title: String
    ) {
        val ventasEntries = labels.mapIndexed { index, label ->
            BarEntry(index.toFloat(), ventasMap[label] ?: 0f)
        }

        val gastosEntries = labels.mapIndexed { index, label ->
            BarEntry(index.toFloat(), gastosMap[label] ?: 0f)
        }

        val ventasDataSet = BarDataSet(ventasEntries, "Ventas").apply {
            color = ContextCompat.getColor(this@ReportesActivity, R.color.primary_color)
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }

        val gastosDataSet = BarDataSet(gastosEntries, "Gastos").apply {
            color = ContextCompat.getColor(this@ReportesActivity, android.R.color.holo_red_light)
            valueTextColor = Color.WHITE
            valueTextSize = 10f
        }

        val data = BarData(ventasDataSet, gastosDataSet).apply {
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) "S/ ${String.format("%.0f", value)}" else ""
                }
            })
            barWidth = 0.43f
        }

        // Configurar el ancho de las barras y el espacio entre grupos
        val groupSpace = 0.08f
        val barSpace = 0.03f

        binding.barChart.apply {
            this.data = data
            description = Description().apply { text = "" }

            // Configurar eje X
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textColor = Color.WHITE
                setDrawGridLines(false)
                setCenterAxisLabels(true)
            }

            // Configurar ejes Y
            axisLeft.apply {
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = Color.GRAY
            }
            axisRight.isEnabled = false

            // Configurar leyenda
            legend.textColor = Color.WHITE

            // Agrupar las barras
            groupBars(0f, groupSpace, barSpace)

            animateY(1000)
            invalidate()
        }
    }

    // ================== UTILITY METHODS ==================

    private fun getDayRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val endDate = Date(calendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getWeekRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()

        // Ajustar al lunes de la semana actual
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY

        calendar.apply {
            add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endCalendar = Calendar.getInstance()
        endCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endDate = Date(endCalendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getMonthRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endCalendar = Calendar.getInstance()
        endCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val endDate = Date(endCalendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getYearRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.apply {
            set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endCalendar = Calendar.getInstance()
        endCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }
        val endDate = Date(endCalendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getLast5DaysRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.apply {
            add(Calendar.DAY_OF_YEAR, -4)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endCalendar = Calendar.getInstance()
        endCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)  // Cambiar a 999
        }
        val endDate = Date(endCalendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getLast3WeeksRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()

        // Retroceder 2 semanas completas
        calendar.apply {
            add(Calendar.WEEK_OF_YEAR, -2)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endCalendar = Calendar.getInstance()
        endCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endDate = Date(endCalendar.timeInMillis)

        return Pair(startDate, endDate)
    }

    private fun getLast3MonthsRange(): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        calendar.apply {
            add(Calendar.MONTH, -2)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startDate = Date(calendar.timeInMillis)

        val endDate = Date() // Hasta ahora
        return Pair(startDate, endDate)
    }

    private fun getCantidadProductosFromDetalles(detalles: Map<String, Any>): Int {
        return detalles.values.sumOf { detalle ->
            if (detalle is Map<*, *>) {
                (detalle["cantidad"] as? Long)?.toInt()
                    ?: (detalle["cantidad"] as? Double)?.toInt()
                    ?: 0
            } else 0
        }
    }

    private fun getDetallesProductosFromMap(detalles: Map<String, Any>): List<Map<String, Any>> {
        return detalles.values.mapNotNull { detalle ->
            if (detalle is Map<*, *>) {
                mapOf(
                    "productoNombre" to (detalle["productoNombre"] as? String ?: ""),
                    "cantidad" to ((detalle["cantidad"] as? Long)?.toInt()
                        ?: (detalle["cantidad"] as? Double)?.toInt() ?: 0),
                    "precioUnitario" to (detalle["precioUnitario"] as? Double ?: 0.0),
                    "subtotal" to (detalle["subtotal"] as? Double ?: 0.0)
                )
            } else null
        }
    }

    // ================== DETAIL DIALOGS ==================

    private fun showVentaDetail(venta: VentaReporte) {
        val detallesText = buildString {
            venta.detalles.forEach { detalle ->
                val nombre = detalle["productoNombre"] as? String ?: ""
                val cantidad = detalle["cantidad"] as? Int ?: 0
                val precio = detalle["precioUnitario"] as? Double ?: 0.0
                val subtotal = detalle["subtotal"] as? Double ?: 0.0

                append("• $nombre\n")
                append("  Cantidad: $cantidad\n")
                append("  Precio: S/ ${String.format("%.2f", precio)}\n")
                append("  Subtotal: S/ ${String.format("%.2f", subtotal)}\n\n")
            }
        }

        val message = buildString {
            append("Cliente: ${venta.clienteNombre}\n")
            append("Fecha: ${dateTimeFormat.format(Date(venta.fechaVenta))}\n")
            append("Total: S/ ${String.format("%.2f", venta.total)}\n")
            append("Medio de Pago: ${venta.medioPago}\n\n")
            append("Productos:\n$detallesText")
        }

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Detalle de Venta")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .show()
    }

    private fun showGastoDetail(gasto: GastoReporte) {
        val message = buildString {
            append("Motivo: ${gasto.motivo}\n")
            append("Fecha: ${dateTimeFormat.format(Date(gasto.fecha))}\n")
            append("Monto: S/ ${String.format("%.2f", gasto.monto)}\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle("Detalle de Gasto")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .create()
            .show()
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
}