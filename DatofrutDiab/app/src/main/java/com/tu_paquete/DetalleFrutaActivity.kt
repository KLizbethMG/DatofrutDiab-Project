package com.example.datofrutdiab

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import org.json.JSONObject

class DetalleFrutaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_fruta)

        val data = intent.getStringExtra("fruta")

        if (data != null) {
            val obj = JSONObject(data)

            val img = findViewById<ImageView>(R.id.imgDetalle)
            val nombre = findViewById<TextView>(R.id.txtNombreDetalle)
            val categoria = findViewById<TextView>(R.id.txtCategoriaDetalle)
            val proteina = findViewById<TextView>(R.id.txtProteina)
            val grasa = findViewById<TextView>(R.id.txtGrasa)
            val carbo = findViewById<TextView>(R.id.txtCarbohidratos)
            val fibra = findViewById<TextView>(R.id.txtFibra)
            val calorias = findViewById<TextView>(R.id.txtCalorias)
            val ig = findViewById<TextView>(R.id.txtIG)
            val nivel = findViewById<TextView>(R.id.txtNivelCarboDetalle)
            val recomendacion = findViewById<TextView>(R.id.txtRecomendacion)

            nombre.text = obj.getString("alimento")
            categoria.text = "Categoría: ${obj.getString("categoria")}"
            proteina.text = "Proteína: ${obj.getDouble("proteina")} g"
            grasa.text = "Grasa: ${obj.getDouble("grasa")} g"
            carbo.text = "Carbohidratos: ${obj.getDouble("carbohidratos")} g"
            fibra.text = "Fibra: ${obj.getDouble("fibra")} g"
            calorias.text = "Calorías: ${obj.getInt("calorias")} kcal"

            // ── Color en el Índice Glucémico ─────────────────────────────
            val igValor = obj.getInt("ig")
            ig.text = "Índice glucémico: $igValor"
            ig.setTextColor(when {
                igValor <= 55 -> Color.parseColor("#2E7D32")  // Verde  – IG bajo
                igValor <= 70 -> Color.parseColor("#F57F17")  // Naranja – IG medio
                else          -> Color.parseColor("#C62828")  // Rojo   – IG alto
            })

            val nivelStr = obj.getString("nivel")
            nivel.text = "Nivel: $nivelStr"
            when (nivelStr.lowercase()) {
                "muy bajo"          -> nivel.setTextColor(Color.parseColor("#1B5E20"))
                "bajo"              -> nivel.setTextColor(Color.parseColor("#4CAF50"))
                "medio", "moderado" -> nivel.setTextColor(Color.parseColor("#FF9800"))
                "alto"              -> nivel.setTextColor(Color.parseColor("#F44336"))
                else                -> nivel.setTextColor(Color.parseColor("#000000"))
            }

            recomendacion.text = "Ideal: ${obj.getString("recomendacion")}"

            val resId = resources.getIdentifier(
                obj.getString("imagen"),
                "drawable",
                packageName
            )

            if (resId != 0) {
                img.setImageResource(resId)
            } else {
                img.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }
}