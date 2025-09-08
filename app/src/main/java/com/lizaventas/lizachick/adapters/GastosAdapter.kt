package com.lizaventas.lizachick.adapters

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ItemGastoBinding
import com.lizaventas.lizachick.models.Gasto
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

class GastosAdapter(
    private val gastos: List<Gasto>,
    private val onEditClick: (Gasto) -> Unit,
    private val onDeleteClick: (Gasto) -> Unit,
    private val onToggleEstado: (Gasto) -> Unit
) : RecyclerView.Adapter<GastosAdapter.GastoViewHolder>() {

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val currencyFormatter = NumberFormat.getCurrencyInstance(Locale("es", "PE"))

    class GastoViewHolder(val binding: ItemGastoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val binding = ItemGastoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GastoViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = gastos[position]

        with(holder.binding) {
            tvMotivo.text = gasto.motivo
            tvMonto.text = currencyFormatter.format(gasto.monto)

            // Formatear fecha
            try {
                val date = gasto.fecha?.toDate() ?: Date()
                val formattedDate = dateFormatter.format(date)
                tvFecha.text = formattedDate
            } catch (e: Exception) {
                tvFecha.text = "Fecha inválida"
            }

            // Estado visual
            val context = holder.itemView.context
            if (gasto.estado) {
                tvEstado.text = "Pagado"
                tvEstado.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
                cardGasto.strokeColor = ContextCompat.getColor(context, android.R.color.holo_green_light)
            } else {
                tvEstado.text = "Pendiente"
                tvEstado.setTextColor(ContextCompat.getColor(context, android.R.color.holo_orange_light))
                cardGasto.strokeColor = ContextCompat.getColor(context, android.R.color.holo_orange_light)
            }

            // Click listeners
            btnEdit.setOnClickListener { onEditClick(gasto) }
            btnDelete.setOnClickListener { onDeleteClick(gasto) }
            cardGasto.setOnClickListener { onToggleEstado(gasto) }
        }
    }

    override fun getItemCount() = gastos.size
}