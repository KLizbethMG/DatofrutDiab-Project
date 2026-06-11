package com.example.datofrutdiab.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "historial")
data class Historial(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fruta: String,

    val recomendacion: String,

    val porcion: String,

    val glucosa: Double,

    val fecha: String
)