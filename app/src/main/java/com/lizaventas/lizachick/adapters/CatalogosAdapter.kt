package com.lizaventas.lizachick.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.models.Catalogo
import com.lizaventas.lizachick.utils.formatOrNull
import com.lizaventas.lizachick.utils.isExpired

class CatalogosAdapter(
    private val catalogos: List<Catalogo>,
    private val onEditClick: (Catalogo) -> Unit,
    private val onViewPdfClick: (Catalogo) -> Unit,
    private val onDeleteClick: (Catalogo) -> Unit
) : RecyclerView.Adapter<CatalogosAdapter.CatalogoViewHolder>() {

    class CatalogoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombre)
        val tvVigencia: TextView = view.findViewById(R.id.tvVigencia)
        val tvEstado: TextView = view.findViewById(R.id.tvEstado)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
        val btnViewPdf: Button = view.findViewById(R.id.btnViewPdf)
        val btnDelete: Button = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CatalogoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_catalogo, parent, false)
        return CatalogoViewHolder(view)
    }

    override fun onBindViewHolder(holder: CatalogoViewHolder, position: Int) {
        val catalogo = catalogos[position]
        val context = holder.itemView.context

        // Nombre
        holder.tvNombre.text = catalogo.nombre.uppercase()

        // Vigencia
        holder.tvVigencia.text = "Vigencia: ${catalogo.vigencia.formatOrNull()}"
        when {
            catalogo.vigencia == null -> holder.tvVigencia.setTextColor(
                ContextCompat.getColor(context, android.R.color.darker_gray)
            )
            catalogo.vigencia.isExpired() -> {
                holder.tvVigencia.setTextColor(
                    ContextCompat.getColor(context, android.R.color.holo_red_light)
                )
                holder.tvVigencia.text = "${holder.tvVigencia.text} (VENCIDO)"
            }
            else -> holder.tvVigencia.setTextColor(
                ContextCompat.getColor(context, android.R.color.white)
            )
        }

        // Estado con color
        holder.tvEstado.text = if (catalogo.estado) "Activo" else "Inactivo"
        holder.tvEstado.setTextColor(
            if (catalogo.estado)
                ContextCompat.getColor(context, android.R.color.holo_green_light)
            else
                ContextCompat.getColor(context, android.R.color.holo_red_light)
        )

        // Click listeners
        holder.btnEdit.setOnClickListener { onEditClick(catalogo) }
        holder.btnViewPdf.setOnClickListener { onViewPdfClick(catalogo) }
        holder.btnDelete.setOnClickListener { onDeleteClick(catalogo) }

        // Deshabilitar botón PDF si no hay URL
        holder.btnViewPdf.isEnabled = catalogo.rutaUrl.isNotBlank()
        holder.btnViewPdf.alpha = if (catalogo.rutaUrl.isNotBlank()) 1.0f else 0.5f
    }

    override fun getItemCount(): Int = catalogos.size
}
