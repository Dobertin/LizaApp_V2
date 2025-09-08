package com.lizaventas.lizachick.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.*
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private var rolesList = mutableListOf<String>()
    private var rolesMap = mutableMapOf<String, String>() // nombre -> id

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar estilo dark
        setupDarkTheme()

        // Inicializar Firebase
        initializeFirebase()

        // Inicializar SharedPreferences
        sharedPreferences = getSharedPreferences("TiendaPrefs", MODE_PRIVATE)

        // Verificar si ya hay una sesión activa
        checkExistingSession()

        // Configurar componentes
        setupComponents()

        // Cargar roles desde Firestore
        loadRoles()
    }

    private fun initializeFirebase() {
        try {
            // Inicializar Firebase si no está inicializado
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }

            // Configurar Firestore
            firestore = FirebaseFirestore.getInstance()

            // Habilitar persistencia offline (opcional)
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build()
                firestore.firestoreSettings = settings
            } catch (e: Exception) {
                throw e
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al conectar con Firebase: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun checkExistingSession() {
        val savedUser = sharedPreferences.getString("usuario_activo", null)
        val savedRole = sharedPreferences.getString("rol_activo", null)

        if (savedUser != null && savedRole != null) {
            navigateToDashboard(savedUser, savedRole)
        } else {
            Toast.makeText(this, "No hay sesión activa", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupComponents() {
        binding.btnLogin.setOnClickListener {
            val selectedRole = binding.spinnerRoles.selectedItem?.toString()

            if (selectedRole != null && selectedRole != "Seleccionar rol...") {
                authenticateUser(selectedRole)
            } else {
                Toast.makeText(this, "Por favor selecciona un rol", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCerrarSesion.setOnClickListener {
            clearSession()
        }
    }

    private fun loadRoles() {

        firestore.collection("roles")
            .get()
            .addOnSuccessListener { documents ->

                rolesList.clear()
                rolesMap.clear()
                rolesList.add("Seleccionar rol...")

                if (!documents.isEmpty) {
                    for (document in documents) {
                        val roleName = document.getString("nombre")
                        val roleId = document.id
                        if (roleName != null) {
                            rolesList.add(roleName)
                            rolesMap[roleName] = roleId
                        }
                    }
                    setupSpinner()

                    if (rolesList.size == 1) {
                        Toast.makeText(this, "No se encontraron roles en la base de datos", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "No se encontraron roles en la base de datos", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error al cargar roles: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun setupSpinner() {
        val adapter = object : ArrayAdapter<String>(this, R.layout.spinner_item_dark, rolesList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                view.textSize = 16f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
                view.textSize = 16f
                return view
            }
        }

        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item_dark)
        binding.spinnerRoles.adapter = adapter

        // Agregar listener para debug
        binding.spinnerRoles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedItem = rolesList[position]

                // Forzar que el texto sea blanco
                (view as? TextView)?.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {

            }
        }
    }

    private fun authenticateUser(selectedRole: String) {

        // Buscar usuario con el rol seleccionado
        firestore.collection("usuarios")
            .whereEqualTo("rol", selectedRole)
            .whereEqualTo("estado", true)
            .get()
            .addOnSuccessListener { documents ->

                if (!documents.isEmpty) {
                    // Tomar el primer usuario activo encontrado
                    val document = documents.documents[0]
                    val usuario = document.id
                    val email = document.getString("email")
                    val nombreUsuario = document.getString("nombreUsuario")

                    // Guardar sesión
                    saveUserSession(
                        usuario,
                        selectedRole,
                        nombreUsuario ?: "",
                        email ?: ""
                    )
                    navigateToDashboard(usuario, selectedRole)
                } else {
                    Toast.makeText(
                        this,
                        "No hay usuarios activos con este rol: $selectedRole",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Error de autenticación: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun saveUserSession(usuario: String, rol: String, nombreUsuario: String, email: String) {
        val editor = sharedPreferences.edit()
        editor.putString("usuario_activo", usuario)
        editor.putString("rol_activo", rol)
        editor.putString("nombre_usuario", nombreUsuario)
        editor.putString("email_usuario", email)
        editor.putLong("timestamp_login", System.currentTimeMillis())
        editor.apply()
        Toast.makeText(this, "Sesión iniciada como $nombreUsuario ($rol)", Toast.LENGTH_SHORT).show()
    }

    private fun navigateToDashboard(usuario: String, rol: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("usuario", usuario)
        intent.putExtra("rol", rol)
        startActivity(intent)
        finish()
    }

    private fun clearSession() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
        Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show()

        // Recargar la actividad
        recreate()
    }
}