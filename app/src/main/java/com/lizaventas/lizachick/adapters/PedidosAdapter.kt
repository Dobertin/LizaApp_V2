package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.databinding.ItemPedidoBinding
import com.lizaventas.lizachick.models.Pedido
import java.text.SimpleDateFormat
import java.util.*

class PedidosAdapter(
    private val pedidos: List<Pedido>,
    private val onItemClick: (Pedido, String) -> Unit
) : RecyclerView.Adapter<PedidosAdapter.PedidoViewHolder>() {

    inner class PedidoViewHolder(private val binding: ItemPedidoBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pedido: Pedido) {
            binding.apply {
                tvClienteNombre.text = pedido.clienteNombre
                tvUsuario.text = "Usuario: ${pedido.usuario}"

                val pendiente = pedido.total - pedido.abonado
                tvPendiente.text = "Pendiente: S/.${String.format("%.2f", pendiente)}"

                // Formatear fecha desde timestamp
                try {
                    val timestamp = pedido.fechaPedido.toLongOrNull()
                    if (timestamp != null) {
                        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        tvFechaPedido.text = "Fecha: ${outputFormat.format(Date(timestamp))}"
                    } else {
                        // Si no es timestamp, intentar parsear como string
                        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                        val fecha = inputFormat.parse(pedido.fechaPedido)
                        val outputFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        tvFechaPedido.text = "Fecha: ${outputFormat.format(fecha ?: Date())}"
                    }
                } catch (e: Exception) {
                    tvFechaPedido.text = "Fecha: ${pedido.fechaPedido}"
                }

                tvCantidadProductos.text = "Productos: ${pedido.detalles.size}"
                tvTotal.text = "Total: S/.${String.format("%.2f", pedido.total)}"
                tvAbonado.text = "Abonado: S/.${String.format("%.2f", pedido.abonado)}"
                tvMedioPago.text = "Pago: ${pedido.medioPago}"

                if (pedido.observaciones.isNotEmpty()) {
                    tvObservaciones.text = "Obs: ${pedido.observaciones}"
                } else {
                    tvObservaciones.text = "Sin observaciones"
                }

                // Configurar botones
                btnModificar.setOnClickListener {
                    onItemClick(pedido, "modificar")
                }

                btnCancelar.setOnClickListener {
                    onItemClick(pedido, "cancelar")
                }

                btnEliminar.setOnClickListener {
                    onItemClick(pedido, "eliminar")
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PedidoViewHolder {
        val binding = ItemPedidoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PedidoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PedidoViewHolder, position: Int) {
        holder.bind(pedidos[position])
    }

    override fun getItemCount(): Int = pedidos.size
}