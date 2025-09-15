package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.models.Venta
import java.text.SimpleDateFormat
import java.util.*

class VentasAdapter(
    private val ventas: List<Venta>,
    private val onWhatsAppClick: (Venta) -> Unit
) : RecyclerView.Adapter<VentasAdapter.VentaViewHolder>() {

    class VentaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumeroFactura: TextView = view.findViewById(R.id.tvNumeroFactura)
        val tvClienteNombre: TextView = view.findViewById(R.id.tvClienteNombre)
        val tvFechaVenta: TextView = view.findViewById(R.id.tvFechaVenta)
        val tvTotal: TextView = view.findViewById(R.id.tvTotal)
        val tvMedioPago: TextView = view.findViewById(R.id.tvMedioPago)
        val tvUsuario: TextView = view.findViewById(R.id.tvUsuario)
        val llDetalles: LinearLayout = view.findViewById(R.id.llDetalles)
        val btnWhatsApp: Button = view.findViewById(R.id.btnWhatsApp)
        val btnExpandir: TextView = view.findViewById(R.id.btnExpandir)
        val layoutDetalles: LinearLayout = view.findViewById(R.id.layoutDetalles)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VentaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_venta, parent, false)
        return VentaViewHolder(view)
    }

    override fun onBindViewHolder(holder: VentaViewHolder, position: Int) {
        val venta = ventas[position]

        holder.tvNumeroFactura.text = "Factura: ${venta.numeroFactura}"
        holder.tvClienteNombre.text = venta.clienteNombre
        holder.tvFechaVenta.text = venta.fechaVenta?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it.toDate())
        } ?: "Sin fecha"
        holder.tvTotal.text = "S/ ${String.format("%.2f", venta.total)}"
        holder.tvMedioPago.text = venta.medioPago
        holder.tvUsuario.text = "Vendedor: ${venta.usuario}"

        // Configurar detalles expandibles
        holder.llDetalles.removeAllViews()
        venta.detalles.values.forEach { detalle ->
            val detalleView = LayoutInflater.from(holder.itemView.context)
                .inflate(R.layout.item_detalle_venta, holder.llDetalles, false)

            detalleView.findViewById<TextView>(R.id.tvProductoNombre).text = detalle.productoNombre
            detalleView.findViewById<TextView>(R.id.tvCantidad).text = "Cant: ${detalle.cantidad}"
            detalleView.findViewById<TextView>(R.id.tvPrecioUnitario).text = "S/ ${String.format("%.2f", detalle.precioUnitario)}"
            detalleView.findViewById<TextView>(R.id.tvSubtotal).text = "S/ ${String.format("%.2f", detalle.subtotal)}"

            holder.llDetalles.addView(detalleView)
        }

        // Control de expandir/contraer
        var isExpanded = false
        holder.layoutDetalles.visibility = if (isExpanded) View.VISIBLE else View.GONE
        holder.btnExpandir.text = if (isExpanded) "Ver menos ▲" else "Ver detalles ▼"

        holder.btnExpandir.setOnClickListener {
            isExpanded = !isExpanded
            holder.layoutDetalles.visibility = if (isExpanded) View.VISIBLE else View.GONE
            holder.btnExpandir.text = if (isExpanded) "Ver menos ▲" else "Ver detalles ▼"
        }

        // Click del botón WhatsApp
        holder.btnWhatsApp.setOnClickListener {
            onWhatsAppClick(venta)
        }
    }

    override fun getItemCount() = ventas.size
}