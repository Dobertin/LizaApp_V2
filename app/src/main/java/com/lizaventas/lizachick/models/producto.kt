package com.lizaventas.lizachick.models

import android.os.Parcelable
import com.google.firebase.Timestamp
import kotlinx.parcelize.Parcelize
import java.util.Date
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@Parcelize
data class Producto(
    val id: String = "",
    val nombre: String = "",
    val categoriaNombre: String = "",
    val marcaNombre: String = "",
    val descripcion: String = "",
    val genero: String = "",
    val familiaOlfativa: String? = null,
    val capacidad: String? = null,
    val precioCatalogo: Double = 0.0,
    val precioVenta: Double = 0.0,
    val stock: Int = 0,
    val imagenUrl: String = "",
    val estado: Boolean = true,
    var fechaCreacion: Date = Date(),
    var modificado: Boolean = false
) : Parcelable{
    fun getInfoCompleta(): String {
        return "$marcaNombre • $categoriaNombre"
    }

    fun getDescripcionCompleta(): String {
        return buildString {
            append(descripcion)
            if (genero.isNotEmpty()) {
                append(" • $genero")
            }
            if (capacidad!!.isNotEmpty()) {
                append(" • $capacidad")
            }
            if (familiaOlfativa!!.isNotEmpty()) {
                append(" • $familiaOlfativa")
            }
        }
    }
}

@Parcelize
data class ItemCarrito(
    val producto: Producto,
    var cantidad: Int = 1,
    var precioUnitario: Double = producto.precioVenta
) : Parcelable {
    val subtotal: Double
        get() = cantidad * precioUnitario
}

@IgnoreExtraProperties
data class ProductoCRUD(
    @DocumentId
    var id: String = "",

    @PropertyName("nombre")
    var nombre: String = "",

    @PropertyName("categoriaNombre")
    var categoriaNombre: String = "",

    @PropertyName("marcaNombre")
    var marcaNombre: String = "",

    @PropertyName("descripcion")
    var descripcion: String = "",

    @PropertyName("genero")
    var genero: String = "",

    @PropertyName("familiaOlfativa")
    var familiaOlfativa: String? = "",

    @PropertyName("capacidad")
    var capacidad: String? = "",

    @PropertyName("precioCatalogo")
    var precioCatalogo: Double = 0.0,

    @PropertyName("precioVenta")
    var precioVenta: Double = 0.0,

    @PropertyName("stock")
    var stock: Int = 0,

    @PropertyName("imagenUrl")
    var imagenUrl: String = "",

    @PropertyName("estado")
    var estado: Boolean = true,

    @PropertyName("fechaCreacion")
    var fechaCreacion: Timestamp = Timestamp.now()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", "", "", "", "", "", "", 0.0, 0.0, 0, "", true, Timestamp.now())
}

@IgnoreExtraProperties
data class Categoria(
    @DocumentId
    var id: String = "",

    @PropertyName("nombre")
    var nombre: String = "",

    @PropertyName("estado")
    var estado: Boolean = true,

    @PropertyName("fechaCreacion")
    var fechaCreacion: Timestamp = Timestamp.now()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", true, Timestamp.now())
}

@IgnoreExtraProperties
data class Marca(
    @DocumentId
    var id: String = "",

    @PropertyName("nombre")
    var nombre: String = "",

    @PropertyName("pais")
    var pais: String = "",

    @PropertyName("estado")
    var estado: Boolean = true,

    @PropertyName("fechaCreacion")
    var fechaCreacion: Timestamp = Timestamp.now()
) {
    // Constructor sin argumentos requerido por Firestore
    constructor() : this("", "", "", true, Timestamp.now())
}