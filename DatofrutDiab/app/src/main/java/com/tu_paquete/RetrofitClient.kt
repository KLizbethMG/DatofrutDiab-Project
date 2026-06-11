package com.example.datofrutdiab

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * RetrofitClient MEJORADO
 * ─────────────────────────
 * Cambios:
 *   1. connectTimeout subido a 20 s   → menos "Connection refused" inmediatos
 *   2. readTimeout    subido a 60 s   → evita timeouts en la predicción
 *   3. writeTimeout   añadido a 30 s  → evita timeout al subir la foto
 *   4. Logging de la URL base al construir el cliente.
 */
object RetrofitClient {

    private const val PREFS_NAME  = "datofrut_prefs"
    private const val KEY_IP      = "servidor_ip"
    private const val IP_DEFAULT  = "192.168.0.118"

    private var _instance: ApiService? = null
    private var _ipActual: String = ""

    fun guardarIP(context: Context, ip: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_IP, ip.trim())
            .apply()
        _instance = null
    }

    fun obtenerIP(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IP, IP_DEFAULT) ?: IP_DEFAULT
    }

    fun getInstance(context: Context): ApiService {
        val ipGuardada = obtenerIP(context)

        if (_instance == null || _ipActual != ipGuardada) {
            _ipActual = ipGuardada

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            val urlBase = if (_ipActual.contains("ngrok-free") || _ipActual.contains("ngrok.io")) {
                "https://$_ipActual/"
            } else {
                "http://$_ipActual:8000/"
            }

            android.util.Log.d("RetrofitClient", "URL base: $urlBase")

            val retrofit = Retrofit.Builder()
                .baseUrl(urlBase)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            _instance = retrofit.create(ApiService::class.java)
        }

        return _instance!!
    }
}