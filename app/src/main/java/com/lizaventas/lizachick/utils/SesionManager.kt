package com.lizaventas.lizachick.utils

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()

    companion object {
        private const val PREFS_NAME = "TiendaPrefs"
        private const val KEY_USER_ID = "usuario_activo"
        private const val KEY_USER_ROLE = "rol_activo"
        private const val KEY_USER_NAME = "nombre_usuario"
        private const val KEY_USER_EMAIL = "email_usuario"
        private const val KEY_LOGIN_TIMESTAMP = "timestamp_login"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    /**
     * Crear sesión de usuario
     */
    fun createLoginSession(
        userId: String,
        userRole: String,
        userName: String,
        userEmail: String
    ) {
        editor.putString(KEY_USER_ID, userId)
        editor.putString(KEY_USER_ROLE, userRole)
        editor.putString(KEY_USER_NAME, userName)
        editor.putString(KEY_USER_EMAIL, userEmail)
        editor.putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
    }

    /**
     * Obtener datos del usuario actual
     */
    fun getUserSession(): UserSession? {
        return if (isLoggedIn()) {
            UserSession(
                userId = prefs.getString(KEY_USER_ID, "") ?: "",
                userRole = prefs.getString(KEY_USER_ROLE, "") ?: "",
                userName = prefs.getString(KEY_USER_NAME, "") ?: "",
                userEmail = prefs.getString(KEY_USER_EMAIL, "") ?: "",
                loginTimestamp = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0)
            )
        } else {
            null
        }
    }

    /**
     * Verificar si hay una sesión activa
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false) &&
                !prefs.getString(KEY_USER_ID, "").isNullOrEmpty()
    }

    /**
     * Verificar permisos por rol
     */
    fun hasPermission(permission: Permission): Boolean {
        val userRole = prefs.getString(KEY_USER_ROLE, "")
        return when (permission) {
            Permission.VENTAS -> userRole in listOf("Administrador", "Vendedor")
            Permission.INVENTARIO -> userRole in listOf("Administrador", "Vendedor")
            Permission.USUARIOS -> userRole == "Administrador"
            Permission.REPORTES -> userRole == "Administrador"
            Permission.CONFIGURACION -> userRole == "Administrador"
        }
    }

    /**
     * Obtener rol del usuario actual
     */
    fun getCurrentUserRole(): String {
        return prefs.getString(KEY_USER_ROLE, "") ?: ""
    }

    /**
     * Obtener ID del usuario actual
     */
    fun getCurrentUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    /**
     * Obtener nombre del usuario actual
     */
    fun getCurrentUserName(): String {
        return prefs.getString(KEY_USER_NAME, "") ?: ""
    }

    /**
     * Cerrar sesión
     */
    fun logoutUser() {
        editor.clear()
        editor.apply()
    }

    /**
     * Actualizar timestamp de última actividad
     */
    fun updateLastActivity() {
        editor.putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
        editor.apply()
    }

    /**
     * Verificar si la sesión ha expirado (opcional)
     */
    fun isSessionExpired(expirationTimeInMillis: Long = 24 * 60 * 60 * 1000): Boolean {
        val lastActivity = prefs.getLong(KEY_LOGIN_TIMESTAMP, 0)
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastActivity) > expirationTimeInMillis
    }

    /**
     * Obtener información completa de la sesión para debugging
     */
    fun getSessionInfo(): String {
        return if (isLoggedIn()) {
            val session = getUserSession()!!
            """
            Usuario: ${session.userName}
            Rol: ${session.userRole}
            Email: ${session.userEmail}
            Última actividad: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss",
                java.util.Locale.getDefault()).format(session.loginTimestamp)}
            """.trimIndent()
        } else {
            "No hay sesión activa"
        }
    }
}

/**
 * Data class para representar una sesión de usuario
 */
data class UserSession(
    val userId: String,
    val userRole: String,
    val userName: String,
    val userEmail: String,
    val loginTimestamp: Long
)

/**
 * Enum para definir permisos por módulo
 */
enum class Permission {
    VENTAS,
    INVENTARIO,
    USUARIOS,
    REPORTES,
    CONFIGURACION
}