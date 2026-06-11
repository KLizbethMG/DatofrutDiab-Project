package com.example.datofrutdiab

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import com.example.datofrutdiab.database.AppDatabase
import com.example.datofrutdiab.database.Historial

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ResultadoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resultado)

        val database = AppDatabase.getDatabase(this)
        GestorFrutas.cargarFrutasDesdeAsset(this)

        val nombreImagenDetectada = intent.getStringExtra("FRUTA_DETECTADA") ?: ""

        val imgFruta        = findViewById<ImageView>(R.id.imgFruta)
        val tvFruta         = findViewById<TextView>(R.id.tvFrutaDetectada)
        val tvGlucosa       = findViewById<TextView>(R.id.tvNivelGlucosa)
        val tvRecomendacion = findViewById<TextView>(R.id.tvRecomendacionFinal)
        val tvInfoIMC       = findViewById<TextView>(R.id.tvInfoIMC)
        val tvPorcionFinal  = findViewById<TextView>(R.id.tvPorcionFinal)
        val btnVolver       = findViewById<Button>(R.id.btnVolverInicio)
        val btnVerLista     = findViewById<Button>(R.id.btnVerListaFrutas)

        // ── Vistas de alternativas (nuevas) ─────────────────────────────────
        val layoutAlternativas = findViewById<LinearLayout>(R.id.layoutAlternativas)
        val tvAlternativa1     = findViewById<TextView>(R.id.tvAlternativa1)
        val tvAlternativa2     = findViewById<TextView>(R.id.tvAlternativa2)
        val tvAlternativa3     = findViewById<TextView>(R.id.tvAlternativa3)

        lifecycleScope.launch {
            val usuario = database.usuarioDao().obtenerUsuario()

            if (usuario == null) {
                Toast.makeText(this@ResultadoActivity, "Error: No hay perfil registrado.", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            val glucosaBase       = usuario.glucosaBase ?: -1
            val glucosaDisponible = glucosaBase > 0
            val glucosa           = glucosaBase.toDouble()
            val edad              = usuario.edad
            val genero            = usuario.sexo
            val actividad         = usuario.actividadFisica

            val imcCrudo = if (usuario.altura > 0) usuario.peso / (usuario.altura * usuario.altura) else 0f
            val imc = Math.round(imcCrudo * 10.0) / 10.0

            val clasificacionIMC = when {
                imc < 18.5        -> "Bajo peso"
                imc in 18.5..24.9 -> "Normal"
                imc in 25.0..29.9 -> "Sobrepeso"
                else              -> "Obesidad"
            }

            tvGlucosa.text = if (glucosaDisponible)
                "Glucosa en ayunas: $glucosa mg/dL"
            else
                "Glucosa: no registrada — recomendación por perfil"

            tvInfoIMC.text = String.format("IMC: %.1f (%s)  |  Edad: %d años  |  Actividad: %s",
                imc, clasificacionIMC, edad, actividad)

            val nombreLimpio = nombreImagenDetectada.trim().lowercase()
            val frutaDatos   = GestorFrutas.buscarFruta(nombreLimpio)

            if (frutaDatos == null) {
                tvFruta.text = "Fruta no encontrada en la base de datos."
                Toast.makeText(this@ResultadoActivity, "Error de identificación", Toast.LENGTH_LONG).show()
                return@launch
            }

            tvFruta.text = "Fruta detectada: ${frutaDatos.alimento}\nÍndice Glucémico (IG): ${frutaDatos.ig}"

            val textoRecomendacion: String
            val colorTexto: Int
            val colorFondo: Int
            var mostrarAlternativas = false

            if (glucosaDisponible) {

                val nivelGlucosa = when {
                    glucosa < 70  -> "HIPOGLUCEMIA"
                    glucosa < 100 -> "NORMAL"
                    glucosa < 126 -> "PREDIABETES"
                    else          -> "DIABETES"
                }

                val igFruta = frutaDatos.ig
                val nivelIG = when {
                    igFruta <= 55 -> "bajo"
                    igFruta <= 70 -> "medio"
                    else          -> "alto"
                }

                val resultado = when {
                    glucosa < 70 -> Triple(
                        "⚠ HIPOGLUCEMIA DETECTADA\n" +
                                "Tu glucosa está muy baja ($glucosa mg/dL).\n" +
                                "Esta fruta de IG $nivelIG puede ayudarte a estabilizarla.\n" +
                                "Consume una pequeña porción y busca atención médica.",
                        Color.parseColor("#B71C1C"), Color.parseColor("#FFCDD2")
                    )
                    glucosa >= 180 && igFruta > 70 -> Triple(
                        "🚫 EVITAR esta fruta\n" +
                                "Glucosa muy elevada ($glucosa mg/dL) e IG alto ($igFruta).\n" +
                                "Combinación de alto riesgo. Consulta a tu médico y elige frutas con IG bajo (≤55) como fresa, ciruela o manzana verde.",
                        Color.RED, Color.parseColor("#FFCDD2")
                    ).also { mostrarAlternativas = true }
                    glucosa >= 180 && igFruta in 56..70 -> Triple(
                        "⛔ LIMITAR al mínimo\n" +
                                "Glucosa muy elevada ($glucosa mg/dL). IG moderado ($igFruta).\n" +
                                "Si consumes, reduce la porción a la mitad y acompáñala con fibra o proteína para frenar la absorción.",
                        Color.parseColor("#BF360C"), Color.parseColor("#FFE0B2")
                    ).also { mostrarAlternativas = true }
                    glucosa >= 180 -> Triple(
                        "⚠ MODERAR\n" +
                                "Glucosa elevada ($glucosa mg/dL), pero el IG de esta fruta es bajo ($igFruta).\n" +
                                "Puedes consumirla en porciones pequeñas. Prefiere comerla entera con cáscara si es posible.",
                        Color.parseColor("#E65100"), Color.parseColor("#FFE0B2")
                    )
                    glucosa in 126.0..179.9 && igFruta > 70 -> Triple(
                        "⚠ PRECAUCIÓN\n" +
                                "Glucosa elevada ($glucosa mg/dL) y fruta con IG alto ($igFruta).\n" +
                                "Evita consumirla en grandes cantidades. Opta por frutas con IG ≤55.",
                        Color.parseColor("#E65100"), Color.parseColor("#FFE0B2")
                    ).also { mostrarAlternativas = true }
                    glucosa in 126.0..179.9 -> Triple(
                        "✅ CONSUMIR CON MODERACIÓN\n" +
                                "Glucosa en rango elevado ($glucosa mg/dL). El IG de esta fruta es $nivelIG ($igFruta).\n" +
                                "Controla la porción y evita combinarla con otros carbohidratos.",
                        Color.parseColor("#F57F17"), Color.parseColor("#FFF9C4")
                    )
                    glucosa in 100.0..125.9 && igFruta > 70 -> Triple(
                        "⚠ MODERACIÓN RECOMENDADA\n" +
                                "Glucosa en rango prediabético ($glucosa mg/dL). IG de la fruta alto ($igFruta).\n" +
                                "Prefiere frutas con IG más bajo para mantener tu glucosa estable.",
                        Color.parseColor("#F57F17"), Color.parseColor("#FFF9C4")
                    ).also { mostrarAlternativas = true }
                    glucosa < 100 && igFruta <= 55 -> Triple(
                        "✅ EXCELENTE ELECCIÓN\n" +
                                "Glucosa normal ($glucosa mg/dL) y fruta con IG bajo ($igFruta).\n" +
                                "Esta fruta es una opción muy saludable para ti. Disfrútala.",
                        Color.parseColor("#1B5E20"), Color.parseColor("#E8F5E9")
                    )
                    glucosa < 100 && igFruta in 56..70 -> Triple(
                        "✅ BUENA OPCIÓN\n" +
                                "Glucosa normal ($glucosa mg/dL) y IG moderado ($igFruta).\n" +
                                "Puedes consumirla sin problema en la porción recomendada.",
                        Color.parseColor("#2E7D32"), Color.parseColor("#E8F5E9")
                    )
                    else -> Triple(
                        "⚠ CONSUMIR CON PRECAUCIÓN\n" +
                                "Tu glucosa es normal ($glucosa mg/dL), pero esta fruta tiene IG alto ($igFruta).\n" +
                                "Puedes consumirla ocasionalmente en porciones pequeñas.",
                        Color.parseColor("#F9A825"), Color.parseColor("#FFFDE7")
                    )
                }

                textoRecomendacion = resultado.first + "\n\nEstado glucémico: $nivelGlucosa"
                colorTexto = resultado.second
                colorFondo = resultado.third

            } else {

                var puntosRiesgo = 0
                when {
                    imc >= 30         -> puntosRiesgo += 3
                    imc in 25.0..29.9 -> puntosRiesgo += 2
                    imc < 18.5        -> puntosRiesgo += 1
                }
                when {
                    edad >= 60     -> puntosRiesgo += 3
                    edad in 45..59 -> puntosRiesgo += 2
                    edad in 40..44 -> puntosRiesgo += 1
                }
                when (actividad) {
                    "Sedentario" -> puntosRiesgo += 2
                    "Moderado"   -> puntosRiesgo += 1
                }
                when {
                    frutaDatos.ig > 70 -> puntosRiesgo += 3
                    frutaDatos.ig > 55 -> puntosRiesgo += 1
                }

                val nivelRiesgo = when {
                    puntosRiesgo >= 8 -> "ALTO"
                    puntosRiesgo >= 5 -> "MODERADO"
                    else              -> "BAJO"
                }

                val resultado = when (nivelRiesgo) {
                    "ALTO" -> Triple(
                        "⚠ PERFIL DE RIESGO ALTO\n" +
                                "Según tu perfil (IMC: $imc, edad: $edad años, actividad: $actividad),\n" +
                                "tienes factores de riesgo para alteraciones de glucosa.\n" +
                                "Esta fruta tiene IG ${frutaDatos.ig} — se recomienda LIMITAR su consumo\n" +
                                "y consultar con un médico para conocer tu nivel de glucosa real.",
                        Color.parseColor("#BF360C"), Color.parseColor("#FFE0B2")
                    ).also { mostrarAlternativas = true }
                    "MODERADO" -> Triple(
                        "⚠ PERFIL DE RIESGO MODERADO\n" +
                                "Tu perfil (IMC: $imc, edad: $edad años, actividad: $actividad)\n" +
                                "sugiere precaución. Esta fruta tiene IG ${frutaDatos.ig}.\n" +
                                "Puedes consumirla en la porción recomendada, preferentemente\n" +
                                "después de una comida y acompañada de proteína o fibra.\n" +
                                "Se recomienda hacerte un análisis de glucosa.",
                        Color.parseColor("#F57F17"), Color.parseColor("#FFF9C4")
                    )
                    else -> Triple(
                        "✅ PERFIL DE BAJO RIESGO\n" +
                                "Según tu perfil (IMC: $imc, edad: $edad años, actividad: $actividad),\n" +
                                "esta fruta es una opción saludable para ti.\n" +
                                "IG: ${frutaDatos.ig} — disfrútala en la porción indicada.",
                        Color.parseColor("#1B5E20"), Color.parseColor("#E8F5E9")
                    )
                }

                textoRecomendacion = resultado.first + "\n\n💡 Riesgo estimado por perfil: $nivelRiesgo"
                colorTexto = resultado.second
                colorFondo = resultado.third
            }

            val consejoGenero = if (genero == "Femenino")
                "\n\nConsejo: Mantén buena hidratación y combina con calcio."
            else
                "\n\nConsejo: Combínala con proteína para mejor saciedad."

            tvRecomendacion.text = textoRecomendacion + consejoGenero
            tvRecomendacion.setTextColor(colorTexto)
            tvRecomendacion.setBackgroundColor(colorFondo)

            // ── Frutas alternativas ──────────────────────────────────────────
            if (mostrarAlternativas) {
                val alternativas = GestorFrutas.obtenerTodas()
                    .filter { it.nombreImagen != frutaDatos.nombreImagen && it.ig <= 55 }
                    .sortedBy { it.ig }
                    .take(3)

                if (alternativas.isNotEmpty()) {
                    layoutAlternativas.visibility = View.VISIBLE
                    val vistas = listOf(tvAlternativa1, tvAlternativa2, tvAlternativa3)
                    alternativas.forEachIndexed { i, fruta ->
                        vistas[i].text = " ${fruta.alimento}  —  IG: ${fruta.ig}  |  ${fruta.recomendacion}"
                        vistas[i].visibility = View.VISIBLE
                    }
                    for (i in alternativas.size until 3) {
                        vistas[i].visibility = View.GONE
                    }
                }
            }

            // ── Factores para porción ────────────────────────────────────────
            val factorIMC = when {
                imc < 18.5        -> 1.2
                imc in 18.5..24.9 -> 1.0
                imc in 25.0..29.9 -> 0.8
                else              -> 0.6
            }
            val factorActividad = when (actividad) {
                "Sedentario" -> 0.8
                "Moderado"   -> 1.0
                else         -> 1.2
            }
            val factorEdad = when {
                edad < 18 -> 1.1
                edad < 40 -> 1.0
                edad < 60 -> 0.9
                else      -> 0.8
            }
            val factorGlucosa = if (glucosaDisponible) {
                when {
                    glucosa > 180 -> 0.5
                    glucosa > 140 -> 0.7
                    else          -> 1.0
                }
            } else 1.0

            val porcionCalculada = frutaDatos.porcionBase * factorIMC * factorActividad * factorEdad * factorGlucosa
            val porcionAjustada  = porcionCalculada.coerceIn(
                frutaDatos.porcionBase * 0.25,
                frutaDatos.porcionBase * 2.0
            )

            val textoAmigable = formatearPorcion(porcionAjustada, frutaDatos.unidad)
            tvPorcionFinal.text = "Puedes consumir:\n$textoAmigable\nde ${frutaDatos.alimento}"

            val fechaActual = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
            database.historialDao().insertarRegistro(
                Historial(
                    fruta         = frutaDatos.alimento,
                    recomendacion = textoRecomendacion,
                    porcion       = textoAmigable,
                    glucosa       = if (glucosaDisponible) glucosa else 0.0,
                    fecha         = fechaActual
                )
            )

            val resourceId = resources.getIdentifier(frutaDatos.nombreImagen, "drawable", packageName)
            if (resourceId != 0) imgFruta.setImageResource(resourceId)
        }

        btnVolver.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        btnVerLista.setOnClickListener {
            startActivity(Intent(this, ListaFrutasActivity::class.java))
        }
    }

    private fun formatearPorcion(porcion: Double, unidad: String): String {
        val unidadLower = unidad.lowercase().trim()

        if (porcion >= 2.5 || unidadLower in listOf("gramos", "uvas", "fresas", "cerezas", "unidades")) {
            return "${Math.round(porcion).toInt()} $unidad"
        }

        val fraccion = when {
            porcion < 0.35 -> "Una cuarta parte de"
            porcion < 0.65 -> "Media"
            porcion < 0.85 -> "Tres cuartos de"
            porcion < 1.35 -> "Una"
            porcion < 1.75 -> "Una y media"
            else           -> "Dos"
        }

        return when (unidadLower) {
            "pieza", "piezas" -> when (fraccion) {
                "Una"         -> "Una pieza entera"
                "Media"       -> "Media pieza"
                "Dos"         -> "Dos piezas"
                "Una y media" -> "Una pieza y media"
                else          -> "$fraccion pieza"
            }
            "taza", "tazas" -> when (fraccion) {
                "Una"         -> "Una taza"
                "Media"       -> "Media taza"
                "Dos"         -> "Dos tazas"
                "Una y media" -> "Una taza y media"
                else          -> "$fraccion taza"
            }
            "rebanada", "rebanadas" -> when (fraccion) {
                "Una"         -> "Una rebanada"
                "Media"       -> "Media rebanada"
                "Dos"         -> "Dos rebanadas"
                "Una y media" -> "Una rebanada y media"
                else          -> "$fraccion rebanada"
            }
            else -> "$fraccion $unidad"
        }
    }
}