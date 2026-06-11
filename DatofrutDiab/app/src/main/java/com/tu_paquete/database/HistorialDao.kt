package com.example.datofrutdiab.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistorialDao {

    @Insert
    suspend fun insertarRegistro(historial: Historial)

    @Query("SELECT * FROM historial ORDER BY id DESC")
    suspend fun obtenerHistorial(): List<Historial>
}