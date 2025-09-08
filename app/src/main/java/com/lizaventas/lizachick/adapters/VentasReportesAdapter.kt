package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.models.VentaReporte
import java.text.SimpleDateFormat
import java.util.*

class VentasReportesAdapter(
    private val ventas: List<VentaReporte>,
    private val onItemClick: (VentaReporte) -> Unit
) : RecyclerView.Adapter<VentasReportesAdapter.VentaViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class VentaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val tvProductos: TextView = view.findViewById(R.id.tvProductos)
        val tvMedioPago: TextView = view.findViewById(R.id.tvMedioPago)
        val tvCliente: TextView = view.findViewById(R.id.tvCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VentaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_venta_reporte, parent, false)
        return VentaViewHolder(view)
    }

    override fun onBindViewHolder(holder: VentaViewHolder, position: Int) {
        val venta = ventas[position]

        holder.tvTotal.text = "S/ ${String.format("%.2f", venta.total)}"
        holder.tvFecha.text = dateFormat.format(Date(venta.fechaVenta))
        holder.tvProductos.text = "${venta.cantidadProductos} item${if (venta.cantidadProductos != 1) "s" else ""}"
        holder.tvMedioPago.text = venta.medioPago
        holder.tvCliente.text = venta.clienteNombre

        holder.itemView.setOnClickListener {
            onItemClick(venta)
        }
    }

    override fun getItemCount() = ventas.size
}