package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ItemProductoCrudBinding
import com.lizaventas.lizachick.models.ProductoCRUD
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class ProductosCRUDAdapter(
    private val productos: List<ProductoCRUD>,
    private val onEditClick: (ProductoCRUD) -> Unit,
    private val onDeleteClick: (ProductoCRUD) -> Unit,
    private val onToggleEstado: (ProductoCRUD) -> Unit
) : RecyclerView.Adapter<ProductosCRUDAdapter.ProductoViewHolder>() {

    private val formatoMoneda = DecimalFormat("#,##0.00")
    private val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val binding = ItemProductoCrudBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        holder.bind(productos[position])
    }

    override fun getItemCount(): Int = productos.size

    inner class ProductoViewHolder(private val binding: ItemProductoCrudBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(producto: ProductoCRUD) {
            with(binding) {
                // Información básica
                tvNombreProducto.text = producto.nombre
                tvCategoriaProducto.text = producto.categoriaNombre
                tvMarcaProducto.text = producto.marcaNombre
                tvDescripcionProducto.text = producto.descripcion

                // Información adicional
                tvGeneroProducto.text = "Género: ${producto.genero}"
                tvFamiliaOlfativa.text = "Familia: ${producto.familiaOlfativa}"
                tvCapacidadProducto.text = "Capacidad: ${producto.capacidad}"

                // Precios
                tvPrecioCatalogo.text = "Catálogo: S/. ${formatoMoneda.format(producto.precioCatalogo)}"
                tvPrecioVenta.text = "Venta: S/. ${formatoMoneda.format(producto.precioVenta)}"

                // Stock
                tvStockProducto.text = "Stock: ${producto.stock}"

                // Estado del stock
                when {
                    producto.stock <= 0 -> {
                        tvStockProducto.setTextColor(ContextCompat.getColor(itemView.context, R.color.sin_stock))
                        tvEstadoStock.text = "SIN STOCK"
                        tvEstadoStock.setTextColor(ContextCompat.getColor(itemView.context, R.color.sin_stock))
                        viewEstadoStock.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.sin_stock))
                    }
                    producto.stock <= 10 -> {
                        tvStockProducto.setTextColor(ContextCompat.getColor(itemView.context, R.color.estado_en_progreso))
                        tvEstadoStock.text = "STOCK BAJO"
                        tvEstadoStock.setTextColor(ContextCompat.getColor(itemView.context, R.color.estado_en_progreso))
                        viewEstadoStock.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.estado_en_progreso))
                    }
                    else -> {
                        tvStockProducto.setTextColor(ContextCompat.getColor(itemView.context, R.color.estado_finalizado))
                        tvEstadoStock.text = "DISPONIBLE"
                        tvEstadoStock.setTextColor(ContextCompat.getColor(itemView.context, R.color.estado_finalizado))
                        viewEstadoStock.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.estado_finalizado))
                    }
                }

                // Estado del producto
                switchEstado.isChecked = producto.estado
                switchEstado.setOnCheckedChangeListener { _, _ ->
                    onToggleEstado(producto)
                }

                // Fecha de creación
                val fechaCreacion = producto.fechaCreacion.toDate()
                tvFechaCreacion.text = "Creado: ${formatoFecha.format(fechaCreacion)}"

                // Cargar imagen
                if (producto.imagenUrl.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(producto.imagenUrl)
                        .placeholder(R.drawable.ic_producto_placeholder)
                        .error(R.drawable.ic_producto_placeholder)
                        .centerCrop()
                        .into(ivProductoImagen)
                } else {
                    ivProductoImagen.setImageResource(R.drawable.ic_producto_placeholder)
                }

                // Configurar el fondo según el estado
                if (producto.estado) {
                    cardViewProducto.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.item_normal)
                    )
                } else {
                    cardViewProducto.setCardBackgroundColor(
                        ContextCompat.getColor(itemView.context, R.color.item_modificado)
                    )
                }

                // Botones de acción
                btnEditarProducto.setOnClickListener {
                    onEditClick(producto)
                }

                btnEliminarProducto.setOnClickListener {
                    onDeleteClick(producto)
                }

                // Click en la tarjeta para ver detalles
                cardViewProducto.setOnClickListener {
                    // Aquí podrías abrir una actividad de detalles del producto
                    onEditClick(producto)
                }
            }
        }
    }
}