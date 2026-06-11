package com.example.datofrutdiab

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.example.datofrutdiab.database.AppDatabase
import com.example.datofrutdiab.database.Usuario
import kotlinx.coroutines.launch

class RegistroActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase

    // ── Sección "ya registrado" ──────────────────────────────────────────
    private lateinit var layoutYaRegistrado: LinearLayout
    private lateinit var tvBienvenida: TextView
    private lateinit var btnEntrarDirecto: Button
    private lateinit var btnActualizarDatos: Button

    // ── Formulario completo ──────────────────────────────────────────────
    private lateinit var layoutFormulario: ScrollView
    private lateinit var btnObtenerRecomendacion: Button

    // ── Campo IP del servidor ────────────────────────────────────────────
    private lateinit var etIP: EditText
    private lateinit var btnGuardarIP: Button

    // ── Campos del formulario ────────────────────────────────────────────
    private lateinit var etGlucosa: EditText
    private lateinit var switchSinGlucosa: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        database = AppDatabase.getDatabase(this)

        // Vincular vistas principales
        layoutYaRegistrado      = findViewById(R.id.layoutYaRegistrado)
        tvBienvenida            = findViewById(R.id.tvBienvenida)
        btnEntrarDirecto        = findViewById(R.id.btnEntrarDirecto)
        btnActualizarDatos      = findViewById(R.id.btnActualizarDatos)
        layoutFormulario        = findViewById(R.id.layoutFormulario)
        btnObtenerRecomendacion = findViewById(R.id.btnObtenerRecomendacion)
        etIP                    = findViewById(R.id.etIP)
        btnGuardarIP            = findViewById(R.id.btnGuardarIP)
        etGlucosa               = findViewById(R.id.etGlucosa)
        switchSinGlucosa        = findViewById(R.id.switchSinGlucosa)

        // Mostrar la IP guardada
        etIP.setText(RetrofitClient.obtenerIP(this))

        // Guardar IP
        btnGuardarIP.setOnClickListener {
            val ip = etIP.text.toString().trim()
            if (ip.isEmpty()) {
                etIP.error = "Ingresa una IP válida"
                return@setOnClickListener
            }
            RetrofitClient.guardarIP(this, ip)
            Toast.makeText(this, "IP guardada: $ip", Toast.LENGTH_SHORT).show()
        }

        // ── Comportamiento del switch de glucosa ─────────────────────────
        // Estado inicial: glucosa habilitada porque switch está apagado
        actualizarEstadoGlucosa(switchSinGlucosa.isChecked)

        switchSinGlucosa.setOnCheckedChangeListener { _, isChecked ->
            actualizarEstadoGlucosa(isChecked)
        }

        // Verificar si ya hay usuario registrado
        lifecycleScope.launch {
            val usuarioExistente = database.usuarioDao().obtenerUsuario()
            if (usuarioExistente != null) {
                mostrarPantallaYaRegistrado(usuarioExistente)
            } else {
                mostrarFormularioCompleto()
            }
        }

        btnObtenerRecomendacion.setOnClickListener {
            validarFormulario()
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Habilita o deshabilita el campo de glucosa según el switch
    // ────────────────────────────────────────────────────────────────────
    private fun actualizarEstadoGlucosa(sinGlucosa: Boolean) {
        if (sinGlucosa) {
            // No conoce su glucosa → deshabilitar y limpiar campo
            etGlucosa.isEnabled = false
            etGlucosa.setText("")
            etGlucosa.error = null
            etGlucosa.alpha = 0.4f
            etGlucosa.hint = "No aplica"
        } else {
            // Sí conoce su glucosa → habilitar campo
            etGlucosa.isEnabled = true
            etGlucosa.alpha = 1.0f
            etGlucosa.hint = "Ej: 110"
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Pantalla cuando el usuario ya existe
    // ────────────────────────────────────────────────────────────────────
    private fun mostrarPantallaYaRegistrado(usuario: Usuario) {
        layoutYaRegistrado.visibility = View.VISIBLE
        layoutFormulario.visibility   = View.GONE

        tvBienvenida.text = "¡Bienvenido de nuevo, ${usuario.nombre}!"

        btnEntrarDirecto.setOnClickListener {
            irAMainActivity()
        }

        btnActualizarDatos.setOnClickListener {
            layoutYaRegistrado.visibility = View.GONE
            layoutFormulario.visibility   = View.VISIBLE
            precargarDatos(usuario)
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Mostrar formulario vacío (primer registro)
    // ────────────────────────────────────────────────────────────────────
    private fun mostrarFormularioCompleto() {
        layoutYaRegistrado.visibility = View.GONE
        layoutFormulario.visibility   = View.VISIBLE
    }

    // ────────────────────────────────────────────────────────────────────
    // Precargar campos con datos del usuario guardado
    // ────────────────────────────────────────────────────────────────────
    private fun precargarDatos(usuario: Usuario) {
        findViewById<EditText>(R.id.etNombre).setText(usuario.nombre)
        findViewById<EditText>(R.id.etEdad).setText(usuario.edad.toString())
        findViewById<EditText>(R.id.etPeso).setText(usuario.peso.toString())
        findViewById<EditText>(R.id.etEstatura).setText(usuario.altura.toString())

        val glucosa = usuario.glucosaBase
        if ((glucosa ?: -1) >= 0) {
            etGlucosa.setText(glucosa.toString())
            switchSinGlucosa.isChecked = false
        } else {
            etGlucosa.setText("")
            switchSinGlucosa.isChecked = true
        }
        // Actualizar estado visual del campo glucosa según el switch precargado
        actualizarEstadoGlucosa(switchSinGlucosa.isChecked)

        val spinnerGenero    = findViewById<Spinner>(R.id.spinnerGenero)
        val spinnerActividad = findViewById<Spinner>(R.id.spinnerActividad)

        val generosAdapter = spinnerGenero.adapter
        for (i in 0 until generosAdapter.count) {
            if (generosAdapter.getItem(i).toString() == usuario.sexo) {
                spinnerGenero.setSelection(i); break
            }
        }

        val actividadAdapter = spinnerActividad.adapter
        for (i in 0 until actividadAdapter.count) {
            if (actividadAdapter.getItem(i).toString() == usuario.actividadFisica) {
                spinnerActividad.setSelection(i); break
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Validar y guardar formulario
    // ────────────────────────────────────────────────────────────────────
    private fun validarFormulario() {
        val etNombre         = findViewById<EditText>(R.id.etNombre)
        val etEdad           = findViewById<EditText>(R.id.etEdad)
        val etPeso           = findViewById<EditText>(R.id.etPeso)
        val etEstatura       = findViewById<EditText>(R.id.etEstatura)
        val spinnerGenero    = findViewById<Spinner>(R.id.spinnerGenero)
        val spinnerActividad = findViewById<Spinner>(R.id.spinnerActividad)

        val nombre      = etNombre.text.toString().trim()
        val edadStr     = etEdad.text.toString().trim()
        val pesoStr     = etPeso.text.toString().trim()
        val estaturaStr = etEstatura.text.toString().trim()
        val glucosaStr  = etGlucosa.text.toString().trim()

        var valido = true

        // Validar campos obligatorios siempre
        if (nombre.isEmpty()) {
            etNombre.error = "El nombre es obligatorio"
            valido = false
        }
        if (edadStr.isEmpty()) {
            etEdad.error = "La edad es obligatoria"
            valido = false
        } else if (edadStr.toIntOrNull() == null || edadStr.toInt() <= 0) {
            etEdad.error = "Ingresa una edad válida"
            valido = false
        }
        if (pesoStr.isEmpty()) {
            etPeso.error = "El peso es obligatorio"
            valido = false
        } else if (pesoStr.toFloatOrNull() == null || pesoStr.toFloat() <= 0) {
            etPeso.error = "Ingresa un peso válido"
            valido = false
        }
        if (estaturaStr.isEmpty()) {
            etEstatura.error = "La estatura es obligatoria"
            valido = false
        } else if (estaturaStr.toFloatOrNull() == null || estaturaStr.toFloat() <= 0) {
            etEstatura.error = "Ingresa una estatura válida"
            valido = false
        }

        // Glucosa: obligatoria solo si el switch está APAGADO
        if (!switchSinGlucosa.isChecked) {
            if (glucosaStr.isEmpty()) {
                etGlucosa.error = "Ingresa tu glucosa o activa la casilla"
                valido = false
            } else if (glucosaStr.toIntOrNull() == null || glucosaStr.toInt() < 0) {
                etGlucosa.error = "Ingresa un valor de glucosa válido"
                valido = false
            }
        }

        if (!valido) {
            Toast.makeText(this, "Por favor completa todos los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        val edad        = edadStr.toInt()
        val peso        = pesoStr.toFloat()
        val altura      = estaturaStr.toFloat()
        val glucosaBase = if (switchSinGlucosa.isChecked) -1 else (glucosaStr.toIntOrNull() ?: 0)
        val sexo        = spinnerGenero.selectedItem?.toString() ?: "Femenino"
        val actividad   = spinnerActividad.selectedItem?.toString() ?: "Sedentario"

        lifecycleScope.launch {
            val nuevoUsuario = Usuario(
                id              = 1,
                nombre          = nombre,
                edad            = edad,
                peso            = peso,
                altura          = altura,
                sexo            = sexo,
                diabetes        = false,
                hipertension    = false,
                glucosaBase     = glucosaBase,
                actividadFisica = actividad
            )

            database.usuarioDao().insertarUsuario(nuevoUsuario)

            Toast.makeText(
                this@RegistroActivity,
                "Datos guardados correctamente",
                Toast.LENGTH_SHORT
            ).show()

            irAMainActivity()
        }
    }

    private fun irAMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}