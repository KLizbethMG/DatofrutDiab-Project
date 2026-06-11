package com.example.datofrutdiab

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("predecir")
    fun predecirFruta(
        @Part imagen: MultipartBody.Part
    ): Call<RespuestaFruta>
}

data class RespuestaFruta(
    val fruta_detectada: String,
    val confianza: Float = 0f,
    val advertencia: String? = null,
    val top3: List<FrutaProbable>? = null
)

data class FrutaProbable(
    val fruta: String,
    val confianza: Float
)