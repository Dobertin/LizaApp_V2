package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.models.GastoReporte
import java.text.SimpleDateFormat
import java.util.*

class GastosReportesAdapter(
    private val gastosList: MutableList<GastoReporte>,
    private val onGastoClick: (GastoReporte) -> Unit
) : RecyclerView.Adapter<GastosReportesAdapter.GastoViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class GastoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtMotivo: TextView = itemView.findViewById(R.id.txtMotivo)
        val txtFecha: TextView = itemView.findViewById(R.id.txtFecha)
        val txtMonto: TextView = itemView.findViewById(R.id.txtMonto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gasto_reporte, parent, false)
        return GastoViewHolder(view)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = gastosList[position]

        holder.txtMotivo.text = gasto.motivo
        holder.txtFecha.text = dateFormat.format(Date(gasto.fecha))
        holder.txtMonto.text = "S/ ${String.format("%.2f", gasto.monto)}"

        holder.itemView.setOnClickListener {
            onGastoClick(gasto)
        }
    }

    override fun getItemCount(): Int = gastosList.size
}