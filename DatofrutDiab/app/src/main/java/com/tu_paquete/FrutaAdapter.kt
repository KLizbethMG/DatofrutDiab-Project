package com.example.datofrutdiab

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

// Se agregaron glucosa e imc opcionales para el semáforo personalizado
class FrutaAdapter(
    private val listaFrutas: MutableList<Fruta>,
    private val glucosaUsuario: Double = -1.0,
    private val imcUsuario: Double = 0.0,
    private val actividadUsuario: String = ""
) : RecyclerView.Adapter<FrutaAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgFruta: ImageView = itemView.findViewById(R.id.imgFruta)
        val txtNombre: TextView = itemView.findViewById(R.id.txtNombre)
        val txtCategoria: TextView = itemView.findViewById(R.id.txtCategoria)
        val txtNivel: TextView = itemView.findViewById(R.id.txtNivelCarbo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_fruta, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = listaFrutas.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val fruta = listaFrutas[position]
        val context = holder.itemView.context

        holder.txtNombre.text = fruta.alimento
        holder.txtCategoria.text = fruta.categoria

        // ── Semáforo personalizado según perfil del usuario ──────────────
        val colorSemaforo = calcularColorSemaforo(fruta)
        holder.txtNivel.text = "Nivel: ${fruta.nivelCarbohidratos}"
        holder.txtNivel.setTextColor(colorSemaforo)

        // Imagen
        val nombreImg = fruta.imagen.ifEmpty { fruta.nombreImagen }
        val resId = context.resources.getIdentifier(
            nombreImg, "drawable", context.packageName
        )
        if (resId != 0) holder.imgFruta.setImageResource(resId)
        else holder.imgFruta.setImageResource(R.drawable.ic_launcher_foreground)

        holder.itemView.setOnClickListener {
            val intent = Intent(context, DetalleFrutaActivity::class.java)
            val json = JSONObject().apply {
                put("alimento", fruta.alimento)
                put("categoria", fruta.categoria)
                put("proteina", fruta.proteina)
                put("grasa", fruta.grasa)
                put("carbohidratos", fruta.carbohidratos)
                put("fibra", fruta.fibra)
                put("calorias", fruta.calorias)
                put("nivel", fruta.nivelCarbohidratos)
                put("ig", fruta.ig)
                put("recomendacion", fruta.recomendacion)
                put("imagen", fruta.nombreImagen)
            }
            intent.putExtra("fruta", json.toString())
            context.startActivity(intent)
        }
    }

    // ── Lógica del semáforo ──────────────────────────────────────────────
    private fun calcularColorSemaforo(fruta: Fruta): Int {
        val ig = fruta.ig

        // Si hay glucosa disponible → semáforo clínico
        if (glucosaUsuario > 0) {
            return when {
                glucosaUsuario >= 180 && ig > 70  -> Color.RED
                glucosaUsuario >= 180              -> Color.parseColor("#E65100")
                glucosaUsuario >= 126 && ig > 70  -> Color.parseColor("#FF6F00")
                glucosaUsuario >= 126              -> Color.parseColor("#F9A825")
                glucosaUsuario >= 100 && ig > 70  -> Color.parseColor("#F57F17")
                ig <= 55                           -> Color.parseColor("#2E7D32")
                ig <= 70                           -> Color.parseColor("#F9A825")
                else                               -> Color.parseColor("#C62828")
            }
        }

        // Sin glucosa → semáforo por IG + perfil de riesgo
        var riesgo = 0
        if (imcUsuario >= 30) riesgo++
        if (imcUsuario >= 25) riesgo++
        if (actividadUsuario == "Sedentario") riesgo++

        return when {
            ig > 70 && riesgo >= 2  -> Color.RED
            ig > 70                 -> Color.parseColor("#FF6F00")
            ig > 55 && riesgo >= 2  -> Color.parseColor("#F57F17")
            ig <= 55                -> Color.parseColor("#2E7D32")
            else                    -> Color.parseColor("#F9A825")
        }
    }

    fun actualizarLista(nuevaLista: List<Fruta>) {
        listaFrutas.clear()
        listaFrutas.addAll(nuevaLista)
        notifyDataSetChanged()
    }
}