package com.example.datofrutdiab.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usuarios")
data class Usuario(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val nombre: String,
    val edad: Int,
    val peso: Float,
    val altura: Float,
    val sexo: String,

    val diabetes: Boolean,
    val hipertension: Boolean,

    val actividadFisica: String,

    val glucosaBase: Int?
)