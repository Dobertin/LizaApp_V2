package com.lizaventas.lizachick.adapters

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ItemCarritoBinding
import com.lizaventas.lizachick.models.ItemCarrito

class CarritoAdapter(
    private val items: List<ItemCarrito>,
    private val onCantidadChanged: (ItemCarrito, Int) -> Unit,
    private val onPrecioChanged: (ItemCarrito, Double) -> Unit,
    private val onEliminar: (ItemCarrito) -> Unit
) : RecyclerView.Adapter<CarritoAdapter.CarritoViewHolder>() {

    class CarritoViewHolder(val binding: ItemCarritoBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarritoViewHolder {
        val binding = ItemCarritoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CarritoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CarritoViewHolder, position: Int) {
        val item = items[position]

        with(holder.binding) {
            // Imagen en miniatura
            Glide.with(holder.itemView.context)
                .load(item.producto.imagenUrl)
                .placeholder(R.drawable.ic_producto_placeholder)
                .error(R.drawable.ic_producto_placeholder)
                .centerCrop()
                .into(ivProductoMini)

            // Nombre del producto
            tvNombreProducto.text = item.producto.nombre

            // Información adicional del producto
            tvInfoProducto.text = "${item.producto.marcaNombre} - ${item.producto.categoriaNombre}"

            // Cantidad - sin listeners para evitar bucles infinitos
            etCantidad.removeTextChangedListener(etCantidad.tag as? TextWatcher)
            etCantidad.setText(item.cantidad.toString())

            // Precio unitario - sin listeners para evitar bucles infinitos
            etPrecio.removeTextChangedListener(etPrecio.tag as? TextWatcher)
            etPrecio.setText("%.2f".format(item.precioUnitario))

            // Subtotal
            val subtotal = item.cantidad * item.precioUnitario
            tvSubtotal.text = "S/. %.2f".format(subtotal)

            // Stock disponible
            tvStockDisponible.text = "Disponible: ${item.producto.stock}"

            // TextWatcher para cantidad
            val cantidadWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (etCantidad.hasFocus()) {
                        val nuevaCantidad = s.toString().toIntOrNull() ?: 1
                        if (nuevaCantidad != item.cantidad) {
                            onCantidadChanged(item, nuevaCantidad)
                        }
                    }
                }
            }
            etCantidad.tag = cantidadWatcher
            etCantidad.addTextChangedListener(cantidadWatcher)

            // TextWatcher para precio
            val precioWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (etPrecio.hasFocus()) {
                        val nuevoPrecio = s.toString().toDoubleOrNull() ?: 0.0
                        if (nuevoPrecio != item.precioUnitario && nuevoPrecio > 0) {
                            onPrecioChanged(item, nuevoPrecio)
                        }
                    }
                }
            }
            etPrecio.tag = precioWatcher
            etPrecio.addTextChangedListener(precioWatcher)

            // Botones para incrementar/decrementar cantidad
            btnDecrementar.setOnClickListener {
                val nuevaCantidad = maxOf(1, item.cantidad - 1)
                if (nuevaCantidad != item.cantidad) {
                    onCantidadChanged(item, nuevaCantidad)
                }
            }

            btnIncrementar.setOnClickListener {
                val nuevaCantidad = item.cantidad + 1
                onCantidadChanged(item, nuevaCantidad)
            }

            // Botón eliminar
            btnEliminar.setOnClickListener {
                onEliminar(item)
            }

            // Deshabilitar botón decrementar si cantidad es 1
            btnDecrementar.isEnabled = item.cantidad > 1
            btnDecrementar.alpha = if (item.cantidad > 1) 1.0f else 0.5f

            // Mostrar warning si cantidad excede stock
            if (item.cantidad > item.producto.stock) {
                tvStockWarning.text = "¡Cantidad excede el stock disponible!"
                tvStockWarning.visibility = android.view.View.VISIBLE
            } else {
                tvStockWarning.visibility = android.view.View.GONE
            }
        }
    }

    override fun getItemCount(): Int = items.size
}