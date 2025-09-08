package com.lizaventas.lizachick.adapters

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ItemProductoBinding
import com.lizaventas.lizachick.models.Producto

class ProductosAdapter(
    private val productos: List<Producto>,
    private val onImageClick: (Producto) -> Unit,
    private val onNombreClick: (Producto) -> Unit,
    private val onAgregarCarrito: (Producto) -> Unit
) : RecyclerView.Adapter<ProductosAdapter.ProductoViewHolder>() {

    class ProductoViewHolder(val binding: ItemProductoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        val binding = ItemProductoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProductoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        val producto = productos[position]

        with(holder.binding) {
            // Imagen del producto
            Glide.with(holder.itemView.context)
                .load(producto.imagenUrl)
                .placeholder(R.drawable.ic_producto_placeholder)
                .error(R.drawable.ic_producto_placeholder)
                .into(ivProducto)

            // Click en imagen
            ivProducto.setOnClickListener { onImageClick(producto) }

            // Nombre del producto
            tvNombre.text = producto.nombre
            tvNombre.setOnClickListener { onNombreClick(producto) }

            // Información del producto
            tvCategoria.text = producto.categoriaNombre
            tvMarca.text = producto.marcaNombre
            tvGenero.text = producto.genero

            // Familia olfativa (puede ser nula)
            if (producto.familiaOlfativa.isNullOrEmpty()) {
                tvFamiliaOlfativa.visibility = View.GONE
                labelFamiliaOlfativa.visibility = View.GONE
            } else {
                tvFamiliaOlfativa.visibility = View.VISIBLE
                labelFamiliaOlfativa.visibility = View.VISIBLE
                tvFamiliaOlfativa.text = producto.familiaOlfativa
            }

            // Capacidad (puede ser nula)
            if (producto.capacidad.isNullOrEmpty()) {
                tvCapacidad.visibility = View.GONE
                labelCapacidad.visibility = View.GONE
            } else {
                tvCapacidad.visibility = View.VISIBLE
                labelCapacidad.visibility = View.VISIBLE
                tvCapacidad.text = producto.capacidad
            }

            // Precios
            tvPrecioCatalogo.text = "S/. %.2f".format(producto.precioCatalogo)
            tvPrecioCatalogo.paintFlags = tvPrecioCatalogo.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            tvPrecioVenta.text = "S/. %.2f".format(producto.precioVenta)
            tvPrecioVenta.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.precio_venta))

            // Stock
            if (producto.stock <= 0) {
                tvStock.text = "Sin Stock"
                tvStock.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.holo_red_dark))
                btnAgregarCarrito.isEnabled = false
                btnAgregarCarrito.alpha = 0.5f
            } else {
                tvStock.text = "Stock: ${producto.stock}"
                tvStock.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
                btnAgregarCarrito.isEnabled = true
                btnAgregarCarrito.alpha = 1.0f
            }

            // Botón agregar al carrito
            btnAgregarCarrito.setOnClickListener { onAgregarCarrito(producto) }
        }
    }

    override fun getItemCount(): Int = productos.size
}