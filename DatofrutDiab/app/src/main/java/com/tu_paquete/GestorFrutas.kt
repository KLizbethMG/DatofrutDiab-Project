package com.example.datofrutdiab

import android.content.Context
import org.json.JSONArray
import java.io.InputStreamReader

object GestorFrutas {

    private val listaFrutas = mutableListOf<Fruta>()

    fun cargarFrutasDesdeAsset(context: Context) {
        if (listaFrutas.isNotEmpty()) return

        try {
            val input = context.assets.open("frutas.json")
            val reader = InputStreamReader(input)
            val jsonText = reader.readText()
            reader.close()

            val jsonArray = JSONArray(jsonText)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                listaFrutas.add(
                    Fruta(
                        id = obj.getInt("ID"),
                        alimento = obj.getString("Alimento"),
                        categoria = obj.getString("Categoria"),
                        proteina = obj.getDouble("Proteina_g"),
                        grasa = obj.getDouble("Grasa_g"),
                        carbohidratos = obj.getDouble("Carbohidratos_g"),
                        fibra = obj.getDouble("Fibra_g"),
                        calorias = obj.getInt("Calorias_kcal"),
                        nivelCarbohidratos = obj.getString("Nivel_Carbohidratos"),
                        ig = obj.getInt("IG"),
                        recomendacion = obj.getString("Recomendacion_Diabetes"),
                        imagen = obj.optString("Imagen", ""),
                        porcionBase = obj.optDouble("porcion_base", 1.0),
                        unidad = obj.getString("unidad"),
                        nombreImagen = obj.optString("nombre_imagen", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun normalizarNombre(nombre: String): String {
        // Mapa directo para los nombres que no siguen camelCase exacto
        val mapaDirecto = mapOf(
            "mangoataulfo"    to "mango_ataulfo",
            "mangokent"       to "mango_kent",
            "manzanaamarilla" to "manzana_amarilla",
            "manzanaroja"     to "manzana_roja",
            "manzanaverde"    to "manzana_verde",
            "platanomaduro"   to "platano_maduro",
            "platanoverde"    to "platano_verde",
            "uvaroja"         to "uva_roja",
            "uvaverde"        to "uva_verde",
            "higofrescos"     to "higo_fresco"
        )

        val nombreLower = nombre.trim().lowercase()

        mapaDirecto[nombreLower]?.let { return it }

        val resultado = StringBuilder()
        for (char in nombre) {
            if (char.isUpperCase() && resultado.isNotEmpty()) {
                resultado.append('_')
                resultado.append(char.lowercaseChar())
            } else {
                resultado.append(char.lowercaseChar())
            }
        }
        return resultado.toString()
    }

    fun buscarFruta(query: String): Fruta? {
        val queryNormalizado = normalizarNombre(query)

        android.util.Log.d("GestorFrutas", "Buscando: '$query' → normalizado: '$queryNormalizado'")

        return listaFrutas.find {
            it.nombreImagen.lowercase() == queryNormalizado ||
                    it.alimento.lowercase().replace(" ", "_") == queryNormalizado ||
                    it.alimento.lowercase() == query.trim().lowercase()
        }
    }

    fun obtenerTodas(): List<Fruta> = listaFrutas
}