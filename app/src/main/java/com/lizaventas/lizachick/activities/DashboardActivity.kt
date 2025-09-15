package com.lizaventas.lizachick.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.lizaventas.lizachick.R
import com.lizaventas.lizachick.databinding.ActivityDashboardBinding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("TiendaPrefs", MODE_PRIVATE)
    }

    private var currentUser: String? = null
    private var currentRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        setupDarkTheme()

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(false)
            title = getString(R.string.dashboard_title)
        }

        getUserData()
        setupUI()
        setupOnBackPressed()
    }

    private fun setupDarkTheme() {
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.black)
    }

    private fun getUserData() {
        // Intent primero, luego SharedPreferences
        currentUser = intent.getStringExtra("usuario") ?: sharedPreferences.getString("usuario_activo", null)
        currentRole = intent.getStringExtra("rol") ?: sharedPreferences.getString("rol_activo", null)

        if (currentUser == null || currentRole == null) {
            returnToLogin()
        }
    }

    private fun setupUI() {
        val nombreUsuario = sharedPreferences.getString("nombre_usuario", currentUser) ?: "Usuario"
        val email = sharedPreferences.getString("email_usuario", "")

        binding.tvWelcome.text = getString(R.string.welcome_message, nombreUsuario)
        binding.tvUserRole.text = getString(R.string.user_role, currentRole)
        binding.tvUserEmail.text = getString(R.string.user_email, email)

        setupButtonsByRole()
    }

    private fun setupButtonsByRole() = with(binding) {
        val isAdmin = currentRole == "Administrador"
        val isSeller = currentRole == "Vendedor"

        btnVentas.isEnabled = isAdmin || isSeller
        btnProductos.isEnabled = isAdmin || isSeller
        btnGastos.isEnabled = isAdmin || isSeller
        btnPedidos.isEnabled = isAdmin || isSeller
        btnReportes.isEnabled = isAdmin || isSeller
        btnConfiguracion.isEnabled = isAdmin
        btnListadoVentas.isEnabled = isAdmin || isSeller
        btnCatalogos.isEnabled = isAdmin || isSeller

        // Listeners centralizados
        mapOf(
            btnVentas to VentasActivity::class.java,
            btnProductos to ProductosActivity::class.java,
            btnGastos to GastosActivity::class.java,
            btnPedidos to PedidosActivity::class.java,
            btnReportes to ReportesActivity::class.java,
            btnCatalogos to CatalogoActivity::class.java,
            btnListadoVentas to ListaVentasActivity::class.java
        ).forEach { (button, activity) ->
            button.setOnClickListener { startActivity(Intent(this@DashboardActivity, activity)) }
        }

        btnConfiguracion.setOnClickListener {
            //startActivity(Intent(this@DashboardActivity, ConfiguracionActivity::class.java))
            AlertDialog.Builder(this@DashboardActivity, R.style.AlertDialogDark)
                .setTitle(R.string.alerta)
                .setMessage(R.string.construccion)
                .setPositiveButton(android.R.string.yes, null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_logout -> {
            logout(); true
        }
        R.id.menu_profile -> {
            showProfile(); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun logout() {
        sharedPreferences.edit { clear() }
        returnToLogin()
    }

    private fun returnToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        })
        finish()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun showProfile() {
        val nombreUsuario = sharedPreferences.getString("nombre_usuario", currentUser) ?: "Usuario"
        val email = sharedPreferences.getString("email_usuario", "")
        val timestamp = sharedPreferences.getLong("timestamp_login", 0)

        val formattedDate = if (timestamp > 0) {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.getDefault())
                .withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(timestamp))
        } else "N/A"

        val profileInfo = """
            Usuario: $nombreUsuario
            Rol: $currentRole
            Email: $email
            Última sesión: $formattedDate
        """.trimIndent()

        AlertDialog.Builder(this, R.style.AlertDialogDark)
            .setTitle(R.string.profile_title)
            .setMessage(profileInfo)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@DashboardActivity, R.style.AlertDialogDark)
                    .setTitle(R.string.exit_app)
                    .setMessage(R.string.exit_confirmation)
                    .setPositiveButton(android.R.string.yes) { _, _ -> finishAffinity() }
                    .setNegativeButton(android.R.string.no, null)
                    .show()
            }
        })
    }
}
