package com.example.datofrutdiab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.datofrutdiab.database.AppDatabase
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var database: AppDatabase
    private lateinit var ivFotoFruta: ImageView
    private lateinit var btnTomarFoto: Button
    private lateinit var btnGaleria: Button
    private lateinit var btnEnviarAPI: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEstado: TextView

    // ── Vistas del perfil (nuevas) ───────────────────────────────────────
    private lateinit var cardPerfil: LinearLayout
    private lateinit var tvSaludoUsuario: TextView
    private lateinit var tvPerfilIMC: TextView
    private lateinit var tvPerfilClasificacionIMC: TextView
    private lateinit var tvPerfilGlucosa: TextView
    private lateinit var tvPerfilEstadoGlucosa: TextView
    private lateinit var tvPerfilRiesgo: TextView

    private var uriFotoCompleta: Uri? = null
    private var archivoFoto: File? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    private val tomarFotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                archivoFoto?.let { archivo ->
                    if (archivo.exists()) procesarYGuardarImagen(archivo.absolutePath)
                }
            }
        }

    private val seleccionarGaleriaLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { uriSeleccionada ->
                try {
                    mostrarEstado("Procesando imagen de la galería...")
                    val archivoDestino = crearArchivoTemporal()
                    archivoFoto = archivoDestino
                    val inputStream: InputStream? = contentResolver.openInputStream(uriSeleccionada)
                    val outputStream = FileOutputStream(archivoDestino)
                    inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
                    procesarYGuardarImagen(archivoDestino.absolutePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Error al cargar desde galería: ${e.message}", e)
                    mostrarEstado("Error al cargar la imagen de la galería.")
                }
            }
        }

    private val pedirPermisoCamaraLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { aprobado ->
            if (aprobado) abrirCamara()
            else Toast.makeText(this, "Se necesita el permiso de la cámara.", Toast.LENGTH_LONG).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database     = AppDatabase.getDatabase(this)
        ivFotoFruta  = findViewById(R.id.imgPreview)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        btnGaleria   = findViewById(R.id.btnGaleria)
        btnEnviarAPI = findViewById(R.id.btnDetectar)
        progressBar  = findViewById(R.id.progressBar)
        tvEstado     = findViewById(R.id.tvEstado)

        // ── Vincular vistas del perfil ───────────────────────────────────
        cardPerfil               = findViewById(R.id.cardPerfil)
        tvSaludoUsuario          = findViewById(R.id.tvSaludoUsuario)
        tvPerfilIMC              = findViewById(R.id.tvPerfilIMC)
        tvPerfilClasificacionIMC = findViewById(R.id.tvPerfilClasificacionIMC)
        tvPerfilGlucosa          = findViewById(R.id.tvPerfilGlucosa)
        tvPerfilEstadoGlucosa    = findViewById(R.id.tvPerfilEstadoGlucosa)
        tvPerfilRiesgo           = findViewById(R.id.tvPerfilRiesgo)

        findViewById<Button>(R.id.btnRegresar).setOnClickListener { finish() }

        btnEnviarAPI.isEnabled = false
        progressBar.visibility = View.GONE

        btnTomarFoto.setOnClickListener { verificarPermisosYCamara() }
        btnGaleria.setOnClickListener { seleccionarGaleriaLauncher.launch("image/*") }

        btnEnviarAPI.setOnClickListener {
            archivoFoto?.let { archivo ->
                if (archivo.exists()) enviarFotoABackend(archivo)
                else mostrarEstado("No se encontró la foto. Intenta de nuevo.")
            } ?: mostrarEstado("Primero toma una foto o selecciona una de la galería.")
        }

        // ── Cargar tarjeta de perfil ─────────────────────────────────────
        cargarTarjetaPerfil()
    }

    // ─────────────────────────────────────────────────────────────
    // Tarjeta de perfil del usuario (nueva)
    // ─────────────────────────────────────────────────────────────
    private fun cargarTarjetaPerfil() {
        lifecycleScope.launch {
            val usuario = database.usuarioDao().obtenerUsuario() ?: return@launch

            val imcCrudo = if (usuario.altura > 0)
                usuario.peso / (usuario.altura * usuario.altura) else 0f
            val imc = Math.round(imcCrudo * 10.0) / 10.0

            val clasificacionIMC = when {
                imc < 18.5        -> "Bajo peso"
                imc in 18.5..24.9 -> "Normal"
                imc in 25.0..29.9 -> "Sobrepeso"
                else              -> "Obesidad"
            }

            val glucosaBase       = usuario.glucosaBase ?: -1
            val glucosaDisponible = glucosaBase > 0
            val textoGlucosa      = if (glucosaDisponible) "$glucosaBase mg/dL" else "No registrada"
            val estadoGlucosa     = when {
                !glucosaDisponible -> "Sin dato"
                glucosaBase < 70   -> "Hipoglucemia"
                glucosaBase < 100  -> "Normal"
                glucosaBase < 126  -> "Prediabetes"
                else               -> "Diabetes"
            }

            var puntosRiesgo = 0
            if (imc >= 30) puntosRiesgo += 3 else if (imc >= 25) puntosRiesgo += 2
            if (usuario.edad >= 60) puntosRiesgo += 3
            else if (usuario.edad >= 45) puntosRiesgo += 2
            else if (usuario.edad >= 40) puntosRiesgo += 1
            if (usuario.actividadFisica == "Sedentario") puntosRiesgo += 2
            else if (usuario.actividadFisica == "Moderado") puntosRiesgo += 1
            if (glucosaDisponible && glucosaBase >= 126) puntosRiesgo += 3
            else if (glucosaDisponible && glucosaBase >= 100) puntosRiesgo += 1

            val textoRiesgo = when {
                puntosRiesgo >= 8 -> "⚠ Riesgo ALTO — Consulta a tu médico"
                puntosRiesgo >= 5 -> "⚡ Riesgo MODERADO — Mantén hábitos saludables"
                else              -> "✅ Riesgo BAJO — ¡Sigue así!"
            }

            runOnUiThread {
                cardPerfil.visibility         = View.VISIBLE
                tvSaludoUsuario.text          = "Hola, ${usuario.nombre} 👋"
                tvPerfilIMC.text              = "$imc"
                tvPerfilClasificacionIMC.text = clasificacionIMC
                tvPerfilGlucosa.text          = textoGlucosa
                tvPerfilEstadoGlucosa.text    = estadoGlucosa
                tvPerfilRiesgo.text           = textoRiesgo
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Permisos y generación de archivos (sin cambios)
    // ─────────────────────────────────────────────────────────────
    private fun verificarPermisosYCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) abrirCamara()
        else pedirPermisoCamaraLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun crearArchivoTemporal(): File {
        val timestamp  = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("FRUTA_${timestamp}_", ".jpg", storageDir)
    }

    private fun abrirCamara() {
        try {
            val archivo = crearArchivoTemporal()
            archivoFoto = archivo
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
            uriFotoCompleta = uri
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
            }
            if (intent.resolveActivity(packageManager) != null) {
                btnEnviarAPI.isEnabled = false
                mostrarEstado("Abriendo cámara…")
                tomarFotoLauncher.launch(intent)
            } else {
                mostrarEstado("No se encontró aplicación de cámara.")
            }
        } catch (e: Exception) {
            mostrarEstado("Error al preparar el archivo para la cámara.")
        }
    }

    private fun procesarYGuardarImagen(rutaAbsoluta: String) {
        val bitmapCorregido = cargarYCorregirOrientacion(rutaAbsoluta)
        if (bitmapCorregido != null) {
            val archivo = File(rutaAbsoluta)
            val out = FileOutputStream(archivo)
            bitmapCorregido.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.flush()
            out.close()
            ivFotoFruta.setImageBitmap(bitmapCorregido)
            btnEnviarAPI.isEnabled = true
            mostrarEstado("✓ Imagen lista. Presiona Detectar fruta.")
        } else {
            mostrarEstado("Error al procesar la imagen. Intenta de nuevo.")
        }
    }

    private fun cargarYCorregirOrientacion(ruta: String): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(ruta, options)
            val maxDim = 1024
            var escala = 1
            while ((options.outWidth / escala) > maxDim || (options.outHeight / escala) > maxDim) {
                escala *= 2
            }
            val opts2 = BitmapFactory.Options().apply { inSampleSize = escala }
            val bitmap = BitmapFactory.decodeFile(ruta, opts2) ?: return null
            val exif   = ExifInterface(ruta)
            val orient = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix()
            when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90  -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando imagen: ${e.message}")
            null
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Envío al backend (sin cambios)
    // ─────────────────────────────────────────────────────────────
    private fun enviarFotoABackend(archivo: File) {
        btnEnviarAPI.isEnabled = false
        btnTomarFoto.isEnabled = false
        btnGaleria.isEnabled   = false
        progressBar.visibility = View.VISIBLE
        mostrarEstado("Analizando fruta con IA… por favor espera.")

        val requestFile = archivo.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val body        = MultipartBody.Part.createFormData("imagen", archivo.name, requestFile)
        Log.d(TAG, "Enviando imagen: ${archivo.name} (${archivo.length() / 1024} KB)")

        RetrofitClient.getInstance(this).predecirFruta(body)
            .enqueue(object : Callback<RespuestaFruta> {
                override fun onResponse(call: Call<RespuestaFruta>, response: Response<RespuestaFruta>) {
                    ocultarCarga()
                    if (response.isSuccessful && response.body() != null) {
                        val resp = response.body()!!
                        Log.d(TAG, "Respuesta del servidor: $resp")
                        if (resp.advertencia != null) {
                            mostrarEstado("⚠ ${resp.advertencia}")
                        } else {
                            mostrarEstado("✓ Detectado: ${resp.fruta_detectada} (${resp.confianza}%)")
                        }
                        irAResultados(resp.fruta_detectada)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Sin detalles"
                        Log.e(TAG, "Error HTTP ${response.code()}: $errorBody")
                        mostrarEstado("Error del servidor (${response.code()}). Intenta de nuevo.")
                        btnEnviarAPI.isEnabled = true
                    }
                }
                override fun onFailure(call: Call<RespuestaFruta>, t: Throwable) {
                    ocultarCarga()
                    Log.e(TAG, "Error de red: ${t.message}", t)
                    val mensaje = when {
                        t.message?.contains("timeout", ignoreCase = true) == true ->
                            "Tiempo de espera agotado. Verifica que el servidor esté encendido y la IP sea correcta."
                        t.message?.contains("refused", ignoreCase = true) == true ->
                            "Conexión rechazada. ¿Está el servidor corriendo en la IP configurada?"
                        else -> "Error de red: ${t.message}"
                    }
                    mostrarEstado(mensaje)
                    btnEnviarAPI.isEnabled = true
                }
            })
    }

    private fun irAResultados(fruta: String) {
        lifecycleScope.launch {
            try {
                val u = database.usuarioDao().obtenerUsuario()
                val imcCalculado = if (u != null && u.altura > 0) u.peso / (u.altura * u.altura) else 0f
                val intent = Intent(this@MainActivity, ResultadoActivity::class.java).apply {
                    putExtra("FRUTA_DETECTADA", fruta)
                    putExtra("GLUCOSA_USUARIO", u?.glucosaBase ?: -1)
                    putExtra("IMC_USUARIO", imcCalculado)
                    putExtra("ACTIVIDAD_USUARIO", u?.actividadFisica ?: "Sedentario")
                    putExtra("GENERO_USUARIO", u?.sexo ?: "Femenino")
                    putExtra("EDAD_USUARIO", u?.edad ?: -1)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error al ir a resultados: ${e.message}", e)
                runOnUiThread {
                    mostrarEstado("Error interno: ${e.message}")
                    btnEnviarAPI.isEnabled = true
                }
            }
        }
    }

    private fun mostrarEstado(msg: String) {
        tvEstado.text       = msg
        tvEstado.visibility = View.VISIBLE
    }

    private fun ocultarCarga() {
        progressBar.visibility = View.GONE
        btnTomarFoto.isEnabled = true
        btnGaleria.isEnabled   = true
    }
}