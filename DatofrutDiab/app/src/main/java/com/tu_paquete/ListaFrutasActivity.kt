package com.example.datofrutdiab

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.datofrutdiab.database.AppDatabase
import kotlinx.coroutines.launch

class ListaFrutasActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var adapter: FrutaAdapter
    private var listaCompleta: List<Fruta> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_frutas)

        recyclerView = findViewById(R.id.recyclerFrutas)
        etBuscar     = findViewById(R.id.etBuscarFruta)

        GestorFrutas.cargarFrutasDesdeAsset(this)
        listaCompleta = GestorFrutas.obtenerTodas()

        // Cargar perfil del usuario para el semáforo personalizado
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@ListaFrutasActivity)
            val usuario  = database.usuarioDao().obtenerUsuario()

            val glucosa   = (usuario?.glucosaBase ?: -1).toDouble()
            val imc       = if ((usuario?.altura ?: 0f) > 0)
                (usuario!!.peso / (usuario.altura * usuario.altura)).toDouble()
            else 0.0
            val actividad = usuario?.actividadFisica ?: ""

            // Crear adapter con el perfil del usuario
            adapter = FrutaAdapter(
                listaFrutas      = listaCompleta.toMutableList(),
                glucosaUsuario   = glucosa,
                imcUsuario       = imc,
                actividadUsuario = actividad
            )
            recyclerView.layoutManager = LinearLayoutManager(this@ListaFrutasActivity)
            recyclerView.adapter = adapter

            // Filtro de búsqueda
            etBuscar.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim().lowercase()
                    val filtrada = if (query.isEmpty()) {
                        listaCompleta
                    } else {
                        listaCompleta.filter {
                            it.alimento.lowercase().contains(query) ||
                                    it.categoria.lowercase().contains(query)
                        }
                    }
                    adapter.actualizarLista(filtrada)
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }
}