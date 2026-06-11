package com.example.datofrutdiab

data class Fruta(
    val id: Int,
    val alimento: String,
    val categoria: String,
    val proteina: Double,
    val grasa: Double,
    val carbohidratos: Double,
    val fibra: Double,
    val calorias: Int,
    val nivelCarbohidratos: String,
    val ig: Int,
    val recomendacion: String,
    val imagen: String,
    val porcionBase: Double,
    val unidad: String,
    val nombreImagen: String
)