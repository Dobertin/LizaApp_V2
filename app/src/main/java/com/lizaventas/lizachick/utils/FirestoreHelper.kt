package com.lizaventas.lizachick.utils

import com.google.firebase.firestore.*

class FirestoreHelper {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    /**
     * Obtener lista de roles desde Firestore
     */
    fun getRoles(callback: (List<Role>) -> Unit, onError: (String) -> Unit) {
        firestore.collection("roles")
            .get()
            .addOnSuccessListener { documents ->
                val roles = mutableListOf<Role>()

                for (document in documents) {
                    val roleId = document.id
                    val nombre = document.getString("nombre")
                    val descripcion = document.getString("descripcion") ?: ""

                    if (nombre != null) {
                        roles.add(Role(roleId, nombre, descripcion))
                    }
                }

                callback(roles)
            }
            .addOnFailureListener { exception ->
                onError("Error al cargar roles: ${exception.message}")
            }
    }

    /**
     * Obtener usuario por rol
     */
    fun getUserByRole(roleName: String, callback: (Usuario?) -> Unit, onError: (String) -> Unit) {
        firestore.collection("usuarios")
            .whereEqualTo("rol", roleName)
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->
                var foundUser: Usuario? = null

                if (!documents.isEmpty) {
                    val document = documents.documents[0] // Tomar el primero
                    val userId = document.id
                    val nombreUsuario = document.getString("nombreUsuario")
                    val email = document.getString("email")
                    val rol = document.getString("rol")
                    val estado = document.getBoolean("estado")

                    if (nombreUsuario != null && email != null && rol != null && estado == true) {
                        foundUser = Usuario(userId, nombreUsuario, email, rol, estado)
                    }
                }

                callback(foundUser)
            }
            .addOnFailureListener { exception ->
                onError("Error al buscar usuario: ${exception.message}")
            }
    }

    /**
     * Obtener todos los usuarios
     */
    fun getAllUsers(callback: (List<Usuario>) -> Unit, onError: (String) -> Unit) {
        firestore.collection("usuarios")
            .get()
            .addOnSuccessListener { documents ->
                val usuarios = mutableListOf<Usuario>()

                for (document in documents) {
                    val userId = document.id
                    val nombreUsuario = document.getString("nombreUsuario")
                    val email = document.getString("email")
                    val rol = document.getString("rol")
                    val estado = document.getBoolean("estado")

                    if (nombreUsuario != null && email != null && rol != null && estado != null) {
                        usuarios.add(Usuario(userId, nombreUsuario, email, rol, estado))
                    }
                }

                callback(usuarios)
            }
            .addOnFailureListener { exception ->
                onError("Error al cargar usuarios: ${exception.message}")
            }
    }

    /**
     * Obtener información del usuario por ID
     */
    fun getUserById(userId: String, callback: (Usuario?) -> Unit, onError: (String) -> Unit) {
        firestore.collection("usuarios").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val nombreUsuario = document.getString("nombreUsuario")
                    val email = document.getString("email")
                    val rol = document.getString("rol")
                    val estado = document.getBoolean("estado")

                    if (nombreUsuario != null && email != null && rol != null && estado != null) {
                        callback(Usuario(userId, nombreUsuario, email, rol, estado))
                    } else {
                        callback(null)
                    }
                } else {
                    callback(null)
                }
            }
            .addOnFailureListener { exception ->
                onError("Error al obtener usuario: ${exception.message}")
            }
    }

    /**
     * Actualizar último acceso del usuario
     */
    fun updateUserLastAccess(userId: String, callback: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val updates = hashMapOf<String, Any>(
            "ultimoAcceso" to System.currentTimeMillis()
        )

        firestore.collection("usuarios").document(userId)
            .update(updates)
            .addOnSuccessListener {
                callback?.invoke()
            }
            .addOnFailureListener { exception ->
                onError?.invoke("Error al actualizar último acceso: ${exception.message}")
            }
    }

    /**
     * Crear un nuevo rol
     */
    fun createRole(nombre: String, descripcion: String, callback: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val role = hashMapOf(
            "nombre" to nombre,
            "descripcion" to descripcion
        )

        firestore.collection("roles")
            .add(role)
            .addOnSuccessListener {
                callback?.invoke()
            }
            .addOnFailureListener { exception ->
                onError?.invoke("Error al crear rol: ${exception.message}")
            }
    }

    /**
     * Crear un nuevo usuario
     */
    fun createUser(
        nombreUsuario: String,
        email: String,
        rol: String,
        estado: Boolean = true,
        callback: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val user = hashMapOf(
            "nombreUsuario" to nombreUsuario,
            "email" to email,
            "rol" to rol,
            "estado" to estado,
            "fechaCreacion" to System.currentTimeMillis()
        )

        firestore.collection("usuarios")
            .add(user)
            .addOnSuccessListener {
                callback?.invoke()
            }
            .addOnFailureListener { exception ->
                onError?.invoke("Error al crear usuario: ${exception.message}")
            }
    }

    /**
     * Verificar disponibilidad de Firestore
     */
    fun testConnection(callback: (Boolean) -> Unit) {
        firestore.collection("test")
            .limit(1)
            .get()
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }
}

/**
 * Data classes para representar los datos (reutilizamos las mismas)
 */
data class Role(
    val id: String,
    val nombre: String,
    val descripcion: String
)

data class Usuario(
    val id: String,
    val nombreUsuario: String,
    val email: String,
    val rol: String,
    val estado: Boolean
)